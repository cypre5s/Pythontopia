#!/usr/bin/env python3
from __future__ import annotations

import argparse

from pyt_agent_lab.rescore import rescore_run


def main() -> None:
    parser = argparse.ArgumentParser(description="Re-score an existing run from responses.jsonl.")
    parser.add_argument("run_dir")
    args = parser.parse_args()

    print(rescore_run(args.run_dir))


if __name__ == "__main__":
    main()
