"""离线评测 runner：跑 golden 数据集 → 汇总分 → 可 fail-soft 推 Langfuse（REST）。

落地主契约 §3.6 第 2 条 / §4「Stream O」第 4 条：

- 读 ``eval/datasets/*.jsonl`` golden（每行一条 :class:`~pythontopia_graph.eval.scorers.EvalSample`），
  逐样本逐 metric 打分（judge 可注入 → 单测零外网 / 零真实 LLM），按 metric 汇总均值与计数。
- 评分结果**可推 Langfuse score**（REST ``POST /api/public/scores``，Basic auth ``pk:sk``）：
  未配置 / 不可达 / 非 2xx 一律 **fail-soft** —— 本地分照常返回、绝不抛、绝不拖垮主链路。

Langfuse 走**裸 REST**（httpx，核心依赖），不 import ``langfuse`` SDK（OTLP 接入由 O1 负责）。
"""

from __future__ import annotations

import json
import os
from collections.abc import Mapping, Sequence
from pathlib import Path
from typing import Any

import httpx
from pydantic import BaseModel, Field

from pythontopia_graph.eval import scorers
from pythontopia_graph.eval.scorers import (
    DEFAULT_METRICS,
    EvalSample,
    Judge,
    ScoreResult,
)

#: 随仓库附带的 golden 数据集目录（本文件同级 datasets/）。
DATASETS_DIR = Path(__file__).resolve().parent / "datasets"

#: 推送到 Langfuse 的 score 名前缀（``eval.faithfulness`` 等）。
SCORE_NAME_PREFIX = "eval"

_SCORES_PATH = "/api/public/scores"


# ── 数据模型 ───────────────────────────────────────────────────────────────────


class LangfuseConfig(BaseModel):
    """Langfuse REST 推送配置（host + 公私钥）。"""

    host: str
    public_key: str
    secret_key: str
    timeout: float = 5.0


class SampleEval(BaseModel):
    """单条样本的全 metric 打分。"""

    sample_id: str
    scores: list[ScoreResult] = Field(default_factory=list)


class MetricSummary(BaseModel):
    """单个 metric 在数据集上的汇总。"""

    metric: str
    mean: float | None = None  # None = 无有效样本
    count_scored: int = 0
    count_skipped: int = 0
    count_error: int = 0


class EvalReport(BaseModel):
    """单个数据集的评测报告。"""

    dataset: str
    total_samples: int = 0
    metrics: dict[str, MetricSummary] = Field(default_factory=dict)
    samples: list[SampleEval] = Field(default_factory=list)
    langfuse_enabled: bool = False
    langfuse_pushed: int = 0


# ── 数据集加载 / 发现 ──────────────────────────────────────────────────────────


def load_dataset(path: str | Path) -> list[EvalSample]:
    """读 jsonl golden 数据集 → ``list[EvalSample]``。

    - 跳过空行 / 纯空白行；
    - 坏行（JSON 解析失败 / 校验失败）抛 ``ValueError`` 并带 ``<path>:<lineno>`` 定位；
    - 空文件返回 ``[]``（空数据集边界）。
    """
    file_path = Path(path)
    samples: list[EvalSample] = []
    text = file_path.read_text(encoding="utf-8")
    for lineno, raw_line in enumerate(text.splitlines(), start=1):
        line = raw_line.strip()
        if not line:
            continue
        try:
            payload = json.loads(line)
            samples.append(EvalSample.model_validate(payload))
        except Exception as exc:  # noqa: BLE001 - 统一转可定位的数据错误（offline 工具应显式报错）
            raise ValueError(f"{file_path}:{lineno}: invalid eval sample: {exc}") from exc
    return samples


def discover_datasets(directory: str | Path | None = None) -> list[Path]:
    """列出目录下全部 ``*.jsonl`` 数据集（默认随仓库的 ``eval/datasets/``），按名排序。"""
    base = Path(directory) if directory is not None else DATASETS_DIR
    if not base.exists():
        return []
    return sorted(base.glob("*.jsonl"))


# ── 评测执行 ───────────────────────────────────────────────────────────────────


def run_dataset(
    source: str | Path | Sequence[EvalSample],
    *,
    dataset_name: str | None = None,
    metrics: Sequence[str] = DEFAULT_METRICS,
    judges: Mapping[str, Judge] | None = None,
    llm: Any | None = None,
    model: Any | None = None,
    langfuse: LangfuseConfig | None = None,
    http_client: Any | None = None,
    trace_id: str = "",
) -> EvalReport:
    """跑一个数据集（路径或内存样本列表）→ :class:`EvalReport`。

    ``judges`` 注入打分器（``{metric: Judge}``）；缺某 metric 的 judge 时该 metric 走真实
    ragas/deepeval 后端（缺包则 skipped）。``langfuse`` 非空时把各 metric 均值 fail-soft 推
    Langfuse；``http_client`` 可注入假 client（测试 / 复用连接）。
    """
    if isinstance(source, (str, Path)):
        samples = load_dataset(source)
        resolved_name = dataset_name or Path(source).name
    else:
        samples = list(source)
        resolved_name = dataset_name or "in-memory"

    summaries: dict[str, _MetricAccumulator] = {m: _MetricAccumulator(m) for m in metrics}
    sample_evals: list[SampleEval] = []

    for index, sample in enumerate(samples):
        results = scorers.score_sample(
            sample, metrics=metrics, judges=judges, llm=llm, model=model
        )
        for result in results:
            summaries[result.metric].add(result)
        sample_evals.append(
            SampleEval(sample_id=sample.id or f"#{index}", scores=results)
        )

    report = EvalReport(
        dataset=resolved_name,
        total_samples=len(samples),
        metrics={m: acc.summary() for m, acc in summaries.items()},
        samples=sample_evals,
        langfuse_enabled=langfuse is not None,
    )

    if langfuse is not None:
        report.langfuse_pushed = _push_report_scores(
            report, config=langfuse, client=http_client, trace_id=trace_id
        )
    return report


