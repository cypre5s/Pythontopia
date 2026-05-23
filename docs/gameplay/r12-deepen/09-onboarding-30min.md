---
title: Pythontopia 首小时新手引导脚本（前 30 分钟）
version: v1.0
date: 2026-05-24
status: Draft
owner: 玩法策划 + 教育体验
related:
  - docs/gameplay/player-progression.md
  - docs/gameplay/npc-ecosystem.md
  - docs/gameplay/world-clock-calendar.md
  - docs/quick_start_demo.md
---

# 09 · 30 分钟新手引导脚本（First-Hour Onboarding）

> 适用范围：R-12 解冻后，玩家首次进入 `/world` 到第 30 真实分钟结束。
> 上游约束：`docs/gameplay/player-progression.md` §2「前 30 分钟黄金窗口」、`docs/gameplay/npc-ecosystem.md` §8「NPC 与教育耦合」、AGENTS.md「禁止无教育目的的装饰系统」。
> 唯一权威 NPC 数据源：`data/npcs/<key>/dialog.json`，本文新增节点必须先回写源 JSON，禁止 scene 层硬编码。

---

## 0. 文档定位

**留存 ≠ 漂亮的过场动画**。30 分钟里玩家会暴露 3 个真问题：会不会走、能不能学、想不想留。三个答案都必须用**可统计的事件**回答：

- 会不会走 → `world_session.tile_x/y` 是否离开镇长家 9×9 房间。
- 能不能学 → `kc_mastery_event.source` 是否出现 ≥ 1 条 `RESOURCE_READ` + ≥ 1 条 `PRACTICE_PASS`。
- 想不想留 → `world_event(type='onboarding_completed')` 是否在 30 分钟内落库。

任何 beat 都必须服务上述三问之一，纯炫技镜头 / 纯叙事铺垫一律砍掉。本脚本与 `player-progression.md` §2 的 5 行总览表互为细化关系：那里是"30 分钟分 5 段"，这里是"30 分钟分 14 拍、每拍秒级"。

> 设计原则：**"教 1 个 Python 概念 + 走 1 段路 + 见 1 个 NPC + 触 1 次后端事件"** 是每个 beat 的最小完整单元，缺一项的 beat 直接砍。

---

## 1. 30 分钟切片总览（按真实秒数）

| 节拍 | 时长 | 起止秒数 | 标题 | 主导 NPC / 系统 | 教学要点 | 关键事件 |
| ---- | ---- | -------- | ---- | --------------- | -------- | -------- |
| B01 | 30s | 0:00–0:30 | 登录 + 角色创建 | LoginShell | —— | `auth.signup` / `player.create` |
| B02 | 60s | 0:30–1:30 | 8 维画像首问（5 题速答） | `interviewer_eva`（屏前简版） | —— | `POST /api/profile/interview` |
| B03 | 90s | 1:30–3:00 | 出生在镇长家 + 第一行 `print` | `mayor_py` | print 函数 / 字符串字面量 | `kc_mastery_event(KC-014, RESOURCE_READ, +0.05)` |
| B04 | 90s | 3:00–4:30 | 出门 + WASD 自由移动 | 自由探索 | 坐标系隐喻 | `LearningEvent.player.move` |
| B05 | 30s | 4:30–5:00 | 抵达中央广场 | `gardener` 远观 | —— | `world_session.tile_x/y` 写镇广场 |
| B06 | 180s | 5:00–8:00 | 偶遇 5 同学（各 1 句） | 5×student NPC | 学习类型自我映射 | `npc_dialog_log` ×5 |
| B07 | 240s | 8:00–12:00 | 微任务：图书馆借第 1 页课件 | `librarian_doc` | "Python 是什么" | `PageReadEvent(page=10707)` |
| B08 | 180s | 12:00–15:00 | 回家路上遇田老接 KC 种子 | `farmer_kc` | KC-002 计算工具演变 | `quest.create('plant_first_seed')` |
| B09 | 300s | 15:00–20:00 | 农场浇第 1 颗 KC（做 1 道入门题） | `farmer_kc` + CodeLab | `print` + 字符串 | `PRACTICE_PASS, +0.20` |
| B10 | 180s | 20:00–23:00 | 收获 10 Pythoco + 回镇长汇报 | `farmer_kc` + `mayor_py` | 反馈闭环 | `kc_mastery` SEED→SPROUT |
| B11 | 180s | 23:00–26:00 | 班级 joinCode 提示 | `mayor_py` | 班级隶属 | `quest.create('join_class')` |
| B12 | 120s | 26:00–28:00 | HUD 全功能解锁动画 | `HudShell` | 5 槽位语义 | `learner_flag.hud_unlocked=true` |
| B13 | 120s | 28:00–30:00 | "今天先到这"睡觉推日 | `mayor_py` | 节奏感 | `POST /api/world/advance/sleep` |
| B14 | 0s | 30:00 | onboarding 结算 | 后端 | —— | `world_event(onboarding_completed)` |

