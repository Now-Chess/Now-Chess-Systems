#!/usr/bin/env python3
"""
scoverage Coverage Gap Reporter
Parses a scoverage XML report (scoverage.xml) and outputs missing statement
and branch (conditional) coverage in a structured format that Claude Code
agents can act on directly.

scoverage tracks coverage at the *statement* level (not bytecode instruction
level like JaCoCo). Each <statement> element has:
  - line              : source line number
  - branch="true|false" : whether this statement is a branch point
  - invocation-count  : how many times it was executed (0 = not covered)
  - ignored           : if true, excluded from coverage metrics

Usage:
    python scoverage_coverage_gaps.py <scoverage.xml>
    python scoverage_coverage_gaps.py <scoverage.xml> --output json
    python scoverage_coverage_gaps.py <scoverage.xml> --output markdown
    python scoverage_coverage_gaps.py <scoverage.xml> --output agent   (default)
    python scoverage_coverage_gaps.py <scoverage.xml> --package-filter de.nowchess.chess.controller
    python scoverage_coverage_gaps.py <scoverage.xml> --min-coverage 80
"""

import xml.etree.ElementTree as ET
import sys
import argparse
import json
import re
from pathlib import Path, PureWindowsPath
from dataclasses import dataclass, field
from typing import Optional


# ---------------------------------------------------------------------------
# Data classes
# ---------------------------------------------------------------------------

@dataclass
class Statement:
    line: int
    is_branch: bool
    invocation_count: int
    ignored: bool
    method: str

    @property
    def is_covered(self) -> bool:
        return self.invocation_count > 0

    @property
    def is_uncovered(self) -> bool:
        return not self.is_covered and not self.ignored


@dataclass
class MethodGap:
    name: str
    uncovered_lines: list[int]
    uncovered_branch_lines: list[int]
    total_statements: int
    covered_statements: int
    total_branches: int
    covered_branches: int

    @property
    def short_name(self) -> str:
        """Strip the package prefix from the full method path."""
        return self.name.split("/")[-1] if "/" in self.name else self.name

    @property
    def stmt_coverage_pct(self) -> float:
        return 100.0 * self.covered_statements / self.total_statements if self.total_statements else 100.0

    @property
    def branch_coverage_pct(self) -> float:
        return 100.0 * self.covered_branches / self.total_branches if self.total_branches else 100.0

    @property
    def missed_branches(self) -> int:
        return self.total_branches - self.covered_branches

    @property
    def has_gaps(self) -> bool:
        return bool(self.uncovered_lines or self.uncovered_branch_lines)


@dataclass
class ClassGap:
    class_name: str          # e.g. de.nowchess.chess.controller.GameController
    source_path: str         # normalised relative source path
    raw_source: str          # original source attribute from XML
    # Authoritative values read directly from <class> XML attributes
    xml_total_statements:   int   = 0
    xml_covered_statements: int   = 0
    xml_stmt_rate:          float = 0.0
    xml_branch_rate:        float = 0.0
    statements: list[Statement] = field(default_factory=list)

    # ---- aggregated views (populated after parse) ----
    method_gaps: list[MethodGap] = field(default_factory=list)

    @property
    def all_uncovered_lines(self) -> list[int]:
        seen: set[int] = set()
        result = []
        for s in self.statements:
            if s.is_uncovered and s.line not in seen:
                seen.add(s.line)
                result.append(s.line)
        return sorted(result)

    @property
    def uncovered_branch_lines(self) -> list[int]:
        """Lines that are branch points and have at least one uncovered branch statement."""
        # Group branch statements by line; a line is "partial" if some covered, some not
        from collections import defaultdict
        by_line: dict[int, list[Statement]] = defaultdict(list)
        for s in self.statements:
            if s.is_branch and not s.ignored:
                by_line[s.line].append(s)
        partial = []
        for line, stmts in by_line.items():
            has_covered = any(s.is_covered for s in stmts)
            has_uncovered = any(s.is_uncovered for s in stmts)
            # Report line if any branch arm is uncovered
            if has_uncovered:
                partial.append(line)
        return sorted(partial)

    @property
    def total_statements(self) -> int:
        return self.xml_total_statements

    @property
    def covered_statements(self) -> int:
        return self.xml_covered_statements

    @property
    def missed_statements(self) -> int:
        return self.xml_total_statements - self.xml_covered_statements

    @property
    def total_branches(self) -> int:
        return sum(1 for s in self.statements if s.is_branch and not s.ignored)

    @property
    def covered_branches(self) -> int:
        return sum(1 for s in self.statements if s.is_branch and s.is_covered and not s.ignored)

    @property
    def missed_branches(self) -> int:
        return self.total_branches - self.covered_branches

    @property
    def stmt_coverage_pct(self) -> float:
        return self.xml_stmt_rate

    @property
    def branch_coverage_pct(self) -> float:
        return self.xml_branch_rate

    @property
    def has_gaps(self) -> bool:
        return self.missed_statements > 0 or self.missed_branches > 0


