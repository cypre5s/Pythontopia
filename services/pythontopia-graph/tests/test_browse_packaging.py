from __future__ import annotations

from pathlib import Path


GRAPH_ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = GRAPH_ROOT.parents[1]


def test_graph_image_installs_matching_playwright_and_chromium_runtime() -> None:
    pyproject = (GRAPH_ROOT / "pyproject.toml").read_text(encoding="utf-8")
    dockerfile = (GRAPH_ROOT / "Dockerfile").read_text(encoding="utf-8")

    assert '"playwright==1.62.0"' in pyproject
    assert '"browser-use' not in pyproject
    assert '".[browse]"' in dockerfile
    assert "playwright install --with-deps chromium" in dockerfile
    assert "PLAYWRIGHT_BROWSERS_PATH" in dockerfile


def test_production_compose_enables_fail_closed_single_concurrency_browse() -> None:
    compose = (REPO_ROOT / "deploy/docker-compose.production.yml").read_text(encoding="utf-8")

    assert 'BROWSE_ENABLED: "on"' in compose
    assert 'BROWSE_MAX_CONCURRENCY: "1"' in compose


def test_development_compose_exposes_the_same_real_browse_runtime() -> None:
    compose = (REPO_ROOT / "deploy/docker-compose.yml").read_text(encoding="utf-8")

    assert 'BROWSE_ENABLED: "on"' in compose
    assert 'BROWSE_MAX_CONCURRENCY: "1"' in compose