> 14 拍总耗时严格 = 30 真实分钟。任何节拍延迟 > 20%（如 B09 玩家卡 360s 仍未提交）触发 §4「失败兜底」分支，不让玩家自己摸索 1 小时也走不出去。

---

## 2. 节拍详表（按 beat 秒级展开）

每张表 6 列：触发条件 / 教学要点（具体 KC） / 玩家可见 UI / NPC 对话原文（中文，2–4 句） / 离场条件 / 失败兜底（指向 §4）。

### B01 · 0:00–0:30 · 登录 + 角色创建

| 字段 | 内容 |
| ---- | ---- |
| 触发 | 路径 `/login` 首登或 `user.created_at` < 24h |
| 教学要点 | 无（仅入口） |
| UI | LoginShell 表单 + "继续"按钮；登录成功后弹 1 张"取个昵称"模态框；昵称非空即可，**不强制真实姓名** |
| 对话原文 | 模态框文案："给自己起个昵称吧，3–12 个字，中英文皆可。这个名字会被镇上 20 位 NPC 喊到。" |
| 离场 | 提交昵称 → `playerName` 写 `users.display_name` |
| 兜底 | 昵称重复 → 自动补 4 位 hash 后缀，不打断流程 |

### B02 · 0:30–1:30 · 画像首问 5 题速答

| 字段 | 内容 |
| ---- | ---- |
| 触发 | 模态框关闭后立即弹 PicassoLite（5 题精简版） |
| 教学要点 | 无（采集，不教学）；写主观 4 维 + 1 维（专业、自评、目标、偏好、易错自识） |
| UI | 单页 5 题，每题 4 单选 + 1 自填，进度条 1/5–5/5，**底部"先跳过"按钮**永远可见 |
| 对话原文 | 题 1："你的专业方向？" / 题 2："你之前接触过 Python 吗？" / 题 3："这门课你想拿到什么？" / 题 4："你更喜欢哪种学法？" / 题 5："上一次写代码最常被卡在哪儿？" |
| 离场 | 提交 → `POST /api/profile/interview`；跳过 → 5 题置 `null` 并标 `dimensions.skipped=true` |
| 兜底 | 用户停留 > 60s 未点 → 自动提示 "可以先跳过，回头还能在画像屋补"；不强制 |

### B03 · 1:30–3:00 · 出生在镇长家 + 第一行 `print`

| 字段 | 内容 |
| ---- | ---- |
| 触发 | PicassoLite 关闭 → fade-in 到 `mayor_house` 内场景 |
| 教学要点 | **KC-014 输入输出基础**（Ch1 / Ch2）— `print()` 函数调用、字符串字面量、英文标点 |
| UI | 9×9 木地板房间 + 桌前 mayor_py + 一台像素电脑；HUD 仅亮"对话"槽，其余 4 槽灰色 |
| 对话原文 | "（mayor_py）醒了？欢迎来 Pythontopia。先跟我做件事 — 在屏幕上打一句话。" → 弹 CodeLab 行级编辑器，预填 `print(` 半句 → "把里面填上 `\"Hello\"` 试试，标点要英文。" → 玩家按 Run → "成了！这就是你今天第一行 Python。" |
| 离场 | `submission.status='ACCEPTED'` 且 stdout 含 `Hello` → 自动关闭 CodeLab → 落 `kc_mastery_event(PY43-KC-014, RESOURCE_READ, +0.05)` |
| 兜底 | 标点写成中文 / 漏右括号 → 镇长温和补一句 "再看一下示例，标点符号要英文"（详见 §4.3） |

### B04 · 3:00–4:30 · 出门 + WASD 自由移动

| 字段 | 内容 |
| ---- | ---- |
| 触发 | B03 离场 → CodeLab 关闭、镇长走到门口 |
| 教学要点 | "屏幕上的位置 = 一对数字"（KC-016 变量预热），不要求记住 |
| UI | 屏幕中央 1.5 秒淡入 `WASD ↑→↓←` 半透明按键提示；HUD"地图"槽闪亮但暂不可点 |
| 对话原文 | "（mayor_py）门外就是 Pythontopia 镇广场。试试 `WASD`，先走到喷泉那儿。" |
| 离场 | 玩家 `tile_x/y` 进入广场触发器（半径 4 tile） → 镇长退场返回家中 |
| 兜底 | 30s 内未按任何键 → §4.1 自动镇长走过来演示 |