# ---------------------------------------------------------------------------
# Source path normalisation
# ---------------------------------------------------------------------------

def _normalise_source(raw: str) -> str:
    """
    Convert an absolute Windows or Unix source path from the XML into a
    relative src/main/scala/… path for agent consumption.

    Strategy:
      1. Replace Windows backslashes.
      2. Find the 'src/' anchor and take everything from there.
      3. Fall back to the package-derived path if no anchor found.
    """
    normalised = raw.replace("\\", "/")
    match = re.search(r"(src/(?:main|test)/scala/.+)", normalised)
    if match:
        return match.group(1)
    # Fallback: just the filename portion
    return normalised.split("/")[-1]


# ---------------------------------------------------------------------------
# Parser
# ---------------------------------------------------------------------------

def parse_scoverage_xml(xml_path: str) -> list[ClassGap]:
    tree = ET.parse(xml_path)
    root = tree.getroot()

    # ── Authoritative project-level totals from <scoverage> root element ──────
    project_stats = {
        "total_statements":    int(root.get("statement-count", 0)),
        "covered_statements":  int(root.get("statements-invoked", 0)),
        "stmt_coverage_pct":   float(root.get("statement-rate", 0.0)),
        "branch_coverage_pct": float(root.get("branch-rate", 0.0)),
    }
    project_stats["missed_statements"] = (
        project_stats["total_statements"] - project_stats["covered_statements"]
    )

    class_map: dict[str, ClassGap] = {}   # full-class-name → ClassGap

    for package in root.findall("packages/package"):
        for cls_elem in package.findall("classes/class"):
            class_name = cls_elem.get("name", "")
            filename   = cls_elem.get("filename", "")

            # Authoritative per-class totals from <class> attributes
            cls_total     = int(cls_elem.get("statement-count", 0))
            cls_invoked   = int(cls_elem.get("statements-invoked", 0))
            cls_stmt_rate = float(cls_elem.get("statement-rate", 0.0))
            cls_br_rate   = float(cls_elem.get("branch-rate", 0.0))

            for method_elem in cls_elem.findall("methods/method"):
                method_name = method_elem.get("name", "")

                # Authoritative per-method totals from <method> attributes
                m_total     = int(method_elem.get("statement-count", 0))
                m_invoked   = int(method_elem.get("statements-invoked", 0))
                m_stmt_rate = float(method_elem.get("statement-rate", 0.0))
                m_br_rate   = float(method_elem.get("branch-rate", 0.0))

                for stmt_elem in method_elem.findall("statements/statement"):
                    raw_source = stmt_elem.get("source", filename)
                    full_class = stmt_elem.get("full-class-name", class_name)

                    if full_class not in class_map:
                        class_map[full_class] = ClassGap(
                            class_name=full_class,
                            source_path=_normalise_source(raw_source),
                            raw_source=raw_source,
                            xml_total_statements=cls_total,
                            xml_covered_statements=cls_invoked,
                            xml_stmt_rate=cls_stmt_rate,
                            xml_branch_rate=cls_br_rate,
                        )

                    cg = class_map[full_class]

                    line      = int(stmt_elem.get("line", 0))
                    is_branch = stmt_elem.get("branch", "false").lower() == "true"
                    inv       = int(stmt_elem.get("invocation-count", 0))
                    ignored   = stmt_elem.get("ignored", "false").lower() == "true"

                    cg.statements.append(Statement(
                        line=line,
                        is_branch=is_branch,
                        invocation_count=inv,
                        ignored=ignored,
                        method=method_name,
                    ))

                # Register method-level gap using authoritative XML stats
                cg = next(
                    (v for v in class_map.values() if v.class_name == class_name),
                    None,
                )
                if cg is None:
                    continue
                active = [s for s in cg.statements if s.method == method_name and not s.ignored]
                uncov_lines        = sorted({s.line for s in active if s.is_uncovered})
                uncov_branch_lines = sorted({s.line for s in active if s.is_branch and s.is_uncovered})
                if uncov_lines or uncov_branch_lines:
                    # Count branches from statement-level data (not in method XML attrs)
                    total_b = sum(1 for s in active if s.is_branch)
                    cov_b   = sum(1 for s in active if s.is_branch and s.is_covered)
                    mg = MethodGap(
                        name=method_name,
                        uncovered_lines=uncov_lines,
                        uncovered_branch_lines=uncov_branch_lines,
                        total_statements=m_total,
                        covered_statements=m_invoked,
                        total_branches=total_b,
                        covered_branches=cov_b,
                    )
                    cg.method_gaps.append(mg)

    # ── Project stats injected so formatters never recount from statements ────
    return project_stats, [cg for cg in class_map.values() if cg.has_gaps]


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _compact_ranges(numbers: list[int]) -> str:
    """[1,2,3,5,7,8,9] → '1-3, 5, 7-9'"""
    if not numbers:
        return ""
    ranges = []
    start = prev = numbers[0]
    for n in numbers[1:]:
        if n == prev + 1:
            prev = n
        else:
            ranges.append(f"{start}-{prev}" if start != prev else str(start))
            start = prev = n
    ranges.append(f"{start}-{prev}" if start != prev else str(start))
    return ", ".join(ranges)


