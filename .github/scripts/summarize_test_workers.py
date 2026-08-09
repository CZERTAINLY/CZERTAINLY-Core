#!/usr/bin/env python3

import argparse
import json
from pathlib import Path


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--github-summary", type=Path, required=True)
    return parser.parse_args()


def format_seconds(value):
    if value is None:
        return "n/a"
    minutes, seconds = divmod(value, 60)
    return f"{minutes:.0f}m {seconds:04.1f}s"


def main():
    args = parse_args()
    summaries = [
        json.loads(path.read_text())
        for path in args.root.rglob("summary.json")
    ]
    summaries.sort(key=lambda item: item["group"])
    if not summaries:
        raise SystemExit(f"No worker summaries found below {args.root}")

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(summaries, indent=2) + "\n")

    lines = [
        "## Test worker costs",
        "",
        "| Worker | Wall clock | Tests | Contexts | DB reset | Slowest class |",
        "|---|---:|---:|---:|---:|---|",
    ]
    for item in summaries:
        contexts = item["spring_contexts"] if item["spring_contexts"] is not None else "n/a"
        slowest = item["slowest_class"] or "n/a"
        if item["slowest_class_seconds"] is not None:
            slowest = f"`{slowest}` ({format_seconds(item['slowest_class_seconds'])})"
        lines.append(
            f"| `{item['group']}` | {format_seconds(item['wall_clock_seconds'])} "
            f"| {item['tests']} | {contexts} | {format_seconds(item['database_reset_seconds'])} "
            f"| {slowest} |"
        )

    wall_clocks = [item["wall_clock_seconds"] for item in summaries if item["wall_clock_seconds"] is not None]
    lines.extend([
        "",
        f"**Critical worker:** {format_seconds(max(wall_clocks) if wall_clocks else None)}  ",
        f"**Aggregate worker time:** {format_seconds(sum(wall_clocks)) if wall_clocks else 'n/a'}",
        "",
    ])
    markdown = "\n".join(lines)
    with args.github_summary.open("a") as stream:
        stream.write(markdown)
    (args.output.with_suffix(".md")).write_text(markdown)


if __name__ == "__main__":
    main()
