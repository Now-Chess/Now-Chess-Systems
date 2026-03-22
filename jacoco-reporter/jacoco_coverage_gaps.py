#!/usr/bin/env python3
"""
JaCoCo Coverage Gap Reporter
Parses a JaCoCo XML report and outputs missing line & branch (conditional)
coverage in a structured format that Claude Code agents can act on directly.

Usage:
    python jacoco_coverage_gaps.py <jacoco-report.xml> [--min-coverage 80]
    python jacoco_coverage_gaps.py <jacoco-report.xml> --output json
    python jacoco_coverage_gaps.py <jacoco-report.xml> --output markdown
    python jacoco_coverage_gaps.py <jacoco-report.xml> --output agent   (default)
"""

import xml.etree.ElementTree as ET
import sys
import argparse
import json
from pathlib import Path
from dataclasses import dataclass, field
from typing import Optional


# ---------------------------------------------------------------------------
# Data classes
# ---------------------------------------------------------------------------

@dataclass
class LineCoverage:
    line_number: int
    hits: int                   # 0 = not executed
    branch_total: int = 0       # 0 = not a branch point
    branch_covered: int = 0

    @property
    def is_uncovered(self) -> bool:
        return self.hits == 0

    @property
    def is_partial_branch(self) -> bool:
        return self.branch_total > 0 and self.branch_covered < self.branch_total


@dataclass
class MethodCoverage:
    name: str
    descriptor: str
    first_line: Optional[int]
    missed_instructions: int
    covered_instructions: int
    missed_branches: int
    covered_branches: int
    uncovered_lines: list[int] = field(default_factory=list)
    partial_branch_lines: list[int] = field(default_factory=list)

    @property
    def total_branches(self) -> int:
        return self.missed_branches + self.covered_branches

    @property
    def is_fully_covered(self) -> bool:
        return self.missed_instructions == 0 and self.missed_branches == 0

    @property
    def branch_coverage_pct(self) -> float:
        total = self.total_branches
        return 100.0 * self.covered_branches / total if total else 100.0

    @property
    def line_coverage_pct(self) -> float:
        total = self.missed_instructions + self.covered_instructions
        return 100.0 * self.covered_instructions / total if total else 100.0


@dataclass
class ClassCoverage:
    class_name: str             # e.g. com/example/Foo
    source_file: Optional[str]
    methods: list[MethodCoverage] = field(default_factory=list)
    all_lines: list[LineCoverage] = field(default_factory=list)

    @property
    def java_class_name(self) -> str:
        return self.class_name.replace("/", ".")

    @property
    def source_path(self) -> Optional[str]:
        """Best-guess relative source path."""
        if self.source_file:
            package = "/".join(self.class_name.split("/")[:-1])
            return f"src/main/java/{package}/{self.source_file}" if package else f"src/main/java/{self.source_file}"
        return None

    @property
    def uncovered_lines(self) -> list[int]:
        return sorted({l.line_number for l in self.all_lines if l.is_uncovered})

    @property
    def partial_branch_lines(self) -> list[int]:
        return sorted({l.line_number for l in self.all_lines if l.is_partial_branch})

    @property
    def missed_branches(self) -> int:
        return sum(max(l.branch_total - l.branch_covered, 0) for l in self.all_lines)

    @property
    def total_branches(self) -> int:
        return sum(l.branch_total for l in self.all_lines)

    @property
    def covered_branches(self) -> int:
        return self.total_branches - self.missed_branches

    @property
    def missed_lines(self) -> int:
        return len(self.uncovered_lines)

    @property
    def total_lines(self) -> int:
        return len(self.all_lines)

    @property
    def covered_lines(self) -> int:
        return self.total_lines - self.missed_lines

    @property
    def has_gaps(self) -> bool:
        return bool(self.uncovered_lines or self.partial_branch_lines)


# ---------------------------------------------------------------------------
# Parser
# ---------------------------------------------------------------------------