# ---------------------------------------------------------------------------
# Formatters
# ---------------------------------------------------------------------------

def _pct_bar(pct: float, width: int = 20) -> str:
    """Render a compact ASCII progress bar, e.g. [████░░░░░░░░░░░░░░░░] 23.5%"""
    filled = round(pct / 100 * width)
    bar = "█" * filled + "░" * (width - filled)
    return f"[{bar}] {pct:.1f}%"


def format_agent(project_stats: dict, classes: list[ClassGap]) -> str:
    lines: list[str] = []
    lines.append("# scoverage Coverage Gaps — Agent Action Report")
    lines.append("")

    # ---- Project-level totals (authoritative from <scoverage> root element) ----
    total_stmts      = project_stats["total_statements"]
    covered_stmts    = project_stats["covered_statements"]
    missed_stmts     = project_stats["missed_statements"]
    overall_stmt_pct   = project_stats["stmt_coverage_pct"]
    overall_branch_pct = project_stats["branch_coverage_pct"]
    total_branch_lines = sum(len(c.uncovered_branch_lines) for c in classes)
    # Branch totals: count from statement data (scoverage root has no branch count attr)
    total_branches   = sum(c.total_branches for c in classes)
    covered_branches = sum(c.covered_branches for c in classes)
    missed_branches  = sum(c.missed_branches for c in classes)

    lines.append("## Project Coverage Summary")
    lines.append("")
    lines.append(f"| Metric            | Covered | Total | Missed | Coverage |")
    lines.append(f"|-------------------|---------|-------|--------|----------|")
    lines.append(f"| Statements        | {covered_stmts:>7} | {total_stmts:>5} | {missed_stmts:>6} | {_pct_bar(overall_stmt_pct)} |")
    lines.append(f"| Branch paths      | {covered_branches:>7} | {total_branches:>5} | {missed_branches:>6} | {_pct_bar(overall_branch_pct)} |")
    lines.append(f"| Files with gaps   | {'—':>7} | {len(classes):>5} | {'—':>6} | {'—'} |")
    lines.append(f"| Lines w/ br. gaps | {'—':>7} | {total_branch_lines:>5} | {'—':>6} | {'—'} |")
    lines.append("")
    lines.append("---")
    lines.append("")
    lines.append("## Files Requiring Tests")
    lines.append("")
    lines.append("> Each entry lists the SOURCE FILE PATH, uncovered LINE NUMBERS,")
    lines.append("> and the METHODS that contain those gaps.")
    lines.append("> Write or extend unit/integration tests to exercise these paths.")
    lines.append("")

    sorted_classes = sorted(classes, key=lambda c: -(c.missed_statements + c.missed_branches))

    for cls in sorted_classes:
        lines.append(f"### `{cls.source_path}`")
        lines.append(f"**Class**: `{cls.class_name}`")
        lines.append("")
        lines.append(f"| Metric       | Covered | Total | Missed | Coverage |")
        lines.append(f"|--------------|---------|-------|--------|----------|")
        lines.append(f"| Statements   | {cls.covered_statements:>7} | {cls.total_statements:>5} | {cls.missed_statements:>6} | {_pct_bar(cls.stmt_coverage_pct)} |")
        if cls.total_branches:
            lines.append(f"| Branch paths | {cls.covered_branches:>7} | {cls.total_branches:>5} | {cls.missed_branches:>6} | {_pct_bar(cls.branch_coverage_pct)} |")
        lines.append("")

        uncov = cls.all_uncovered_lines
        if uncov:
            lines.append("#### ❌ Uncovered Statements")
            lines.append(f"Lines never executed: `{_compact_ranges(uncov)}`")
            lines.append("")

        branch_lines = cls.uncovered_branch_lines
        if branch_lines:
            lines.append("#### ⚠️  Missing Branch Coverage (Conditional Paths)")
            lines.append(f"Lines where not all conditional paths are taken: `{_compact_ranges(branch_lines)}`")
            lines.append("")

        if cls.method_gaps:
            lines.append("#### Methods with Gaps")
            lines.append("")
            lines.append("| Method | Stmt Coverage | Branch Coverage | Uncovered Lines | Branch Gap Lines |")
            lines.append("|--------|--------------|-----------------|-----------------|------------------|")
            for mg in cls.method_gaps:
                stmt_cell   = f"{_pct_bar(mg.stmt_coverage_pct, 10)} ({mg.total_statements - mg.covered_statements}/{mg.total_statements} missed)"
                branch_cell = f"{_pct_bar(mg.branch_coverage_pct, 10)} ({mg.missed_branches}/{mg.total_branches} missed)" if mg.total_branches else "n/a"
                uncov_cell  = f"`{_compact_ranges(mg.uncovered_lines)}`"        if mg.uncovered_lines        else "—"
                br_cell     = f"`{_compact_ranges(mg.uncovered_branch_lines)}`" if mg.uncovered_branch_lines else "—"
                lines.append(f"| `{mg.short_name}` | {stmt_cell} | {branch_cell} | {uncov_cell} | {br_cell} |")
            lines.append("")

        lines.append("**Action**: Add tests that exercise the lines/branches listed above.")
        lines.append("")
        lines.append("---")
        lines.append("")

    lines.append("## Quick Reference: All Uncovered Locations")
    lines.append("")
    lines.append("Copy-paste friendly list for IDE navigation or grep:")
    lines.append("")
    lines.append("```")
    for cls in sorted_classes:
        for ln in cls.all_uncovered_lines:
            lines.append(f"{cls.source_path}:{ln}  # uncovered statement")
        for ln in cls.uncovered_branch_lines:
            if ln not in cls.all_uncovered_lines:
                lines.append(f"{cls.source_path}:{ln}  # partial branch")
    lines.append("```")

    return "\n".join(lines)