def run_all(
    directory: str | Path | None = None,
    *,
    metrics: Sequence[str] = DEFAULT_METRICS,
    judges: Mapping[str, Judge] | None = None,
    llm: Any | None = None,
    model: Any | None = None,
    langfuse: LangfuseConfig | None = None,
    http_client: Any | None = None,
    trace_id: str = "",
) -> list[EvalReport]:
    """对目录下全部数据集跑评测，返回每个数据集一份报告。"""
    reports: list[EvalReport] = []
    for path in discover_datasets(directory):
        reports.append(
            run_dataset(
                path,
                metrics=metrics,
                judges=judges,
                llm=llm,
                model=model,
                langfuse=langfuse,
                http_client=http_client,
                trace_id=trace_id,
            )
        )
    return reports


# ── Langfuse REST 推送（fail-soft）─────────────────────────────────────────────


def langfuse_config_from_env() -> LangfuseConfig | None:
    """从 ``LANGFUSE_HOST`` / ``LANGFUSE_PUBLIC_KEY`` / ``LANGFUSE_SECRET_KEY`` 读配置。

    三者缺一即返回 ``None``（不推送）。host 去尾斜杠。
    """
    host = os.getenv("LANGFUSE_HOST")
    public_key = os.getenv("LANGFUSE_PUBLIC_KEY")
    secret_key = os.getenv("LANGFUSE_SECRET_KEY")
    if not (host and public_key and secret_key):
        return None
    return LangfuseConfig(
        host=host.rstrip("/"), public_key=public_key, secret_key=secret_key
    )


def push_score_to_langfuse(
    config: LangfuseConfig | None,
    *,
    name: str,
    value: float,
    trace_id: str = "",
    comment: str = "",
    client: Any | None = None,
) -> bool:
    """向 Langfuse 推一条 NUMERIC score（REST）。**fail-soft**：成功 True，其余一律 False。

    未配置 / 网络异常 / 非 2xx 都被吞掉返回 False，**绝不抛**，从而评测主流程不被观测拖垮。
    """
    if config is None:
        return False
    body: dict[str, Any] = {"name": name, "value": float(value), "dataType": "NUMERIC"}
    if trace_id:
        body["traceId"] = trace_id
    if comment:
        body["comment"] = comment
    url = f"{config.host}{_SCORES_PATH}"
    auth = (config.public_key, config.secret_key)
    try:
        if client is not None:
            response = client.post(url, json=body, auth=auth)
            response.raise_for_status()
        else:
            with httpx.Client(timeout=config.timeout, trust_env=False) as http:
                response = http.post(url, json=body, auth=auth)
                response.raise_for_status()
        return True
    except Exception:  # noqa: BLE001 - 观测/评测推送 fail-soft：任何失败都不影响本地分
        return False


def _push_report_scores(
    report: EvalReport,
    *,
    config: LangfuseConfig,
    client: Any | None,
    trace_id: str,
) -> int:
    """把报告里每个有均值的 metric 推一条 Langfuse score；返回成功条数。"""
    pushed = 0
    for metric, summary in report.metrics.items():
        if summary.mean is None:
            continue
        ok = push_score_to_langfuse(
            config,
            name=f"{SCORE_NAME_PREFIX}.{metric}",
            value=summary.mean,
            trace_id=trace_id,
            comment=f"dataset={report.dataset} n={summary.count_scored}",
            client=client,
        )
        if ok:
            pushed += 1
    return pushed


# ── 内部：均值累加器 ───────────────────────────────────────────────────────────


class _MetricAccumulator:
    """逐样本累加单个 metric 的有效分与各状态计数。"""

    def __init__(self, metric: str) -> None:
        self._metric = metric
        self._sum = 0.0
        self._scored = 0
        self._skipped = 0
        self._error = 0

    def add(self, result: ScoreResult) -> None:
        if result.scored and result.value is not None:
            self._sum += result.value
            self._scored += 1
        elif result.status == "skipped":
            self._skipped += 1
        else:  # error（含 value 缺失的异常态）
            self._error += 1

    def summary(self) -> MetricSummary:
        mean = self._sum / self._scored if self._scored else None
        return MetricSummary(
            metric=self._metric,
            mean=mean,
            count_scored=self._scored,
            count_skipped=self._skipped,
            count_error=self._error,
        )


# ── 离线 CLI（``python -m pythontopia_graph.eval.runner``）──────────────────────


def main() -> None:
    """跑随仓库数据集并打印汇总；配置了 ``LANGFUSE_*`` 时 fail-soft 推送。

    注意：默认无注入 judge → 真实 ragas/deepeval 路径；缺包/缺评审 LLM 时各 metric skipped
    （仍打印报告，不报错），符合 fail-soft 语义。CI / 单测请用注入 judge。
    """
    config = langfuse_config_from_env()
    reports = run_all(langfuse=config)
    print(
        json.dumps(
            [report.model_dump() for report in reports], ensure_ascii=False, indent=2
        )
    )


if __name__ == "__main__":  # pragma: no cover - 仅离线手动入口
    main()
