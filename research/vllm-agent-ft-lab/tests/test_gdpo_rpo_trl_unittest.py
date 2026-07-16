from __future__ import annotations

import unittest
from collections import defaultdict
from types import SimpleNamespace
from unittest.mock import patch

import torch

from pyt_agent_lab.gdpo_rpo import (
    create_gdpo_rpo_trainer_class,
    create_preference_q_collator,
)

try:
    from trl import DPOTrainer
except ImportError:
    DPOTrainer = None  # type: ignore[assignment,misc]


@unittest.skipUnless(DPOTrainer is not None, "仅在训练虚拟环境验证 TRL 接入")
class GdpoRpoTrlIntegrationTest(unittest.TestCase):
    def _trainer_without_init(self):  # noqa: ANN201
        trainer_class = create_gdpo_rpo_trainer_class()
        trainer = object.__new__(trainer_class)
        trainer.beta = 0.1
        trainer.model = SimpleNamespace(training=True)
        trainer._metrics = {"train": defaultdict(list), "eval": defaultdict(list)}
        return trainer

    def test_dynamic_beta_scales_single_q_and_restores_base_value(self) -> None:
        trainer = self._trainer_without_init()
        seen: list[float] = []
        test_case = self

        def fake_parent(self, model, inputs, return_outputs=False, num_items_in_batch=None):  # noqa: ANN001
            seen.append(float(self.beta))
            test_case.assertNotIn("preference_q", inputs)
            return torch.tensor(self.beta)

        with patch.object(DPOTrainer, "compute_loss", fake_parent):
            result = trainer.compute_loss(None, {"preference_q": torch.tensor([0.75])})

        self.assertAlmostEqual(seen[0], 0.05)
        self.assertAlmostEqual(float(result.item()), 0.05)
        self.assertAlmostEqual(trainer.beta, 0.1)

    def test_dynamic_beta_restores_base_value_when_parent_raises(self) -> None:
        trainer = self._trainer_without_init()

        def fail_parent(self, model, inputs, return_outputs=False, num_items_in_batch=None):  # noqa: ANN001
            raise RuntimeError("测试异常")

        with patch.object(DPOTrainer, "compute_loss", fail_parent):
            with self.assertRaisesRegex(RuntimeError, "测试异常"):
                trainer.compute_loss(None, {"preference_q": torch.tensor([1.0])})

        self.assertAlmostEqual(trainer.beta, 0.1)

    def test_dynamic_beta_rejects_multi_sample_microbatch(self) -> None:
        trainer = self._trainer_without_init()

        with self.assertRaisesRegex(ValueError, "单样本"):
            trainer.compute_loss(None, {"preference_q": torch.tensor([0.6, 0.8])})

    def test_collator_preserves_one_q_per_pair(self) -> None:
        collator = create_preference_q_collator(pad_token_id=0)
        batch = collator(
            [
                {"prompt_ids": [1], "chosen_ids": [2], "rejected_ids": [3], "preference_q": 2 / 3},
                {"prompt_ids": [4], "chosen_ids": [5], "rejected_ids": [6], "preference_q": 1.0},
            ]
        )

        self.assertEqual(tuple(batch["preference_q"].shape), (2,))
        self.assertEqual(batch["preference_q"].dtype, torch.float32)
        self.assertTrue(torch.allclose(batch["preference_q"], torch.tensor([2 / 3, 1.0])))


if __name__ == "__main__":
    unittest.main()