def format_json(project_stats: dict, classes: list[ClassGap]) -> str:
    total_branches   = sum(c.total_branches for c in classes)
    covered_branches = sum(c.covered_branches for c in classes)
    out = {
        "project": {
            "total_statements":    project_stats["total_statements"],
            "covered_statements":  project_stats["covered_statements"],
            "missed_statements":   project_stats["missed_statements"],
            "stmt_coverage_pct":   project_stats["stmt_coverage_pct"],
            "branch_coverage_pct": project_stats["branch_coverage_pct"],
            "total_branches":      total_branches,
            "covered_branches":    covered_branches,
            "missed_branches":     total_branches - covered_branches,
            "files_with_gaps": len(classes),
        },
        "classes": [],
    }
    for cls in sorted(classes, key=lambda c: c.class_name):
        out["classes"].append({
            "class": cls.class_name,
            "source_path": cls.source_path,
            "total_statements": cls.total_statements,
            "covered_statements": cls.covered_statements,
            "missed_statements": cls.missed_statements,
            "stmt_coverage_pct": round(cls.stmt_coverage_pct, 1),
            "total_branches": cls.total_branches,
            "covered_branches": cls.covered_branches,
            "missed_branches": cls.missed_branches,
            "branch_coverage_pct": round(cls.branch_coverage_pct, 1),
            "uncovered_lines": cls.all_uncovered_lines,
            "uncovered_branch_lines": cls.uncovered_branch_lines,
            "methods": [
                {
                    "name": mg.short_name,
                    "full_name": mg.name,
                    "total_statements": mg.total_statements,
                    "covered_statements": mg.covered_statements,
                    "missed_statements": mg.total_statements - mg.covered_statements,
                    "stmt_coverage_pct": round(mg.stmt_coverage_pct, 1),
                    "total_branches": mg.total_branches,
                    "covered_branches": mg.covered_branches,
                    "missed_branches": mg.missed_branches,
                    "branch_coverage_pct": round(mg.branch_coverage_pct, 1),
                    "uncovered_lines": mg.uncovered_lines,
                    "uncovered_branch_lines": mg.uncovered_branch_lines,
                }
                for mg in cls.method_gaps
            ],
        })
    return json.dumps(out, indent=2)


