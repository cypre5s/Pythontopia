#!/usr/bin/env python3
from __future__ import annotations

import argparse

from pyt_agent_lab.domain_case_factory import generate_domain_cases, write_cases_jsonl


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate a larger Pythontopia domain evaluation set.")
    parser.add_argument("--count", type=int, default=140)
    parser.add_argument("--output", default="research/vllm-agent-ft-lab/cases/domain_cases_140.jsonl")
    args = parser.parse_args()

    count = write_cases_jsonl(generate_domain_cases(args.count), args.output)
    print(f"wrote {count} cases to {args.output}")


if __name__ == "__main__":
    main()
