#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json

from pyt_agent_lab.shadow_routing import (
    compose_shadow_route,
    load_jsonl,
    write_shadow_route,
)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Compose paired champion/specialist responses for offline route evaluation."
    )
    parser.add_argument("--champion", required=True)
    parser.add_argument("--specialist", required=True)
    parser.add_argument("--specialist-agent", action="append", required=True)
    parser.add_argument("--specialist-schema-version", action="append", default=[])
    parser.add_argument("--output-dir", required=True)
    args = parser.parse_args()
    routed, decisions = compose_shadow_route(
        champion_records=load_jsonl(args.champion),
        specialist_records=load_jsonl(args.specialist),
        specialist_agents=args.specialist_agent,
        specialist_schema_versions=args.specialist_schema_version,
    )
    manifest = write_shadow_route(
        routed_records=routed,
        decisions=decisions,
        output_dir=args.output_dir,
        champion_source=args.champion,
        specialist_source=args.specialist,
    )
    print(json.dumps(manifest, ensure_ascii=False, sort_keys=True))


if __name__ == "__main__":
    main()