def format_markdown(project_stats: dict, classes: list[ClassGap]) -> str:
    lines: list[str] = []
    lines.append("# scoverage Missing Coverage Report")
    lines.append("")

    overall_stmt_pct   = project_stats["stmt_coverage_pct"]
    overall_branch_pct = project_stats["branch_coverage_pct"]
    covered_stmts      = project_stats["covered_statements"]
    total_stmts        = project_stats["total_statements"]
    total_branches     = sum(c.total_branches for c in classes)
    covered_branches   = sum(c.covered_branches for c in classes)

    lines.append("## Project Totals")
    lines.append("")
    lines.append("| Metric       | Covered | Total | Missed | % |")
    lines.append("|--------------|---------|-------|--------|---|")
    lines.append(f"| Statements   | {covered_stmts} | {total_stmts} | {total_stmts - covered_stmts} | {overall_stmt_pct:.1f}% |")
    lines.append(f"| Branch paths | {covered_branches} | {total_branches} | {total_branches - covered_branches} | {overall_branch_pct:.1f}% |")
    lines.append("")

    for cls in sorted(classes, key=lambda c: c.class_name):
        lines.append(f"## `{cls.class_name}`")
        lines.append(f"**File**: `{cls.source_path}`")
        lines.append("")
        lines.append("| Metric       | Covered | Total | Missed | % |")
        lines.append("|--------------|---------|-------|--------|---|")
        lines.append(f"| Statements   | {cls.covered_statements} | {cls.total_statements} | {cls.missed_statements} | {cls.stmt_coverage_pct:.1f}% |")
        if cls.total_branches:
            lines.append(f"| Branch paths | {cls.covered_branches} | {cls.total_branches} | {cls.missed_branches} | {cls.branch_coverage_pct:.1f}% |")
        lines.append("")
        if cls.all_uncovered_lines:
            lines.append(f"**Uncovered lines**: `{_compact_ranges(cls.all_uncovered_lines)}`")
            lines.append("")
        if cls.uncovered_branch_lines:
            lines.append(f"**Branch gaps at lines**: `{_compact_ranges(cls.uncovered_branch_lines)}`")
            lines.append("")
        lines.append("| Method | Stmt Cov | Stmt Missed | Branch Cov | Branch Missed |")
        lines.append("|--------|----------|-------------|------------|---------------|")
        for mg in cls.method_gaps:
            lines.append(
                f"| `{mg.short_name}` | {mg.stmt_coverage_pct:.1f}% | "
                f"{mg.total_statements - mg.covered_statements}/{mg.total_statements} | "
                f"{mg.branch_coverage_pct:.1f}% | {mg.missed_branches}/{mg.total_branches} |"
            )
        lines.append("")
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def main() -> None:
    parser = argparse.ArgumentParser(
        description="Report missing statement & branch coverage from a scoverage XML report."
    )
    parser.add_argument("xml_file", help="Path to scoverage.xml report file")
    parser.add_argument(
        "--output", "-o",
        choices=["agent", "json", "markdown"],
        default="agent",
        help="Output format (default: agent)",
    )
    parser.add_argument(
        "--min-coverage",
        type=float,
        default=0.0,
        help="Only report classes below this %% statement coverage (0 = report all gaps)",
    )
    parser.add_argument(
        "--package-filter", "-p",
        default=None,
        help="Only report classes in this package prefix (e.g. de.nowchess.chess.controller)",
    )
    args = parser.parse_args()

    xml_path = Path(args.xml_file)
    if not xml_path.exists():
        print(f"ERROR: File not found: {xml_path}", file=sys.stderr)
        sys.exit(1)

    project_stats, classes = parse_scoverage_xml(str(xml_path))

    if args.package_filter:
        classes = [c for c in classes if c.class_name.startswith(args.package_filter)]

    if args.min_coverage > 0:
        classes = [c for c in classes if c.stmt_coverage_pct < args.min_coverage]

    if not classes:
        print("✅ No coverage gaps found matching the given filters.")
        return

    if args.output == "agent":
        print(format_agent(project_stats, classes))
    elif args.output == "json":
        print(format_json(project_stats, classes))
    elif args.output == "markdown":
        print(format_markdown(project_stats, classes))


if __name__ == "__main__":
    main()
