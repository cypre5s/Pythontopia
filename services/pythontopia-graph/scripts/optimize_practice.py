#!/usr/bin/env python3
"""离线 GEPA 编译：百晓工坊「练习题生成」（practice_generate）。

仅离线运行（P1-dspy-compile · §3.5）。把 :func:`pythontopia_graph.optimize.programs.build_program`
的结构化出题程序经 GEPA（反馈型 metric：grounding/safety 防泄答 + 难度对齐）优化，导出冻结产物到
``pythontopia_graph/optimize/artifacts/practice_generate.json``，供运行时 P2 loader fail-soft 读取。

运行时**绝不**导入/调用本脚本；它不属于 ``pythontopia_graph`` 包。

用法::

    python3 scripts/optimize_practice.py \
        --task-model deepseek-v4-pro --reflection-model deepseek-flash --auto light

依赖：``pip install -e '.[optimize]'``（dspy>=3.2,<4）。缺 dspy → 清晰报错并退出码 2。
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

# 让脚本能 import 到 pythontopia_graph（脚本不在包内）。
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

PROGRAM_NAME = "practice_generate"


def _parse_args(argv: list[str] | None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=f"离线 GEPA 编译 {PROGRAM_NAME}")
    parser.add_argument("--task-model", default=None, help="主任务模型名（默认读 env PYT_OPENAI_MODEL 等）")
    parser.add_argument("--reflection-model", default=None, help="GEPA 反思模型名（默认同 task-model）")
    parser.add_argument("--base-url", default=None, help="OpenAI 兼容 base_url（默认读 env）")
    parser.add_argument("--api-key", default=None, help="OpenAI 兼容 api_key（默认读 env）")
    parser.add_argument(
        "--auto", default="light", choices=["light", "medium", "heavy"], help="GEPA 预算档"
    )
    parser.add_argument(
        "--max-full-evals", type=int, default=None, help="改用全量评估预算（与 --auto 互斥优先）"
    )
    parser.add_argument("--out-dir", default=None, help="产物目录覆盖（默认 optimize/artifacts/）")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = _parse_args(argv)
    from pythontopia_graph.optimize import compile as compile_mod
    from pythontopia_graph.optimize.programs import OptimizeDependencyError

    try:
        task_lm = compile_mod.build_openai_lm(
            args.task_model, base_url=args.base_url, api_key=args.api_key,
            temperature=0.2, max_tokens=4000,
        )
        reflection_lm = compile_mod.build_openai_lm(
            args.reflection_model or args.task_model, base_url=args.base_url,
            api_key=args.api_key, temperature=1.0, max_tokens=8000,
        )
        path = compile_mod.run_offline_compile(
            PROGRAM_NAME,
            task_lm=task_lm,
            reflection_lm=reflection_lm,
            auto=None if args.max_full_evals is not None else args.auto,
            max_full_evals=args.max_full_evals,
            artifacts_dir=args.out_dir,
        )
    except OptimizeDependencyError as exc:
        print(f"[optimize:{PROGRAM_NAME}] 依赖缺失：{exc}", file=sys.stderr)
        return 2
    except ValueError as exc:
        print(f"[optimize:{PROGRAM_NAME}] 配置错误：{exc}", file=sys.stderr)
        return 2

    print(f"[optimize:{PROGRAM_NAME}] 冻结产物已导出：{path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
