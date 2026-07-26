import pytest

from pyt_agent_lab.model_registry import ModelRegistry, ModelState, RegistryEntry


def _entry(state: ModelState = ModelState.TRAINED) -> RegistryEntry:
    return RegistryEntry(
        model_id="candidate",
        adapter_path="outputs/adapters/candidate",
        state=state,
        schema_version="agent-schema-v2",
        evidence=[],
    )


def test_registry_requires_ordered_promotion() -> None:
    registry = ModelRegistry([_entry()])

    with pytest.raises(ValueError, match="invalid model transition"):
        registry.transition("candidate", ModelState.CHAMPION, evidence="decision.json")


def test_registry_records_transition_evidence(tmp_path) -> None:
    registry = ModelRegistry([_entry()])
    updated = registry.transition(
        "candidate", ModelState.GOLDEN_PASSED, evidence="golden-report.json"
    )
    target = tmp_path / "registry.json"
    registry.dump(target)

    loaded = ModelRegistry.load(target)
    assert updated.evidence == ["golden-report.json"]
    with pytest.raises(ValueError, match="invalid model transition"):
        loaded.transition("candidate", ModelState.CHAMPION, evidence="skip.json")


def test_registry_allows_only_one_champion() -> None:
    with pytest.raises(ValueError, match="only one champion"):
        ModelRegistry([
            _entry(ModelState.CHAMPION),
            RegistryEntry(
                model_id="other",
                adapter_path="other",
                state=ModelState.CHAMPION,
                schema_version="agent-schema-v1",
                evidence=[],
            ),
        ])