### B05 · 4:30–5:00 · 抵达中央广场

| 字段 | 内容 |
| ---- | ---- |
| 触发 | 进入广场触发器 |
| 教学要点 | 无（场景过场） |
| UI | 镜头从玩家拉远到广场全景 1.2 秒，回中；HUD"地图"槽点亮可用 |
| 对话原文 | 屏幕居中 toast："Pythontopia · 镇广场"（1.5 秒淡出） |
| 离场 | toast 消失 → 镜头回正 |
| 兜底 | —— |

### B06 · 5:00–8:00 · 偶遇 5 同学（6 选 5，每人 1 句）

| 字段 | 内容 |
| ---- | ---- |
| 触发 | 镜头回正 → 6 位同学 NPC 在广场围圈散立，玩家行进路线上的前 5 位主动招呼 |
| 教学要点 | 学习类型自我映射（写 `learner_profile.dimensions.peer_archetype_hint`） |
| UI | 每位 NPC 头顶 2 秒气泡，玩家走近自动播报，不需要 E 确认；左下 mini-banner 显示"今日已遇 N/5 同学" |
| 对话原文 | 见 §11.2（5 句 × 5 人） |
| 离场 | 第 5 位同学气泡播完 |
| 兜底 | 玩家绕开同学 / 反向走 → 同学主动跟随 1 tile 后说话，不强制走过去 |

### B07 · 8:00–12:00 · 微任务：图书馆借第 1 页课件

| 字段 | 内容 |
| ---- | ---- |
| 触发 | B06 结束 → `gardener`（远处）抬手指向 `campus_east` 方向 → 任务面板首次自动弹出（1.5 秒后自动收起，HUD"任务"槽点亮） |
| 教学要点 | **"Python 是什么"** — 借第 1 页课件（pageId=10707，第一章 1.1） |
| UI | 屏顶 1 行任务条 "去东边图书馆找馆长 Doc 借第 1 页"；点击展开任务面板 |
| 对话原文 | （librarian_doc）共 3 句：①"图书馆里 561 页课件随你翻。新来的吧？" ②"这是第 1 页 — Python 是一门解释执行的高级语言，由 Guido van Rossum 在 1989 年圣诞节开发。" ③"读完按 E 把书还我，下次想找哪一页都来找我。" |
| 离场 | `PageReadEvent(page_id=10707, duration_ms ≥ 30s)` → `kc_mastery_event(PY43-KC-001, RESOURCE_READ, +0.10)` + `inventory_item('evidence_slip_p10707')` 入库 |
| 兜底 | 玩家找不到图书馆 → §4.2 镇长 toast "按 M 看地图"；停留 ≥ 90s 未到 → 馆长 Doc 主动走到馆门口招手 |

### B08 · 12:00–15:00 · 回家路上遇田老接 KC 种子

| 字段 | 内容 |
| ---- | ---- |
| 触发 | 玩家离开图书馆 → 路径上 farmer_kc 在城北门 stationary spawn 等候 |
| 教学要点 | **KC-002 计算工具的演变**（"种子"= 1 个 KC 概念封装为 1 颗作物） |
| UI | 接近 farmer_kc 触发 dialog；任务面板自动新增 1 行 "plant_first_seed" |
| 对话原文 | （farmer_kc）共 3 句：①"嚯，新面孔。第一次见你 — 拿一颗 KC 种子带回去。" ②"这颗是 `KC-002 计算工具演变`，种到农场北边任一垄上，浇一次水就发芽。" ③"我在田头等你 — 别忘了浇水时做完那道入门题。" |
| 离场 | `quest('plant_first_seed').status='ACCEPTED'`，背包多出 `seed_kc_002` |
| 兜底 | 玩家漏点对话 → farmer_kc 横身阻挡通路（仅本次 onboarding），强制触发 |

### B09 · 15:00–20:00 · 农场浇第 1 颗 KC（做 1 道入门题）

| 字段 | 内容 |
| ---- | ---- |
| 触发 | 玩家进 `farm_north` 任一空田垄 → 按 F 种下 `seed_kc_002` |
| 教学要点 | **KC-014 输入输出 + KC-016 变量** — 题面：把"计算工具"四个字打印出来 |
| UI | 田垄上方浮现 CodeLab 入门题面板，4 行编辑器，预填 1 行注释 `# 把字符串"计算工具"打印到屏幕`；右下 "Run" "Submit" 双按钮 |
| 对话原文 | （farmer_kc）站在田头补一句："写好按 Submit，水自然就浇上了。" |
| 离场 | `practice_submission.verdict='ACCEPTED'` → `kc_mastery_event(PY43-KC-002, PRACTICE_PASS, +0.20)` → 作物动画 SEED→SPROUT |
| 兜底 | 3 次提交失败 → CodeLab 自动展开 4 级 hint 链第 1 级（思路），但不直接给答案 |

