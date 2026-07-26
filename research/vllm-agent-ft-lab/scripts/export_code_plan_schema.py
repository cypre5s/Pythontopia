#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


LAB_ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = LAB_ROOT.parents[1]
GRAPH_ROOT = REPO_ROOT / "services" / "pythontopia-graph"
sys.path.insert(0, str(GRAPH_ROOT))

from pythontopia_graph.contracts.semantic_ir import CodeLabPlanSemanticIR  # noqa: E402


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Export the compiler-owned CodePlan v2.1 evaluation contract."
    )
    parser.add_argument(
        "--output",
        default=str(
            LAB_ROOT
            / "schemas"
            / "agent"
            / "v2.1"
            / "resource_designer_code_lab.schema.json"
        ),
    )
    args = parser.parse_args()
    target = Path(args.output)
    target.parent.mkdir(parents=True, exist_ok=True)
    schema = CodeLabPlanSemanticIR.model_json_schema()
    schema["title"] = "ResourceDesignerAgent.code_lab"
    schema["x-schema-version"] = "agent-schema-v2.1"
    schema["x-product-contract"] = "semantic-code-plan-evidence-bound"
    schema["x-adapter-eligible"] = True
    target.write_text(
        json.dumps(schema, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(json.dumps({"output": str(target), "title": schema["title"]}))


if __name__ == "__main__":
    main()
