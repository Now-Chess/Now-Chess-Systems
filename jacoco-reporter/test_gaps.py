#!/usr/bin/env python3
"""
Test Gap Reporter
Scans JUnit XML test results under modules/*/build/test-results/*.xml and
outputs a minimal summary optimised for agent consumption.

Usage:
    python test_gaps.py                        # scan all modules (default)
    python test_gaps.py --module chess         # single module
    python test_gaps.py --module all           # explicit all
    python test_gaps.py --modules-dir ./modules
    python test_gaps.py --results-subdir build/test-results
"""

import xml.etree.ElementTree as ET
import sys
import argparse
from pathlib import Path
from dataclasses import dataclass, field
from typing import Optional


# ---------------------------------------------------------------------------
# Data classes
# ---------------------------------------------------------------------------

@dataclass
class TestCase:
    classname: str
    name: str
    time: float
    failure: Optional[str] = None   # message if failed
    error: Optional[str]   = None   # message if errored
    skipped: bool          = False

    @property
    def short_class(self) -> str:
        return self.classname.split(".")[-1]

    @property
    def status(self) -> str:
        if self.failure is not None:
            return "FAIL"
        if self.error is not None:
            return "ERROR"
        if self.skipped:
            return "SKIP"
        return "OK"


@dataclass
class SuiteResult:
    name: str
    total: int
    failures: int
    errors: int
    skipped: int
    time: float
    cases: list[TestCase] = field(default_factory=list)

    @property
    def passed(self) -> int:
        return self.total - self.failures - self.errors - self.skipped

    @property
    def is_clean(self) -> bool:
        return self.failures == 0 and self.errors == 0

    @property
    def bad_cases(self) -> list[TestCase]:
        return [c for c in self.cases if c.status in ("FAIL", "ERROR")]

    @property
    def skipped_cases(self) -> list[TestCase]:
        return [c for c in self.cases if c.skipped]


@dataclass
class ModuleResult:
    name: str
    suites: list[SuiteResult] = field(default_factory=list)

    @property
    def total(self)    -> int: return sum(s.total    for s in self.suites)
    @property
    def failures(self) -> int: return sum(s.failures for s in self.suites)
    @property
    def errors(self)   -> int: return sum(s.errors   for s in self.suites)
    @property
    def skipped(self)  -> int: return sum(s.skipped  for s in self.suites)
    @property
    def passed(self)   -> int: return sum(s.passed   for s in self.suites)
    @property
    def is_clean(self) -> bool: return self.failures == 0 and self.errors == 0

    @property
    def bad_cases(self) -> list[TestCase]:
        return [c for s in self.suites for c in s.bad_cases]

    @property
    def skipped_cases(self) -> list[TestCase]:
        return [c for s in self.suites for c in s.skipped_cases]


# ---------------------------------------------------------------------------
# Parser
# ---------------------------------------------------------------------------

def parse_suite_xml(xml_path: Path) -> SuiteResult:
    tree = ET.parse(xml_path)
    root = tree.getroot()

    # Handle both <testsuite> root and <testsuites> wrapper
    suites = [root] if root.tag == "testsuite" else root.findall("testsuite")

    # Merge multiple suites from one file into a single SuiteResult
    total = failures = errors = skipped = 0
    elapsed = 0.0
    name = xml_path.stem
    cases: list[TestCase] = []

    for suite in suites:
        total    += int(suite.get("tests",    0))
        failures += int(suite.get("failures", 0))
        errors   += int(suite.get("errors",   0))
        skipped  += int(suite.get("skipped",  0))
        elapsed  += float(suite.get("time",   0.0))
        if suite.get("name"):
            name = suite.get("name")

        for tc in suite.findall("testcase"):
            fail_el = tc.find("failure")
            err_el  = tc.find("error")
            skip_el = tc.find("skipped")
            cases.append(TestCase(
                classname=tc.get("classname", ""),
                name=tc.get("name", ""),
                time=float(tc.get("time", 0.0)),
                failure=fail_el.get("message", fail_el.text or "") if fail_el is not None else None,
                error=err_el.get("message",  err_el.text  or "") if err_el  is not None else None,
                skipped=skip_el is not None,
            ))

    return SuiteResult(
        name=name, total=total, failures=failures,
        errors=errors, skipped=skipped, time=elapsed, cases=cases,
    )


