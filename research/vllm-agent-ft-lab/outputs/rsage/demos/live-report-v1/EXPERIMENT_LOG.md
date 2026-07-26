# RSAGE 实验实时记录

> 本文件由只读训练监控器原子更新；模型、优化器与 checkpoint 不会被修改。

## 当前状态

| 项目 | 当前值 |
| --- | --- |
| 更新时间（UTC） | 2026-07-16T08:25:21+00:00 |
| 已解析训练日志 | 3 条 |
| 最新训练步数 | 3 |
| 最新 epoch | 0.75 |
| GPU 遥测 | 已采集 1 张 GPU 的遥测数据 |
| 末尾半行处理 | 无需处理 |

## 最新训练指标

| 指标 | 值 |
| --- | ---: |
| 训练损失 | 0.72 |
| 验证损失 | 0.79 |
| chosen 奖励 | 0.65 |
| rejected 奖励 | -0.08 |
| 奖励准确率 | 1 |
| 训练样本/秒 | 1.61 |

## 实时图表

- [训练与验证损失](charts/loss.svg)
- [偏好奖励](charts/reward.svg)
- [训练吞吐量](charts/throughput.svg)
- [GPU 遥测](charts/gpu.svg)

## 增量产物

- `trainer_log.jsonl`：训练器原始增量日志
- `events.jsonl`：中文生命周期事件（只追加）
- `progress_snapshots.jsonl`：监控进度快照（只追加）
- `gpu_metrics.jsonl`：GPU 遥测快照（只追加）
