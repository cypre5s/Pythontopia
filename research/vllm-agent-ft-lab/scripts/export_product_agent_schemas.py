from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


LAB_ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = LAB_ROOT.parents[1]
GRAPH_ROOT = REPO_ROOT / "services" / "pythontopia-graph"
sys.path.insert(0, str(GRAPH_ROOT))

from pythontopia_graph.contracts.semantic_ir import (  # noqa: E402
    CodeLabSemanticIR,
    GuideCoachSemanticIR,
    HandoutSemanticIR,
    IntentRouterSemanticIR,
    MindMapSemanticIR,
    PracticeSemanticIR,
    ReadingSemanticIR,
    ReflectionSemanticIR,
    StoryboardSemanticIR,
)
from pythontopia_graph.nodes.grounding_critic import GroundingReport  # noqa: E402
from pythontopia_graph.schemas.debug import DebugAgentOutput  # noqa: E402
from pythontopia_graph.schemas.path import PathPlannerResponse  # noqa: E402
from pythontopia_graph.schemas.resource import (  # noqa: E402
    ResourceDraft,
    SafetyResult,
)
from pythontopia_graph.schemas.tutor import TutorResponse  # noqa: E402


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Export product semantic IR schemas into the offline evaluation registry."
    )
    parser.add_argument(
        "--output", default=str(LAB_ROOT / "schemas" / "agent" / "v2")
    )
    args = parser.parse_args()
    target = Path(args.output)
    target.mkdir(parents=True, exist_ok=True)
    schemas = {
        "guide_coach": ("GuideCoachAgent", GuideCoachSemanticIR, "semantic-ir", True),
        "practice": ("PracticeAgent", PracticeSemanticIR, "semantic-ir", True),
        "intent_router": ("IntentRouterAgent", IntentRouterSemanticIR, "semantic-ir", True),
        "mind_map": ("MindMapAgent", MindMapSemanticIR, "semantic-ir", True),
        "reflection": (
            "ReflectionAgent",
            ReflectionSemanticIR,
            "semantic-ir-event-evidence-bound",
            True,
        ),
        "resource_designer_handout": (
            "ResourceDesignerAgent.handout",
            HandoutSemanticIR,
            "semantic-ir-evidence-bound",
            True,
        ),
        "resource_designer_reading": (
            "ResourceDesignerAgent.reading",
            ReadingSemanticIR,
            "semantic-ir-evidence-bound",
            True,
        ),
        "resource_designer_code_lab": (
            "ResourceDesignerAgent.code_lab",
            CodeLabSemanticIR,
            "semantic-ir-evidence-bound",
            True,
        ),
        "resource_designer_storyboard": (
            "ResourceDesignerAgent.storyboard",
            StoryboardSemanticIR,
            "semantic-ir-evidence-bound",
            True,
        ),
        "debug": ("DebugAgent", DebugAgentOutput, "deterministic-product-output", False),
        "path_planner": (
            "PathPlannerAgent",
            PathPlannerResponse,
            "deterministic-product-output",
            False,
        ),
        "grounding_critic": (
            "GroundingCriticAgent",
            GroundingReport,
            "deterministic-product-output",
            False,
        ),
        "safety": ("SafetyAgent", SafetyResult, "deterministic-product-output", False),
        "tutor": ("TutorAgent", TutorResponse, "streaming-product-output", False),
        "resource_designer": (
            "ResourceDesignerAgent",
            ResourceDraft,
            "subagent-ir-split-in-progress",
            False,
        ),
    }
    for name, (agent, model, contract_kind, adapter_eligible) in schemas.items():
        schema = model.model_json_schema()
        schema["title"] = agent
        schema["x-schema-version"] = "agent-schema-v2"
        schema["x-product-contract"] = contract_kind
        schema["x-adapter-eligible"] = adapter_eligible
        (target / f"{name}.schema.json").write_text(
            json.dumps(schema, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )


if __name__ == "__main__":
    main()