def parse_jacoco_xml(xml_path: str) -> list[ClassCoverage]:
    """Parse a JaCoCo XML report into ClassCoverage objects."""
    tree = ET.parse(xml_path)
    root = tree.getroot()

    results: list[ClassCoverage] = []

    for package in root.iter("package"):
        for cls_elem in package.findall("class"):
            class_name = cls_elem.get("name", "")
            source_file = cls_elem.get("sourcefilename")

            # Build method map from <method> children
            methods: list[MethodCoverage] = []
            for m in cls_elem.findall("method"):
                counters = {c.get("type"): c for c in m.findall("counter")}

                def _missed(t): return int(counters[t].get("missed", 0)) if t in counters else 0
                def _covered(t): return int(counters[t].get("covered", 0)) if t in counters else 0

                methods.append(MethodCoverage(
                    name=m.get("name", ""),
                    descriptor=m.get("desc", ""),
                    first_line=int(m.get("line")) if m.get("line") else None,
                    missed_instructions=_missed("INSTRUCTION"),
                    covered_instructions=_covered("INSTRUCTION"),
                    missed_branches=_missed("BRANCH"),
                    covered_branches=_covered("BRANCH"),
                ))

            cc = ClassCoverage(
                class_name=class_name,
                source_file=source_file,
                methods=methods,
            )

            # Per-line data lives in the matching <sourcefile> element
            source_file_elem = package.find(f"sourcefile[@name='{source_file}']") if source_file else None
            if source_file_elem is not None:
                for line_elem in source_file_elem.findall("line"):
                    nr = int(line_elem.get("nr", 0))
                    mi = int(line_elem.get("mi", 0))   # missed instructions
                    ci = int(line_elem.get("ci", 0))   # covered instructions
                    mb = int(line_elem.get("mb", 0))   # missed branches
                    cb = int(line_elem.get("cb", 0))   # covered branches
                    hits = ci  # ci > 0 means line was executed at least once
                    cc.all_lines.append(LineCoverage(
                        line_number=nr,
                        hits=hits,
                        branch_total=mb + cb,
                        branch_covered=cb,
                    ))

            if cc.has_gaps:
                results.append(cc)

    return results


# ---------------------------------------------------------------------------
# Formatters
# ---------------------------------------------------------------------------

def _compact_ranges(numbers: list[int]) -> str:
    """Turn [1,2,3,5,7,8,9] -> '1-3, 5, 7-9'"""
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


def format_agent(classes: list[ClassCoverage]) -> str:
    """
    Output optimised for Claude Code agents:
    – structured, machine-readable yet human-legible
    – uses file paths and line numbers agents can act on
    – groups by file, sorts by severity (most gaps first)
    """
    lines: list[str] = []
    lines.append("# JaCoCo Coverage Gaps — Agent Action Report")
    lines.append("")
    lines.append("## Summary")
    total_uncovered = sum(c.missed_lines for c in classes)
    total_partial = sum(len(c.partial_branch_lines) for c in classes)
    total_missed_branches = sum(c.missed_branches for c in classes)
    lines.append(f"- Files with gaps : {len(classes)}")
    lines.append(f"- Uncovered lines : {total_uncovered}")
    lines.append(f"- Partial branches: {total_partial} lines affected")
    lines.append(f"- Missed branches : {total_missed_branches} branch paths")
    lines.append("")
    lines.append("---")
    lines.append("")
    lines.append("## Files Requiring Tests")
    lines.append("")
    lines.append("> Each entry lists the SOURCE FILE PATH, the LINE NUMBERS that need")
    lines.append("> coverage, and the METHODS that contain those gaps.")
    lines.append("> Write or extend unit/integration tests to exercise these paths.")
    lines.append("")

    # Sort: most uncovered lines first
    sorted_classes = sorted(classes, key=lambda c: -(c.missed_lines + len(c.partial_branch_lines)))

    for cls in sorted_classes:
        source = cls.source_path or f"(source unknown) {cls.java_class_name}"
        lines.append(f"### `{source}`")
        lines.append(f"**Class**: `{cls.java_class_name}`")
        lines.append("")

        if cls.uncovered_lines:
            lines.append(f"#### ❌ Uncovered Lines")
            lines.append(f"Lines not executed at all: `{_compact_ranges(cls.uncovered_lines)}`")
            lines.append("")
            lines.append("**Methods with uncovered lines:**")
            for method in cls.methods:
                uncov = [l for l in cls.uncovered_lines
                         if method.first_line and l >= method.first_line]
                # heuristic: only attribute if there are uncovered lines near the method start
                if method.missed_instructions > 0:
                    sig = f"`{method.name}{method.descriptor}`"
                    pct = method.line_coverage_pct
                    lines.append(f"  - {sig} — {pct:.0f}% instruction coverage")
            lines.append("")

        if cls.partial_branch_lines:
            lines.append(f"#### ⚠️  Partial Branch Coverage (Missing Conditional Paths)")
            lines.append(f"Lines where not all branches are taken: `{_compact_ranges(cls.partial_branch_lines)}`")
            lines.append("")
            lines.append("**Methods with branch gaps:**")
            for method in cls.methods:
                if method.missed_branches > 0:
                    sig = f"`{method.name}{method.descriptor}`"
                    pct = method.branch_coverage_pct
                    missing = method.missed_branches
                    lines.append(f"  - {sig} — {pct:.0f}% branch coverage ({missing} branch path(s) never taken)")
            lines.append("")

        lines.append("**Action**: Add tests that exercise the above lines/branches.")
        lines.append("")
        lines.append("---")
        lines.append("")

    lines.append("## Quick Reference: All Uncovered Locations")
    lines.append("")
    lines.append("Copy-paste friendly list for IDE navigation or grep:")
    lines.append("")
    lines.append("```")
    for cls in sorted_classes:
        src = cls.source_path or cls.java_class_name
        if cls.uncovered_lines:
            for ln in cls.uncovered_lines:
                lines.append(f"{src}:{ln}  # uncovered line")
        if cls.partial_branch_lines:
            for ln in cls.partial_branch_lines:
                lines.append(f"{src}:{ln}  # partial branch")
    lines.append("```")

    return "\n".join(lines)