def load_module(module_dir: Path, results_subdir: str) -> Optional[ModuleResult]:
    results_dir = module_dir / results_subdir
    if not results_dir.is_dir():
        return None

    xml_files = sorted(results_dir.glob("*.xml"))
    if not xml_files:
        return None

    mod = ModuleResult(name=module_dir.name)
    for xml_path in xml_files:
        try:
            mod.suites.append(parse_suite_xml(xml_path))
        except ET.ParseError:
            pass  # skip malformed files silently
    return mod if mod.suites else None


# ---------------------------------------------------------------------------
# Formatter
# ---------------------------------------------------------------------------

def _truncate(text: str, max_len: int = 120) -> str:
    text = " ".join(text.split())        # collapse whitespace
    return text[:max_len] + "…" if len(text) > max_len else text


def format_module(mod: ModuleResult) -> str:
    parts = [f"[{mod.name}]"]

    if mod.is_clean and mod.skipped == 0:
        parts.append(f"tests: {mod.total} ✅")
        return " ".join(parts)

    parts.append(f"tests: {mod.total}")
    if mod.failures:  parts.append(f"failed: {mod.failures}")
    if mod.errors:    parts.append(f"errors: {mod.errors}")
    if mod.skipped:   parts.append(f"skipped: {mod.skipped}")

    # Agent hint only when there are actual failures/errors
    if not mod.is_clean:
        parts.append(f" # hint: run ./test {mod.name} for details")

    lines = [" ".join(parts)]

    # List each failed/errored test — this IS the actionable info
    for tc in mod.bad_cases:
        msg = tc.failure if tc.failure is not None else tc.error
        label = f"  {tc.status}: {tc.short_class} > {tc.name}"
        if msg:
            label += f"  [{_truncate(msg, 80)}]"
        lines.append(label)

    # Skipped: compact, one line total
    if mod.skipped_cases:
        skipped_names = ", ".join(
            f"{c.short_class}.{c.name}" for c in mod.skipped_cases[:5]
        )
        if len(mod.skipped_cases) > 5:
            skipped_names += f" (+{len(mod.skipped_cases) - 5} more)"
        lines.append(f"  SKIP: {skipped_names}")

    return "\n".join(lines)


# ---------------------------------------------------------------------------
# Runner
# ---------------------------------------------------------------------------

def run(modules_dir: str, results_subdir: str, module_filter: Optional[str]) -> None:
    base = Path(modules_dir)
    if not base.is_dir():
        print(f"ERROR: modules directory not found: {base}", file=sys.stderr)
        sys.exit(1)

    # Resolve which module dirs to scan
    if module_filter and module_filter != "all":
        mod_dir = base / module_filter
        if not mod_dir.is_dir():
            print(f"ERROR: module not found: {mod_dir}", file=sys.stderr)
            sys.exit(1)
        candidates = [mod_dir]
    else:
        candidates = sorted(p for p in base.iterdir() if p.is_dir())

    results: list[str] = []
    missing: list[str] = []

    for mod_dir in candidates:
        if mod_dir.name.startswith("build"):
            continue
        mod = load_module(mod_dir, results_subdir)
        if mod is None:
            missing.append(mod_dir.name)
            continue
        results.append(format_module(mod))

    print("\n".join(results))

    if missing:
        print(
            f"\n# Modules without test results: {', '.join(missing)}",
            file=sys.stderr,
        )


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def main() -> None:
    parser = argparse.ArgumentParser(
        description="Minimal test-gap reporter for JUnit XML results across modules."
    )
    parser.add_argument(
        "--module", "-m",
        nargs="?",
        const="all",
        default="all",
        help="Module name to scan, or 'all' (default: all)",
    )
    parser.add_argument(
        "--modules-dir",
        default="./modules",
        help="Root directory containing one sub-directory per module (default: ./modules)",
    )
    parser.add_argument(
        "--results-subdir",
        default="build/test-results/test",
        help="Sub-path inside each module dir where *.xml files live (default: build/test-results/test)",
    )
    args = parser.parse_args()

    filter_ = None if args.module == "all" else args.module
    run(args.modules_dir, args.results_subdir, filter_)


if __name__ == "__main__":
    main()