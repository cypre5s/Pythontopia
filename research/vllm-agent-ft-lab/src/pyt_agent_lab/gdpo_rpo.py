from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Literal, Mapping, Sequence

import torch
import torch.nn.functional as F


Reduction = Literal["none", "mean", "sum"]


@dataclass(frozen=True)
class GdpoRpoLossParts:
    total: torch.Tensor
    gdpo: torch.Tensor
    chosen_nll: torch.Tensor


def summarize_preference_q(rows: Sequence[Mapping[str, Any]]) -> dict[str, Any]:
    if not rows:
        raise ValueError("软偏好数据不能为空")
    values: list[float] = []
    for index, row in enumerate(rows, start=1):
        raw = row.get("preference_q")
        if raw is None:
            raise ValueError(f"第 {index} 条软偏好数据缺少 preference_q")
        if isinstance(raw, bool) or not isinstance(raw, (int, float)):
            raise ValueError(f"第 {index} 条 preference_q 必须是数值")
        value = float(raw)
        if not torch.isfinite(torch.tensor(value)) or not 0.5 <= value <= 1.0:
            raise ValueError(f"第 {index} 条软偏好概率不在 0.5 到 1.0")
        values.append(value)
    return {
        "count": len(values),
        "minimum": min(values),
        "maximum": max(values),
        "mean": sum(values) / len(values),
        "half_count": sum(value == 0.5 for value in values),
        "unanimous_count": sum(value == 1.0 for value in values),
        "message_zh": f"已核验 {len(values)} 条软偏好概率，全部位于 0.5 到 1.0",
    }


def gdpo_loss(
    dpo_log_ratio: torch.Tensor,
    preference_q: torch.Tensor,
    *,
    beta: float,
    reduction: Reduction = "mean",
) -> torch.Tensor:
    """NeurIPS 2024 GDPO 的几何软偏好损失。

    公式：-log sigmoid(beta * (2q - 1) * dpo_log_ratio)。
    q=1 时等于普通 sigmoid DPO，q=0.5 时偏好项对 margin 的梯度为零。
    """

    if beta <= 0:
        raise ValueError("偏好温度 beta 必须大于零")
    if dpo_log_ratio.shape != preference_q.shape:
        raise ValueError("DPO 对数比与软偏好概率必须具有相同形状")
    if not torch.isfinite(preference_q).all() or bool(
        ((preference_q < 0.5) | (preference_q > 1.0)).any()
    ):
        raise ValueError("软偏好概率必须位于闭区间 0.5 到 1.0")
    scaled_margin = beta * (2.0 * preference_q - 1.0) * dpo_log_ratio
    per_example = -F.logsigmoid(scaled_margin)
    return _reduce(per_example, reduction)


def gdpo_rpo_loss(
    dpo_log_ratio: torch.Tensor,
    preference_q: torch.Tensor,
    chosen_nll: torch.Tensor,
    *,
    beta: float,
    lambda_sft: float,
    reduction: Reduction = "mean",
) -> GdpoRpoLossParts:
    if lambda_sft < 0:
        raise ValueError("chosen NLL 正则系数 lambda_sft 不能小于零")
    if chosen_nll.shape != dpo_log_ratio.shape:
        raise ValueError("chosen NLL 与 DPO 对数比必须具有相同形状")
    gdpo_per_example = gdpo_loss(
        dpo_log_ratio,
        preference_q,
        beta=beta,
        reduction="none",
    )
    total_per_example = gdpo_per_example + lambda_sft * chosen_nll
    return GdpoRpoLossParts(
        total=_reduce(total_per_example, reduction),
        gdpo=_reduce(gdpo_per_example, reduction),
        chosen_nll=_reduce(chosen_nll, reduction),
    )


def create_preference_q_collator(*, pad_token_id: int, pad_to_multiple_of: int | None = None) -> Any:
    """延迟创建 TRL collator，使纯损失测试不依赖训练虚拟环境。"""

    try:
        from trl.trainer.dpo_trainer import DataCollatorForPreference
    except ImportError as exc:  # pragma: no cover - 仅训练环境触发
        raise RuntimeError("训练虚拟环境缺少 TRL，无法创建软偏好批处理器") from exc

    class PreferenceQCollator(DataCollatorForPreference):
        def torch_call(self, examples: list[dict[str, Any]]) -> dict[str, Any]:
            output = super().torch_call(examples)
            values = [float(example["preference_q"]) for example in examples]
            q = torch.tensor(values, dtype=torch.float32)
            if not torch.isfinite(q).all() or bool(((q < 0.5) | (q > 1.0)).any()):
                raise ValueError("批次中的软偏好概率必须位于闭区间 0.5 到 1.0")
            output["preference_q"] = q
            return output

    return PreferenceQCollator(
        pad_token_id=pad_token_id,
        pad_to_multiple_of=pad_to_multiple_of,
    )