def format_json(classes: list[ClassCoverage]) -> str:
    out = []
    for cls in classes:
        out.append({
            "class": cls.java_class_name,
            "source_path": cls.source_path,
            "uncovered_lines": cls.uncovered_lines,
            "partial_branch_lines": cls.partial_branch_lines,
            "missed_branches": cls.missed_branches,
            "methods": [
                {
                    "name": m.name,
                    "descriptor": m.descriptor,
                    "first_line": m.first_line,
                    "line_coverage_pct": round(m.line_coverage_pct, 1),
                    "branch_coverage_pct": round(m.branch_coverage_pct, 1),
                    "missed_branches": m.missed_branches,
                    "missed_instructions": m.missed_instructions,
                }
                for m in cls.methods
                if not m.is_fully_covered
            ],
        })
    return json.dumps(out, indent=2)


def format_markdown(classes: list[ClassCoverage]) -> str:
    lines: list[str] = []
    lines.append("# JaCoCo Missing Coverage Report\n")
    for cls in sorted(classes, key=lambda c: cls.java_class_name):
        lines.append(f"## {cls.java_class_name}")
        if cls.source_path:
            lines.append(f"**File**: `{cls.source_path}`\n")
        if cls.uncovered_lines:
            lines.append(f"**Uncovered lines**: {_compact_ranges(cls.uncovered_lines)}\n")
        if cls.partial_branch_lines:
            lines.append(f"**Partial branches at lines**: {_compact_ranges(cls.partial_branch_lines)}\n")
        lines.append("| Method | Line Coverage | Branch Coverage | Missed Branches |")
        lines.append("|--------|--------------|-----------------|-----------------|")
        for m in cls.methods:
            if not m.is_fully_covered:
                lines.append(
                    f"| `{m.name}` | {m.line_coverage_pct:.0f}% | "
                    f"{m.branch_coverage_pct:.0f}% | {m.missed_branches} |"
                )
        lines.append("")
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def main() -> None:
    parser = argparse.ArgumentParser(
        description="Report missing line & branch coverage from a JaCoCo XML report."
    )
    parser.add_argument("xml_file", help="Path to jacoco.xml report file")
    parser.add_argument(
        "--output", "-o",
        choices=["agent", "json", "markdown"],
        default="json",
        help="Output format (default: agent)",
    )
    parser.add_argument(
        "--min-coverage",
        type=float,
        default=0.0,
        help="Only report classes below this %% line coverage (0 = report all gaps)",
    )
    parser.add_argument(
        "--package-filter", "-p",
        default=None,
        help="Only report classes in this package prefix (e.g. com/example/service)",
    )
    args = parser.parse_args()

    xml_path = Path(args.xml_file)
    if not xml_path.exists():
        print(f"ERROR: File not found: {xml_path}", file=sys.stderr)
        sys.exit(1)

    classes = parse_jacoco_xml(str(xml_path))

    # Apply package filter
    if args.package_filter:
        prefix = args.package_filter.replace(".", "/")
        classes = [c for c in classes if c.class_name.startswith(prefix)]

    # Apply min-coverage filter
    if args.min_coverage > 0:
        def _line_pct(c: ClassCoverage) -> float:
            total = c.total_lines
            return 100.0 * c.covered_lines / total if total else 100.0

        classes = [c for c in classes if _line_pct(c) < args.min_coverage]

    if not classes:
        print("✅ No coverage gaps found matching the given filters.")
        return

    if args.output == "agent":
        print(format_agent(classes))
    elif args.output == "json":
        print(format_json(classes))
    elif args.output == "markdown":
        print(format_markdown(classes))


if __name__ == "__main__":
    main()