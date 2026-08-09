#!/usr/bin/env python3

import argparse
import csv
import json
from collections import defaultdict
from pathlib import Path
import xml.etree.ElementTree as ET


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--group", required=True)
    parser.add_argument("--reports", type=Path, required=True)
    parser.add_argument("--wall-clock", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--github-summary", type=Path)
    return parser.parse_args()


def elapsed(element):
    try:
        return float(element.get("time", "0"))
    except ValueError:
        return 0.0


def status(testcase):
    for candidate in ("failure", "error", "skipped"):
        if testcase.find(candidate) is not None:
            return candidate
    return "passed"


def top_level_class(name):
    return name.split("$", 1)[0]


def load_harness(path):
    if not path.exists():
        return {}
    values = {}
    for line in path.read_text().splitlines():
        if "=" in line:
            key, value = line.split("=", 1)
            values[key] = value
    return values


def main():
    args = parse_args()
    args.output.mkdir(parents=True, exist_ok=True)

    methods = []
    suite_elapsed = defaultdict(list)
    for report in sorted(args.reports.glob("TEST-*.xml")):
        root = ET.parse(report).getroot()
        suite_name = root.get("name", report.stem.removeprefix("TEST-"))
        suite_elapsed[top_level_class(suite_name)].append(elapsed(root))
        for testcase in root.iter("testcase"):
            class_name = testcase.get("classname", suite_name)
            methods.append({
                "class": top_level_class(class_name),
                "method": testcase.get("name", ""),
                "seconds": elapsed(testcase),
                "status": status(testcase),
            })

    classes = defaultdict(lambda: {"tests": 0, "testcase_seconds": 0.0})
    status_counts = defaultdict(int)
    for method in methods:
        item = classes[method["class"]]
        item["tests"] += 1
        item["testcase_seconds"] += method["seconds"]
        status_counts[method["status"]] += 1

    class_rows = []
    for class_name, values in classes.items():
        measured_times = suite_elapsed.get(class_name, [])
        class_rows.append({
            "class": class_name,
            "tests": values["tests"],
            "seconds": max([values["testcase_seconds"], *measured_times]),
            "testcase_seconds": values["testcase_seconds"],
        })
    class_rows.sort(key=lambda row: (-row["seconds"], row["class"]))
    methods.sort(key=lambda row: (-row["seconds"], row["class"], row["method"]))

    wall_clock_seconds = int(args.wall_clock.read_text().strip()) if args.wall_clock.exists() else None
    harness = load_harness(args.output / "harness.properties")
    summary = {
        "group": args.group,
        "wall_clock_seconds": wall_clock_seconds,
        "classes": len(classes),
        "tests": len(methods),
        "passed": status_counts["passed"],
        "failures": status_counts["failure"],
        "errors": status_counts["error"],
        "skipped": status_counts["skipped"],
        "testcase_seconds": round(sum(method["seconds"] for method in methods), 3),
        "spring_contexts": int(harness["spring_contexts"]) if "spring_contexts" in harness else None,
        "base_setup_invocations": int(harness["base_setup_invocations"])
        if "base_setup_invocations" in harness else None,
        "database_reset_seconds": float(harness["database_reset_seconds"])
        if "database_reset_seconds" in harness else None,
        "settings_refresh_seconds": float(harness["settings_refresh_seconds"])
        if "settings_refresh_seconds" in harness else None,
        "slowest_class": class_rows[0]["class"] if class_rows else None,
        "slowest_class_seconds": round(class_rows[0]["seconds"], 3) if class_rows else None,
    }

    (args.output / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    write_csv(args.output / "classes.csv", class_rows, ["class", "tests", "seconds", "testcase_seconds"])
    write_csv(args.output / "methods.csv", methods, ["class", "method", "status", "seconds"])

    markdown = render_markdown(summary, class_rows[:10])
    (args.output / "summary.md").write_text(markdown)
    if args.github_summary:
        with args.github_summary.open("a") as stream:
            stream.write(markdown)


def write_csv(path, rows, fields):
    with path.open("w", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)


def format_seconds(value):
    if value is None:
        return "n/a"
    minutes, seconds = divmod(value, 60)
    return f"{minutes:.0f}m {seconds:04.1f}s"


def render_markdown(summary, slowest):
    lines = [
        f"## Test cost: `{summary['group']}`",
        "",
        "| Metric | Value |",
        "|---|---:|",
        f"| Wall clock | {format_seconds(summary['wall_clock_seconds'])} |",
        f"| Test cases | {summary['tests']} |",
        f"| Top-level classes | {summary['classes']} |",
        f"| Failures / errors / skipped | {summary['failures']} / {summary['errors']} / {summary['skipped']} |",
    ]
    if summary["spring_contexts"] is not None:
        lines.extend([
            f"| Spring contexts observed | {summary['spring_contexts']} |",
            f"| Base setup invocations | {summary['base_setup_invocations']} |",
            f"| Database reset time | {format_seconds(summary['database_reset_seconds'])} |",
            f"| Settings refresh time | {format_seconds(summary['settings_refresh_seconds'])} |",
        ])
    lines.extend([
        "",
        "### Slowest top-level classes",
        "",
        "| Class | Tests | Elapsed |",
        "|---|---:|---:|",
    ])
    for row in slowest:
        lines.append(f"| `{row['class']}` | {row['tests']} | {format_seconds(row['seconds'])} |")
    lines.append("")
    return "\n".join(lines)


if __name__ == "__main__":
    main()