def create_gdpo_rpo_trainer_class() -> type[Any]:
    """为本地 TRL 0.29.1 构造 batch=1 的最窄 GDPO+RPO 子类。

    TRL 0.29.1 已原生支持 ``loss_type=["sigmoid", "sft"]``。在单样本
    microbatch 下，将 beta 临时缩放为 ``beta * (2q - 1)`` 与 GDPO 公式完全
    等价，同时 chosen-SFT 分支不读取 beta，因而 RPO 正则保持独立。
    """

    try:
        import trl.trainer.dpo_trainer as trl_dpo
    except ImportError as exc:  # pragma: no cover - 仅训练环境触发
        raise RuntimeError("训练虚拟环境缺少 TRL，无法创建 GDPO+RPO Trainer") from exc

    class GdpoRpoTrainer(trl_dpo.DPOTrainer):
        def __init__(self, *args: Any, lambda_sft: float, **kwargs: Any) -> None:
            trainer_args = kwargs.get("args")
            if trainer_args is not None:
                if trainer_args.use_liger_kernel:
                    raise ValueError("GDPO+RPO 暂不支持 Liger 融合损失")
                if trainer_args.f_divergence_type != "reverse_kl":
                    raise ValueError("GDPO+RPO 第一版只支持 reverse_kl")
                if trainer_args.use_weighting or trainer_args.ld_alpha is not None:
                    raise ValueError("GDPO+RPO 第一版不与 WPO 或长度分解损失叠加")
                if trainer_args.per_device_train_batch_size != 1 or trainer_args.per_device_eval_batch_size != 1:
                    raise ValueError("GDPO 动态 beta 精确实现要求训练和评测 microbatch 均为 1")
                if list(trainer_args.loss_type) != ["sigmoid", "sft"]:
                    raise ValueError("GDPO+RPO 必须使用 sigmoid 与 sft 两个独立损失分支")
                expected_weights = [1.0, float(lambda_sft)]
                actual_weights = list(trainer_args.loss_weights or [])
                if actual_weights != expected_weights:
                    raise ValueError(f"GDPO+RPO 损失权重必须是 {expected_weights}")
            if lambda_sft < 0:
                raise ValueError("chosen NLL 正则系数 lambda_sft 不能小于零")
            self.lambda_sft = float(lambda_sft)
            super().__init__(*args, **kwargs)

        def _set_signature_columns_if_needed(self) -> None:
            super()._set_signature_columns_if_needed()
            if self._signature_columns is not None and "preference_q" not in self._signature_columns:
                self._signature_columns.append("preference_q")

        def compute_loss(
            self,
            model: Any,
            inputs: dict[str, torch.Tensor],
            return_outputs: bool = False,
            num_items_in_batch: Any = None,
        ) -> Any:
            local_inputs = dict(inputs)
            q = local_inputs.pop("preference_q", None)
            if q is None:
                raise ValueError("GDPO 批次缺少 preference_q")
            q = q.detach().float().reshape(-1)
            if q.numel() != 1:
                raise ValueError("GDPO 动态 beta 精确实现只接受单样本 microbatch")
            q_value = float(q.item())
            if not torch.isfinite(q).all() or not 0.5 <= q_value <= 1.0:
                raise ValueError("批次中的软偏好概率必须位于闭区间 0.5 到 1.0")

            base_beta = float(self.beta)
            self.beta = base_beta * (2.0 * q_value - 1.0)
            mode = "train" if self.model.training else "eval"
            self._metrics[mode]["preference_q/mean"].append(q_value)
            self._metrics[mode]["preference_scale/mean"].append(2.0 * q_value - 1.0)
            try:
                return super().compute_loss(
                    model,
                    local_inputs,
                    return_outputs=return_outputs,
                    num_items_in_batch=num_items_in_batch,
                )
            finally:
                self.beta = base_beta

    GdpoRpoTrainer.__name__ = "GdpoRpoTrainer"
    return GdpoRpoTrainer


def _reduce(value: torch.Tensor, reduction: Reduction) -> torch.Tensor:
    if reduction == "none":
        return value
    if reduction == "mean":
        return value.mean()
    if reduction == "sum":
        return value.sum()
    raise ValueError(f"未知损失归约方式：{reduction}")