### B10 · 20:00–23:00 · 收获 10 Pythoco + 回镇长汇报

| 字段 | 内容 |
| ---- | ---- |
| 触发 | 作物变 SPROUT → farmer_kc 主动来收（首次 onboarding 跳过常规 mastery≥0.8 门槛） |
| 教学要点 | **反馈闭环** — 让玩家亲眼看到"学一点就有点回报"的数字变化 |
| UI | 屏幕中央数字滚动 `+10 Pythoco · +0.20 掌握「计算工具」`；任务面板 `plant_first_seed` 打勾；地图 minimap 上 mayor_house 出现金色感叹号 |
| 对话原文 | （farmer_kc）"收成！第一茬不多，往后越种越多。镇长在家等你回话。" → 玩家走回镇长家 → （mayor_py）"看到你浇了第一颗 — 这就是 Pythontopia 的玩法节奏。" |
| 离场 | 玩家进入 mayor_house 触发器 |
| 兜底 | 玩家不回镇长家 → 60s 后镇长走出家门到田边迎接（一次性脚本） |

### B11 · 23:00–26:00 · 班级 joinCode 提示

| 字段 | 内容 |
| ---- | ---- |
| 触发 | B10 镇长对话结束 → 弹"班级"小面板 |
| 教学要点 | "Pythontopia 不是单机，是教师 + 同学的小镇" |
| UI | 6 位输入框 + "我没有 joinCode" 文字按钮；后者点了直接关闭面板，写 `learner_flag.no_classroom_yet=true` |
| 对话原文 | （mayor_py）"对了，如果你是从老师那儿来的，把 6 位班级码输进来。没有也没关系，先自由探索。" |
| 离场 | 提交 joinCode 校验通过 → `classroom_member` 新行 + 公告板小红点；或点跳过 |
| 兜底 | joinCode 输错 3 次 → 提示"问一下你的老师"，**不锁流程** |

### B12 · 26:00–28:00 · HUD 全功能解锁动画

| 字段 | 内容 |
| ---- | ---- |
| 触发 | B11 结束 |
| 教学要点 | 5 槽位语义（对话 / 任务 / 地图 / 背包 / 班级），每槽 0.3 秒像素脉冲点亮 |
| UI | HudShell 5 槽位依次从灰转金 + 1.5 秒底部说明条 "对话(E) / 任务(Tab) / 地图(M) / 背包(I) / 班级(C)"；右上角 1 行小字 "随时按 ? 看全部按键" |
| 对话原文 | 无 NPC 对话；屏幕顶部 toast "HUD 已解锁全部功能" |
| 离场 | toast 淡出 |
| 兜底 | —— |

### B13 · 28:00–30:00 · "今天先到这"睡觉推日

| 字段 | 内容 |
| ---- | ---- |
| 触发 | B12 结束 → 镇长再次走出家门 |
| 教学要点 | **世界时钟节奏感** — 让玩家亲历"睡觉 = 推一天" |
| UI | 镇长指向 mayor_house 二楼床；床触发器靠近后弹 F 提示 |
| 对话原文 | （mayor_py）"差不多了 — 你今天做的事都已存档。回床上按 F 睡一觉，明早会有新任务。" |
| 离场 | 玩家按 F → 1.2 秒 fade-out + `POST /api/world/advance/sleep` → fade-in 到第 2 天 06:30 |
| 兜底 | 玩家不去睡 → 90s 后弹强制睡眠提示，可选"再玩 5 分钟"按钮（只可点 1 次） |

### B14 · 30:00 · onboarding 结算

| 字段 | 内容 |
| ---- | ---- |
| 触发 | sleep 接口 200 返回 |
| 教学要点 | 无 |
| UI | 屏顶 banner "Pythontopia 第 1 天完成 · 解锁日常任务"，3 秒淡出 |
| 对话原文 | —— |
| 离场 | 写 `world_event(type='onboarding_completed', payload={duration_sec, beats_completed, beats_skipped})` |
| 兜底 | —— |

---

## 3. 跳过策略

