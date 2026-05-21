# KC Mastery Formula

Pythontopia 用单一权威公式驱动 `kc_mastery` 更新；前端不允许做掌握度计算。

## 数据来源

- `kc_mastery_event(source, delta)`：每次事件追加 1 行，按时间倒序保留。
- `kc_mastery(mastery_score, confidence, last_event_at)`：当前快照。

## 事件 → delta 映射

| source             | 默认 delta | 触发阶段 |
| ------------------ | ---------- | -------- |
| `RESOURCE_READ`    | +0.10      | 阶段 07  |
| `PRACTICE_PASS`    | +0.20      | 阶段 09  |
| `PRACTICE_FAIL`    | -0.05      | 阶段 09  |
| `DEBUG_FIX`        | +0.15      | 阶段 10  |
| `MANUAL_OVERRIDE`  | 由教师指定 | 阶段 12  |
| `DECAY`            | 由衰减计算 | 本阶段   |
| `BOOTSTRAP`        | 0.00       | 本阶段   |

> 上述默认 delta 集中在 `application.yml` 的 `pythontopia.mastery.deltas` 命名空间，便于教学侧调参。

## 累积公式

```
mastery_score(new) = clamp(mastery_score(old) + delta, 0.0, 1.0)
```

## 置信度

`confidence` 与事件次数挂钩，使用 Beta 分布近似：

```
confidence(new) = clamp(confidence(old) + 0.05, 0.0, 1.0)   # 每次 success/practice/resource_read
confidence(new) = clamp(confidence(old) - 0.02, 0.0, 1.0)   # DECAY 触发
```

## 半衰期衰减

参数 `halfLifeDays` 默认 14（`pythontopia.mastery.halfLifeDays`）：

```
days = (now - last_event_at).total_days
mastery_score(new) = mastery_score(old) * 0.5 ^ (days / halfLifeDays)
```

衰减后会写入一条 `source=DECAY` 的 `kc_mastery_event`，`delta` 为新旧分差（负值）。

## 状态映射

| 状态        | 条件 |
| ----------- | ---- |
| `LOCKED`    | `crop_tile_state.unlocked = false` |
| `SEED`      | `unlocked = true` 且 `mastery < 0.2` |
| `SPROUT`    | `0.2 ≤ mastery < 0.5` |
| `MATURE`    | `0.5 ≤ mastery < 0.8` 或 `0.8 ≤ mastery` 但已收获 |
| `WITHERED`  | `last_event_at` 早于 30 天且 `mastery < 0.2` |

阶段 13 的复盘逻辑会重置 `MATURE → SEED` 完成"再播种"。

## 不变约束

- `mastery_score` ∈ `[0.0, 1.0]`。
- `confidence` ∈ `[0.0, 1.0]`。
- `delta` 可正可负，但应用后必须裁剪到合法区间。
- 同一学生同一 KC 只有一行 `kc_mastery` 快照。
- 每次更新必须追加 `kc_mastery_event`，禁止只改快照不写事件。
