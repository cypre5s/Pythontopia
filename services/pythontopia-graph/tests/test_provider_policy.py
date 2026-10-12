"""Disabled providers must not remain callable or exported as alternate runtimes."""

import pytest

import pythontopia_graph.llm as llm
import pythontopia_graph.llm.provider as provider
from pythontopia_graph.llm.provider import provider_from_env


def test_spark_provider_and_cross_project_env_loader_are_not_exported(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("PYT_LLM_PROVIDER", "spark")

    assert not hasattr(provider, "SparkProvider")
    assert not hasattr(llm, "SparkProvider")
    assert not hasattr(provider, "load_alethicode_env")
    assert not hasattr(llm, "load_alethicode_env")
    with pytest.raises(RuntimeError, match="Spark LLM is disabled"):
        provider_from_env()