| 触发 | 行为 | 状态写入 |
| ---- | ---- | -------- |
| 老玩家（`user.onboarding_completed_at IS NOT NULL`）重登 | 完全不弹引导 | 无 |
| 新玩家在 B01 后点"跳过新手引导"（右上角小字按钮） | 直接跳到 B12 节点，前 11 拍全部 skip | `learner_profile.onboarding_skipped=true` |
| 新玩家在中间任意拍按 ESC ≥ 3 次 | 弹 "确认跳过？" 二次确认，确认后跳 B12 | `onboarding_progress.exit_at_beat=<id>` |
| 教师 / 助教账号首登 | 不进入学生引导，直接进 dashboard | `learner_profile.onboarding_skipped=true`, `role='TEACHER'` |

跳过策略的硬约束：

1. **跳过仍写画像**：跳过的玩家 `dimensions.peer_archetype_hint` 用画像采样的默认值（"未知"），不留空。
2. **跳过不送 mastery**：B09 的 `+0.20 PRACTICE_PASS` 必须真正提交才能写，跳过引导直接拿不到这次加成。
3. **跳过可回放**：玩家在 mayor_house 桌前可点"重看新手指引"，但只能播 cutscene 文本，无 mastery 二次奖励。

---

## 4. 失败兜底

### 4.1 5 分钟不动 → 镇长主动来教

- **触发**：连续 300s 无任何 `LearningEvent.player.move`（B04~B13 任一拍内）
- **行为**：mayor_py 从最近寻路点走到玩家身边，停下，气泡 "试试按 `WASD` 走两步看看？"；2 秒后 HUD 显示半透明按键提示再 5 秒
- **写库**：`engagement_risk_event(type='STUCK_IDLE', beat=<current>, intervention='mayor_walk_over')`
- **后续**：若再 120s 仍不动 → 弹"切换控制方式"提示（虚拟摇杆 / 鼠标点击移动二选一）

### 4.2 玩家走丢 → "按 M 看地图"

- **触发**：B07 / B08 / B10 任一拍内，玩家偏离推荐路径 ≥ 12 tile 并连续 60s 未接近目标
- **行为**：屏幕顶 toast "迷路了？按 M 打开地图" 3 秒；M 第一次按下时迷你地图自动放大 1.5 倍 1 次
- **写库**：`engagement_risk_event(type='LOST_OFFTRACK', beat=<id>, distance_tiles=<n>)`

### 4.3 玩家 print 写错 → 镇长温和提示

