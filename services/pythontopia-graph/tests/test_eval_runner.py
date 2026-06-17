"""eval.runner 覆盖测试（O2-eval-harness）。

测试矩阵（对齐主契约 §3.6 / §4「Stream O」第 4 条 + §6 测试矩阵 ②③⑤）：

- load_dataset：解析 jsonl、跳空行、空文件边界、坏行带行号报错。
- run_dataset：注入 judge 汇总 mean 正确；error 样本排除出 mean；空数据集边界。
- Langfuse 推送：成功路径（断言 body / auth / dataType）；不可达（注入抛异常的 client）→
  本地分仍在、langfuse_pushed=0、不抛（fail-soft）；未配置 → 不推送。
- push_score_to_langfuse 直测四态（config None / 异常 / HTTP 错误 / 成功）。
- langfuse_config_from_env env 读取。
- discover_datasets / run_all 覆盖随仓库附带的 golden 数据集。
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import pytest

from pythontopia_graph.eval import runner, scorers


# ── 辅助：写临时 jsonl + 假 HTTP client ─────────────────────────────────────


def write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    text = "\n".join(json.dumps(row, ensure_ascii=False) for row in rows) + "\n"
    path.write_text(text, encoding="utf-8")


class FakeResponse:
    def __init__(self, status_code: int = 200) -> None:
        self.status_code = status_code

    def raise_for_status(self) -> None:
        if self.status_code >= 400:
            raise RuntimeError(f"http {self.status_code}")


class FakeClient:
    """记录调用的假 httpx client；可注入异常或非 2xx 状态。"""

    def __init__(self, exc: Exception | None = None, status_code: int = 200) -> None:
        self.calls: list[dict[str, Any]] = []
        self._exc = exc
        self._status = status_code

    def post(
        self, url: str, json: dict[str, Any] | None = None, auth: Any | None = None
    ) -> FakeResponse:
        self.calls.append({"url": url, "json": json, "auth": auth})
        if self._exc is not None:
            raise self._exc
        return FakeResponse(self._status)


def _cfg() -> runner.LangfuseConfig:
    return runner.LangfuseConfig(host="http://langfuse.local", public_key="pk", secret_key="sk")


# ── 1. load_dataset ─────────────────────────────────────────────────────────


def test_load_dataset_parses_samples(tmp_path: Path) -> None:
    path = tmp_path / "d.jsonl"
    write_jsonl(
        path,
        [
            {"id": "1", "question": "q1", "answer": "a1", "contexts": ["c1"], "reference": "r1"},
            {"id": "2", "question": "q2", "answer": "a2"},
        ],
    )
    samples = runner.load_dataset(path)
    assert len(samples) == 2
    assert samples[0].id == "1"
    assert samples[0].contexts == ["c1"]
    assert samples[1].contexts == []  # 默认空


def test_load_dataset_skips_blank_lines(tmp_path: Path) -> None:
    path = tmp_path / "d.jsonl"
    path.write_text('{"question":"q","answer":"a"}\n\n   \n', encoding="utf-8")
    assert len(runner.load_dataset(path)) == 1


def test_load_dataset_empty_file_returns_empty(tmp_path: Path) -> None:
    path = tmp_path / "empty.jsonl"
    path.write_text("", encoding="utf-8")
    assert runner.load_dataset(path) == []


def test_load_dataset_malformed_line_raises_with_lineno(tmp_path: Path) -> None:
    path = tmp_path / "bad.jsonl"
    path.write_text('{"question":"q","answer":"a"}\n{not valid json}\n', encoding="utf-8")
    with pytest.raises(ValueError) as exc_info:
        runner.load_dataset(path)
    assert ":2" in str(exc_info.value)


def test_load_dataset_missing_required_field_raises(tmp_path: Path) -> None:
    path = tmp_path / "miss.jsonl"
    path.write_text('{"question":"q only, no answer"}\n', encoding="utf-8")
    with pytest.raises(ValueError):
        runner.load_dataset(path)


# ── 2. run_dataset 汇总 ─────────────────────────────────────────────────────


def test_run_dataset_aggregates_means(tmp_path: Path) -> None:
    path = tmp_path / "d.jsonl"
    write_jsonl(path, [{"question": "q1", "answer": "a1"}, {"question": "q2", "answer": "a2"}])
    per_answer = {"a1": 0.4, "a2": 0.6}
    judge = lambda s: per_answer[s.answer]  # noqa: E731
    report = runner.run_dataset(path, judges=scorers.uniform_judges(judge))

    assert report.total_samples == 2
    summary = report.metrics[scorers.METRIC_FAITHFULNESS]
    assert summary.mean == pytest.approx(0.5)
    assert summary.count_scored == 2
    assert summary.count_skipped == 0
    assert summary.count_error == 0
    assert len(report.samples) == 2
    assert report.dataset  # 有名字


def test_run_dataset_all_default_metrics_present(tmp_path: Path) -> None:
    path = tmp_path / "d.jsonl"
    write_jsonl(path, [{"question": "q", "answer": "a"}])
    report = runner.run_dataset(path, judges=scorers.uniform_judges(lambda s: 0.7))
    assert set(report.metrics) == set(scorers.DEFAULT_METRICS)
    for metric in scorers.DEFAULT_METRICS:
        assert report.metrics[metric].mean == pytest.approx(0.7)


def test_run_dataset_excludes_error_samples_from_mean(tmp_path: Path) -> None:
    path = tmp_path / "d.jsonl"
    write_jsonl(path, [{"question": "q1", "answer": "good"}, {"question": "q2", "answer": "bad"}])

    def judge(sample: scorers.EvalSample) -> float:
        if sample.answer == "bad":
            raise ValueError("cannot score")
        return 0.8

    report = runner.run_dataset(path, judges=scorers.uniform_judges(judge))
    summary = report.metrics[scorers.METRIC_FAITHFULNESS]
    assert summary.mean == pytest.approx(0.8)  # 仅 1 个有效样本
    assert summary.count_scored == 1
    assert summary.count_error == 1


def test_run_dataset_accepts_samples_list() -> None:
    samples = [
        scorers.EvalSample(question="q1", answer="a1"),
        scorers.EvalSample(question="q2", answer="a2"),
    ]
    report = runner.run_dataset(
        samples, dataset_name="inmem", judges=scorers.uniform_judges(lambda s: 1.0)
    )
    assert report.dataset == "inmem"
    assert report.total_samples == 2
    assert report.metrics[scorers.METRIC_FAITHFULNESS].mean == pytest.approx(1.0)


# ── 3. 空数据集边界 ─────────────────────────────────────────────────────────


def test_run_dataset_empty_boundary(tmp_path: Path) -> None:
    path = tmp_path / "empty.jsonl"
    path.write_text("", encoding="utf-8")
    report = runner.run_dataset(path, judges=scorers.uniform_judges(lambda s: 0.5))
    assert report.total_samples == 0
    for metric in scorers.DEFAULT_METRICS:
        summary = report.metrics[metric]
        assert summary.mean is None
        assert summary.count_scored == 0
    assert report.langfuse_pushed == 0
    assert report.samples == []


# ── 4. Langfuse 推送 ────────────────────────────────────────────────────────


def test_run_dataset_pushes_to_langfuse_success(tmp_path: Path) -> None:
    path = tmp_path / "d.jsonl"
    write_jsonl(path, [{"question": "q", "answer": "a"}])
    client = FakeClient()
    report = runner.run_dataset(
        path,
        judges=scorers.uniform_judges(lambda s: 0.5),
        langfuse=_cfg(),
        http_client=client,
    )
    assert report.langfuse_enabled is True
    assert report.langfuse_pushed == len(scorers.DEFAULT_METRICS)  # 每个有 mean 的 metric 一条
    assert len(client.calls) == len(scorers.DEFAULT_METRICS)
    call = client.calls[0]
    assert call["url"].endswith("/api/public/scores")
    assert call["auth"] == ("pk", "sk")
    assert call["json"]["dataType"] == "NUMERIC"
    assert call["json"]["name"].startswith("eval.")
    assert call["json"]["value"] == pytest.approx(0.5)


def test_run_dataset_langfuse_unreachable_keeps_local_scores(tmp_path: Path) -> None:
    path = tmp_path / "d.jsonl"
    write_jsonl(path, [{"question": "q", "answer": "a"}])
    client = FakeClient(exc=ConnectionError("no route to host"))
    report = runner.run_dataset(
        path,
        judges=scorers.uniform_judges(lambda s: 0.5),
        langfuse=_cfg(),
        http_client=client,
    )
    # 关键：Langfuse 不可达，本地分仍完整、不抛、pushed=0。
    assert report.metrics[scorers.METRIC_FAITHFULNESS].mean == pytest.approx(0.5)
    assert report.langfuse_enabled is True
    assert report.langfuse_pushed == 0
    assert len(client.calls) == len(scorers.DEFAULT_METRICS)  # 每个都尝试过


def test_run_dataset_no_langfuse_does_not_push(tmp_path: Path) -> None:
    path = tmp_path / "d.jsonl"
    write_jsonl(path, [{"question": "q", "answer": "a"}])
    report = runner.run_dataset(path, judges=scorers.uniform_judges(lambda s: 0.5))
    assert report.langfuse_enabled is False
    assert report.langfuse_pushed == 0


def test_run_dataset_empty_does_not_push_even_with_langfuse(tmp_path: Path) -> None:
    path = tmp_path / "empty.jsonl"
    path.write_text("", encoding="utf-8")
    client = FakeClient()
    report = runner.run_dataset(
        path, judges=scorers.uniform_judges(lambda s: 0.5), langfuse=_cfg(), http_client=client
    )
    assert report.langfuse_pushed == 0
    assert client.calls == []  # 无 mean 可推


# ── 5. push_score_to_langfuse 直测（fail-soft 四态） ────────────────────────


def test_push_score_config_none_returns_false() -> None:
    assert runner.push_score_to_langfuse(None, name="eval.x", value=0.5) is False


def test_push_score_client_exception_returns_false() -> None:
    client = FakeClient(exc=TimeoutError("slow"))
    assert (
        runner.push_score_to_langfuse(_cfg(), name="eval.x", value=0.5, client=client) is False
    )


def test_push_score_http_error_returns_false() -> None:
    client = FakeClient(status_code=500)
    assert (
        runner.push_score_to_langfuse(_cfg(), name="eval.x", value=0.5, client=client) is False
    )


def test_push_score_success_returns_true_with_body() -> None:
    client = FakeClient()
    ok = runner.push_score_to_langfuse(
        _cfg(), name="eval.faithfulness", value=0.9, trace_id="t1", comment="c", client=client
    )
    assert ok is True
    body = client.calls[0]["json"]
    assert body == {
        "name": "eval.faithfulness",
        "value": 0.9,
        "dataType": "NUMERIC",
        "traceId": "t1",
        "comment": "c",
    }


def test_push_score_omits_optional_fields_when_blank() -> None:
    client = FakeClient()
    runner.push_score_to_langfuse(_cfg(), name="eval.x", value=0.3, client=client)
    body = client.calls[0]["json"]
    assert "traceId" not in body
    assert "comment" not in body
    assert body["value"] == pytest.approx(0.3)


# ── 6. langfuse_config_from_env ─────────────────────────────────────────────


def test_langfuse_config_from_env_missing_returns_none(monkeypatch: pytest.MonkeyPatch) -> None:
    for key in ("LANGFUSE_HOST", "LANGFUSE_PUBLIC_KEY", "LANGFUSE_SECRET_KEY"):
        monkeypatch.delenv(key, raising=False)
    assert runner.langfuse_config_from_env() is None


def test_langfuse_config_from_env_present(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("LANGFUSE_HOST", "http://lf.local/")
    monkeypatch.setenv("LANGFUSE_PUBLIC_KEY", "pk")
    monkeypatch.setenv("LANGFUSE_SECRET_KEY", "sk")
    cfg = runner.langfuse_config_from_env()
    assert cfg is not None
    assert cfg.host == "http://lf.local"  # 去尾斜杠
    assert cfg.public_key == "pk"
    assert cfg.secret_key == "sk"


def test_langfuse_config_from_env_partial_returns_none(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("LANGFUSE_HOST", "http://lf.local")
    monkeypatch.setenv("LANGFUSE_PUBLIC_KEY", "pk")
    monkeypatch.delenv("LANGFUSE_SECRET_KEY", raising=False)
    assert runner.langfuse_config_from_env() is None


# ── 7. discover_datasets / run_all（随仓库 golden 数据集） ───────────────────


def test_discover_datasets_finds_shipped() -> None:
    paths = runner.discover_datasets()
    assert len(paths) >= 1
    assert all(p.suffix == ".jsonl" for p in paths)
    assert all(p.exists() for p in paths)


def test_shipped_datasets_load_and_are_valid() -> None:
    for path in runner.discover_datasets():
        samples = runner.load_dataset(path)
        assert len(samples) >= 1
        for sample in samples:
            assert sample.question
            assert sample.answer


def test_run_all_over_shipped_datasets() -> None:
    reports = runner.run_all(judges=scorers.uniform_judges(lambda s: 0.75))
    assert len(reports) == len(runner.discover_datasets())
    assert len(reports) >= 1
    for report in reports:
        assert report.metrics[scorers.METRIC_FAITHFULNESS].mean == pytest.approx(0.75)
        assert report.total_samples >= 1


def test_run_all_fail_soft_langfuse_does_not_break(tmp_path: Path) -> None:
    # 自定义目录，langfuse 不可达 → run_all 仍返回完整本地报告。
    write_jsonl(tmp_path / "x.jsonl", [{"question": "q", "answer": "a"}])
    client = FakeClient(exc=ConnectionError("down"))
    reports = runner.run_all(
        directory=tmp_path,
        judges=scorers.uniform_judges(lambda s: 0.5),
        langfuse=_cfg(),
        http_client=client,
    )
    assert len(reports) == 1
    assert reports[0].metrics[scorers.METRIC_FAITHFULNESS].mean == pytest.approx(0.5)
    assert reports[0].langfuse_pushed == 0
