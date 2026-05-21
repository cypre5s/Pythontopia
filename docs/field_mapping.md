# 知识田教育映射

Pythontopia 把"星露谷式像素小镇"的视觉语言**全部约束在教学叙事上**。任何
不能映射回教学行为的装饰元素都不允许引入。

## 核心映射

| 像素小镇语义 | 教学语义 | 数据来源 |
| ------------ | -------- | -------- |
| 田垄一格    | 1 个 KC | `course_kc` |
| 种子        | 已解锁但未掌握 | `kc_mastery.mastery_score < 0.2` |
| 幼苗        | 掌握度上升中 | `0.2 ≤ mastery < 0.5` |
| 成熟作物    | 掌握度接近通过 | `0.5 ≤ mastery < 0.8` |
| 收获        | 学生在该 KC 上稳定通过练习 | `mastery ≥ 0.8` 且最近练习成功 |
| 枯萎        | 30 天未见进展，需要复盘 | `last_event_at` 久远 + 衰减触发 |
| 浇水/施肥   | 阅读学习资源 | `kc_mastery_event.source = RESOURCE_READ` |
| 除草        | 在训练场练习并通过 | `PRACTICE_PASS` |
| 病害/矿洞事件 | 提交失败/Bug 调试 | `PRACTICE_FAIL` / `DEBUG_FIX` |
| 天气        | 学习节奏（连续学习/休息日） | `weather_state.current_weather` |

## 不允许的扩展

- 不允许添加纯装饰 NPC（如农场动物、商店店员）。
- 不允许加入与教学无关的经济系统（金币、商品）。
- 不允许在前端独立计算 mastery，前端只展示后端权威值。

## 不解锁规则

- 学生未完成画像（`learner_profile` 空）时，无法触发 `bootstrap`；前端引导先去画像屋。
- 默认解锁前 8 个 KC（按 `chapter_order` + `difficulty` 升序）。
- 其余 KC 通过阶段 08 的 `PathPlannerAgent` 推进时解锁。

## 视觉风格

- 像素小镇风格参考星露谷，但不复制官方素材。`assets/pixel/LICENSE.md` 记录所有素材来源。
- 田垄、作物、季节、天气元素使用同一配色板，保证整体视觉一致。
