#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path

from pyt_agent_lab.rsage_pipeline import PipelineError, run_rsage_pipeline


class _ChineseArgumentParser(argparse.ArgumentParser):
    def format_usage(self) -> str:
        return super().format_usage().replace("usage:", "用法：", 1)

    def format_help(self) -> str:
        return super().format_help().replace("usage:", "用法：", 1)

    def error(self, message: str) -> None:
        self.print_usage()
        self.exit(2, f"{self.prog}：参数错误：{message}\n")


def _build_parser() -> argparse.ArgumentParser:
    parser = _ChineseArgumentParser(
        description="按 manifest 编排、恢复并审计 R-SAGE 自动标注与训练流水线。",
        add_help=False,
        usage="%(prog)s --manifest 清单.json [运行模式] [阶段选择] [--resume]",
    )
    parser._positionals.title = "位置参数"
    parser._optionals.title = "选项"
    parser.add_argument("-h", "--help", action="help", help="显示此帮助并退出。")
    parser.add_argument(
        "--manifest",
        default="research/vllm-agent-ft-lab/outputs/rsage/pipeline/manifest.json",
        help="流水线 JSON manifest 路径。",
    )
    parser.add_argument("--run-root", help="覆盖 manifest 中的运行根目录。")
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument("--demo", action="store_true", help="只运行隔离的 demo 阶段。")
    modes.add_argument(
        "--prepare-only",
        action="store_true",
        help="只运行 full 模式的数据与计划准备阶段；这是默认模式。",
    )
    modes.add_argument(
        "--start-training",
        action="store_true",
        help="显式启动训练及后续评测；所有当前 demo 回执必须已通过。",
    )
    parser.add_argument(
        "--resume",
        action="store_true",
        help="恢复相同命令哈希的运行，并跳过已完成阶段。",
    )
    parser.add_argument(
        "--stage",
        action="append",
        dest="stages",
        help="仅执行指定阶段；可重复传入。",
    )
    parser.add_argument("--from-stage", help="从指定阶段开始执行。")
    parser.add_argument("--through-stage", help="执行到指定阶段为止，包含该阶段。")
    return parser


def main() -> None:
    parser = _build_parser()
    args = parser.parse_args()
    if args.stages and (args.from_stage or args.through_stage):
        parser.error("--stage 不能与 --from-stage 或 --through-stage 同时使用")
    operation = "demo" if args.demo else "training" if args.start_training else "prepare"
    summary = run_rsage_pipeline(
        Path(args.manifest),
        operation=operation,
        resume=args.resume,
        selected_stages=args.stages,
        from_stage=args.from_stage,
        through_stage=args.through_stage,
        run_root=args.run_root,
    )
    for stage in summary["stages"]:
        if stage["action"] == "resume_skip":
            print(f"恢复跳过阶段：{stage['stage_id']}", flush=True)
        else:
            print(f"阶段完成：{stage['stage_id']}", flush=True)
    print(summary["message_zh"], flush=True)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        raise SystemExit("R-SAGE 流水线已由操作者中止；现有阶段证据已保留") from None
    except PipelineError as exc:
        raise SystemExit(f"R-SAGE 流水线失败：{exc}") from None
    except Exception as exc:  # noqa: BLE001 - CLI 统一转换为中文错误边界
        raise SystemExit(f"R-SAGE 流水线发生未预期错误：{exc}") from None