- **触发**：B03 / B09 内 `practice_submission.verdict ∈ {COMPILE_ERROR, RUNTIME_ERROR}` 且错误信息含 `SyntaxError|invalid character`
- **行为**：mayor_py（B03）或 farmer_kc（B09）补一句 "再看一下示例，标点符号要英文 — `\"` 不是 `"`，`(` 不是 `（`"
- **写库**：`engagement_risk_event(type='SYNTAX_PUNCT_CN', beat=<id>, traceback_excerpt=<line>)`
- **后续**：连续 3 次同类错误 → CodeLab 自动把中文标点替换为英文（一次性、显式提示）

### 4.4 浏览器异常 / 网络中断

- **触发**：bootstrap 200 后任一接口 5xx 连续 ≥ 2 次
- **行为**：屏顶红色 banner "连接异常，已暂停引导"，原 beat 状态写 `onboarding_progress.paused_at`；恢复后从断点续播
- **写库**：`engagement_risk_event(type='NETWORK_DROP', last_beat=<id>)`

> 4 类兜底全部走 `engagement_risk_event` 表，与 `player-progression.md` §7 复用同一张表，避免新增表膨胀。

---

## 5. 画像影响

| 维度 | 写入时机 | 来源 |
| ---- | -------- | ---- |
| `major` 主观 | B02 题 1 | PicassoLite 提交 |
| `python_self_rated_level` 主观 | B02 题 2 | PicassoLite 提交 |
| `learning_goal.target` 主观 | B02 题 3 | PicassoLite 提交 |
| `learning_preference.style_self_report` 主观 | B02 题 4 | PicassoLite 提交 |
| `error_pattern.self_recall` 主观 | B02 题 5 | PicassoLite 提交（首条 self_recall 字符串） |
| `learning_preference.early_bird/night_owl` 被动 | B01~B14 全程 | 登录时段直方图（按 `world_clock-calendar` §9） |
| `cognitive_style.pace_self` 被动 | B04 行走停顿统计 | `LearningEvent.player.move` 间隔均值 |
| `practice_capability.first_pass_rate` 被动 | B09 题首提通过率 | `practice_submission.attempt_count` |
| `emotion_persistence.recent_mood` 被动 | B07 / B08 是否走丢、B03 / B09 是否报错 | `engagement_risk_event` 聚合 |
| `peer_archetype_hint` 被动 | B06 玩家走近顺序 | NPC dialog 触发序列写 5 元数组 |

> 5 题主观维度仅占画像的 4/8（最后 1 题写入 `error_pattern.self_recall`，是 8 维之一的"易错模式"种子）。其余 4 维（认知风格、实践能力、情绪与坚持度、知识基础）由 `PassiveProfileService` 在 onboarding 30 分钟里**首批被动信号**采样，结束后 `dimensions.last_updated_at` 8 个维度全部非空。

---

## 6. 教师可见

教师 dashboard `/teacher/onboarding-today` 新增 1 张卡片，数据每 5 分钟物化（沿用 `classroom_kc_snapshot` 物化器）。

| 字段 | 含义 | 来源 |
| ---- | ---- | ---- |
| `new_students_today` | 今日新加入学生数 | `users.created_at >= today_start` |
| `onboarding_completion_rate` | 30 分钟内完成 B14 的比例 | `world_event(type='onboarding_completed')` / `users.created_at` |
| `onboarding_median_minutes` | 完成耗时中位数 | `payload.duration_sec` |
| `top_stuck_beat` | 最常卡住的 beat id | `onboarding_progress.exit_at_beat` 众数 |
| `syntax_punct_cn_count` | 中文标点错误总数 | `engagement_risk_event(type='SYNTAX_PUNCT_CN')` 计数 |

教师视角只看**聚合**，不能下钻到单个学生的具体 beat 时间戳，避免家长式监控（沿用 `world-clock-calendar` §11 伦理边界约定）。

---

## 7. A/B 实验位

R-12 Beta 上线 2 个最小实验，按 `hash(user_id) mod 100` 分流，写 `ab_assignment(user_id, experiment_key, variant)`。

### 7.1 镇长第一句对话 3 版本（B03）

| variant | 文案 | 假设 |
| ------- | ---- | ---- |
| `A` 默认 | "醒了？欢迎来 Pythontopia。先跟我做件事 — 在屏幕上打一句话。" | 中性、行动导向 |
| `B` 共情 | "新同学到了。我知道第一行代码总让人紧张 — 我陪你写。" | 高共情，假设可降低焦虑型学生流失 |
| `C` 挑战 | "听说你能搞定 Python？来，写一句话证明给我看。" | 挑战型，假设可激发挑战型学生 |

观测指标：B03 完成率、首次报错次数、7 日留存差异（双向 t 检验，n ≥ 200 / 组）。

### 7.2 HUD 解锁节奏（B12）

| variant | 行为 | 假设 |
| ------- | ---- | ---- |
| `gradual` 默认 | 5 槽位 B03~B12 依次点亮（本文 §2 设定） | 渐进降低认知负担 |
| `all_at_once` | 5 槽位在 B01 末尾一次性全亮 | 看是否反而提高自由探索意愿、提升 D 型探索路径触发率 |

观测指标：7 日留存、HUD 各槽位平均点击次数、玩家「按 ? 看全部按键」面板访问率。

> 任何新增 A/B 必须先在 `docs/ab/` 立项审核（含统计功效），禁止策划私自加 variant。

---

## 8. 后端字段（新增）

R-12 W4 通过 1 张新表 + 1 个新事件 type 落地，沿用既有 `world_event` 总线。

```sql
CREATE TABLE onboarding_progress (
  user_id        BIGINT       PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  current_beat   VARCHAR(8)   NOT NULL DEFAULT 'B01',
  started_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  completed_at   TIMESTAMPTZ  NULL,
  exit_at_beat   VARCHAR(8)   NULL,
  paused_at      TIMESTAMPTZ  NULL,
  duration_sec   INT          NULL,
  beats_skipped  TEXT[]       NOT NULL DEFAULT '{}',
  variant        JSONB        NOT NULL DEFAULT '{}'::jsonb,
  CONSTRAINT onboarding_progress_beat_valid CHECK (current_beat ~ '^B(0[1-9]|1[0-4])$')
);
CREATE INDEX idx_onboarding_progress_completed_at ON onboarding_progress(completed_at);
```

`world_event` 新增 1 个 `event_type='onboarding_completed'`，payload 形如：

```json
{
  "user_id": 1024,
  "duration_sec": 1738,
  "beats_completed": ["B01","B02","B03","B04","B05","B06","B07","B08","B09","B10","B11","B12","B13","B14"],
  "beats_skipped": [],
  "joined_classroom_id": null,
  "interventions": [{"type":"STUCK_IDLE","beat":"B04"}]
}
```

> 不新增 `onboarding_event` 之类的散表；所有节拍级追踪靠 `LearningEvent.player.move` + `engagement_risk_event`，聚合在 `onboarding_progress` 单行即可。

---

## 9. 跨设备（移动 / 平板）

| 项 | Web 桌面 | Android（R-12 Beta） |
| -- | -------- | -------------------- |
| 输入提示 | "按 WASD 走、E 交互、Tab 公告板" | "左下虚拟摇杆 + 右下 E 按钮 + 顶部菜单铃铛" |
| NPC 对话文案 | 完整版 | **缩短 30%**，每句保留前两个分句；超长句换行符更激进 |
| B04 自由移动时长 | 90s | 120s（摇杆首次手感校准） |
| B12 HUD 槽位 | 5 槽位横排 | 5 槽位竖排，顶部抽屉式 |
| B13 sleep 入口 | F 键 | 床面板 1 个大圆按钮 |
| 双端 onboarding 共享 | 是 | `onboarding_progress` 同表；任一端完成 → 另一端不再触发 |

> 文案缩短示例：B03 "醒了？欢迎来 Pythontopia。先跟我做件事 — 在屏幕上打一句话。" → 移动端 "醒了？欢迎。来 — 打一句话。"
> 移动端文案缩短由 `frontend/src/i18n/onboarding/zh-CN-mobile.json` 单独维护，禁止运行时正则截断。

---

## 10. 验收

### 10.1 自动化（E2E）

`frontend/tests/e2e/onboarding.spec.ts`（Playwright + 10x 时间快进）：

```ts
test('30min onboarding happy path', async ({ page }) => {
  await loginAsNewUser(page);
  await fillPicassoLite(page, defaultProfile5);
  await advanceClock(page, 10);
  await assertBeat(page, 'B03'); await typePrintHello(page);
  await advanceClock(page, 10);
  await walkTo(page, 'town_square'); await assertBeat(page, 'B05');
  // ... B06–B13 步骤略
  await assertEvent(page, 'onboarding_completed');
  expect((await fetchOnboardingProgress(page)).duration_sec).toBeLessThan(1800);
});
```

| 用例 | 断言 |
| ---- | ---- |
| Happy path | 30 真实分钟（10x = 3 真实分钟）内完成 14 拍 |
| Skip path | 跳过按钮 → `beats_skipped.length >= 11` 且 `onboarding_completed` 仍触发 |
| Stuck idle | mock 5 分钟无 input → mayor_py 走过来事件 + `engagement_risk_event` 1 条 |
| Punct CN | 提交 `print（"Hello"）` → 镇长补句 + 兜底替换 |
| Mobile viewport | iPhone 12 视口 → 摇杆 + 短文案断言 |

### 10.2 人工

| 检查 | 标准 |
| ---- | ---- |
| 新评测员上手 | 不看文档完成 30 分钟，无主动求助 |
| 教师视角看板 | 同日新增学生 ≥ 5 人时，`onboarding_completion_rate` 卡片显示中位 ≤ 32 分钟 |
| 双端对照 | Web 与 Android 同账号交叉登录，`onboarding_progress` 仅 1 行 |

### 10.3 指标基线（Beta 上线后 14 天复盘）

| 指标 | 目标 | 上限阈值 |
| ---- | ---- | -------- |
| 30 分钟完成率 | ≥ 80% | 完成耗时中位 ≤ 32 分钟 |
| 跳过率 | ≤ 15% | > 20% 触发文案 / 节奏 review |
| 7 日留存（完成组） | ≥ 70% | 与 `player-progression.md` §11 同源 |
| `SYNTAX_PUNCT_CN` 触发 / 玩家 | ≤ 0.6 次 | > 1.0 次 → CodeLab 默认开"自动英文标点" |

---

## 11. 附录：NPC 对话原文

> 所有原文同步回写 `data/npcs/<key>/dialog.json` 的 `onboarding_*` 节点；本附录仅作策划稿，**source of truth 仍是 JSON**。

### 11.1 镇长 Py（mayor_py） · 5 句

| 编号 | 节拍 | 节点 key | 文案 |
| ---- | ---- | -------- | ---- |
| M1 | B03 开场 | `onboarding_wake` | "醒了？欢迎来 Pythontopia。先跟我做件事 — 在屏幕上打一句话。" |
| M2 | B03 引导 | `onboarding_print_hint` | "把里面填上 `\"Hello\"` 试试，标点要英文。" |
| M3 | B04 出门 | `onboarding_door` | "门外就是 Pythontopia 镇广场。试试 `WASD`，先走到喷泉那儿。" |
| M4 | B11 班级 | `onboarding_classroom_hint` | "对了，如果你是从老师那儿来的，把 6 位班级码输进来。没有也没关系，先自由探索。" |
| M5 | B13 收尾 | `onboarding_sleep_hint` | "差不多了 — 你今天做的事都已存档。回床上按 F 睡一觉，明早会有新任务。" |

### 11.2 5 位同学（同学 6 选 5） · 每人 1 句

| 编号 | NPC | 节点 key | 文案（招呼一句） |
| ---- | --- | -------- | ---------------- |
| S1 | `student_diligent`（勤勉小柒） | `onboarding_meet` | "嗨，新来的？我每天 7 点准时来农场浇水，要不要一起？" |
| S2 | `student_procrast`（拖延小迟） | `onboarding_meet` | "啊…昨晚熬到三点。别学我，今天能写一行就一行。" |
| S3 | `student_anxious`（焦虑小安） | `onboarding_meet` | "我刚才那道题写了第六遍才过 — 你别紧张，错也没关系。" |
| S4 | `student_top`（拔尖小尖） | `onboarding_meet` | "39 题做完了，准备去港口看看外校的题。回头你有不懂的来找我。" |
| S5 | `student_hobby`（兴趣小好） | `onboarding_meet` | "我用 Python 做了个像素小游戏，等你学会 for 循环我教你画地图。" |

> 第 6 位 `student_late_starter` 在玩家正向行进路线上**不主动招呼**，留在 town_hub 给"路径 C 社交型"玩家在 Day 2 之后自然遇见，避免 onboarding 信息过载。

### 11.3 馆长 Doc（librarian_doc） · 3 句

| 编号 | 节拍 | 节点 key | 文案 |
| ---- | ---- | -------- | ---- |
| L1 | B07 进门 | `onboarding_intro` | "图书馆里 561 页课件随你翻。新来的吧？" |
| L2 | B07 借书 | `onboarding_first_page` | "这是第 1 页 — Python 是一门解释执行的高级语言，由 Guido van Rossum 在 1989 年圣诞节开发。" |
| L3 | B07 离馆 | `onboarding_borrow_done` | "读完按 E 把书还我，下次想找哪一页都来找我。" |

### 11.4 田老（farmer_kc） · 3 句

| 编号 | 节拍 | 节点 key | 文案 |
| ---- | ---- | -------- | ---- |
| F1 | B08 接种子 | `onboarding_first_seed` | "嚯，新面孔。第一次见你 — 拿一颗 KC 种子带回去。" |
| F2 | B08 解释 | `onboarding_seed_explain` | "这颗是 `KC-002 计算工具演变`，种到农场北边任一垄上，浇一次水就发芽。" |
| F3 | B09 田头 | `onboarding_water_remind` | "写好按 Submit，水自然就浇上了。别忘了浇水时做完那道入门题。" |

---

## 12. 风险与开放问题

1. **B02 5 题速答的代表性**：5 题写 4 主观维度 + 1 易错自识，留 4 维全靠被动。若新生用户行为信号在前 30 分钟过于稀疏（如 B04 几乎不动），`peer_archetype_hint` 会偏 D 型。R-13 计划在 Day 3 触发"第二次画像补问 8 题"补救。
2. **A/B 7.1 共情版风险**：若文案被误解为"我以为你不行" → 反向流失。Beta 上线第 1 周日报需人工抽查 50 个低留存 user_id 的退出 beat，发现集中在 B03 → C 版本立刻下线。
3. **跨设备 onboarding 一致性**：Android 端 B04 改 120s，会导致 `duration_sec` 中位数 Web vs Android 差 ≥ 30s。教师 dashboard 需按设备分桶展示，避免误判。
4. **B07 馆长 Doc 主动招手是否打断手感**：90s 阈值偏宽，可能让真正想自己探索的 D 型玩家被动接受。建议 R-13 把阈值与画像维度 `field_dependent` 挂钩（低依赖玩家阈值放宽到 180s）。
5. **B11 班级 joinCode 跳过率**：自由用户（无班级码）会 100% 跳过这一拍。`learner_flag.no_classroom_yet` 必须在 Day 7 触发一次"还要不要加入班级"的二次提醒，否则这批用户永远不进班级数据。
6. **B13 强制睡觉是否违反"不剥夺用户控制"**：90s 后弹"再玩 5 分钟"是唯一兜底；若 Beta 期发现玩家点了"再玩 5 分钟"后流失率反而升高，应改为"今天先到这"banner + 不弹模态。

---

-- 子代理生成于 2026-05-24（UTC+8）
