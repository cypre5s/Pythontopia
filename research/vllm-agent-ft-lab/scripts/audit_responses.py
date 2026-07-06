#!/usr/bin/env python3
from __future__ import annotations

import argparse

from pyt_agent_lab.response_audit import audit_response_records, load_response_records, summarize_audits, write_audit_report


def main() -> None:
    parser = argparse.ArgumentParser(description="Audit experiment responses for protocol and data-contract risks.")
    parser.add_argument("--responses", required=True)
    parser.add_argument("--output-dir", required=True)
    args = parser.parse_args()

    audits = audit_response_records(load_response_records(args.responses))
    write_audit_report(audits, args.output_dir)
    print(summarize_audits(audits))


if __name__ == "__main__":
    main()
