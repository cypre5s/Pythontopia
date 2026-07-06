---
title: Pythontopia BGM 切换 Cue List（星露谷风但不是星露谷）
version: v1.0
status: Draft
owner: 音乐设计师 + 前端工程师
last_updated: 2026-05-24
audience: 前端工程师 / Phaser 场景作者 / 音乐策划 / 美术外包 / 教师视角运营
scope: 全端 Web 游戏画布 BGM 与节奏 SFX、教师班级管控、可访问性
依赖文档:
  - /home/cypress/Pythontopia/docs/stardew/00-visual-system-overview.md
  - /home/cypress/Pythontopia/docs/gameplay/world-clock-calendar.md
  - /home/cypress/Pythontopia/docs/gameplay/r12-deepen/05-festival-events.md
  - /home/cypress/Pythontopia/frontend/src/composables/useAudio.ts
  - /home/cypress/Pythontopia/frontend/src/game/core/WorldClock.ts
  - /home/cypress/Pythontopia/LICENSE-ART.md
---

# 0. 文档定位

本文是 Pythontopia 全端 BGM 与节奏类 SFX 的**音乐宪法**。所有 Phaser 场景、HUD 教学面板、节日活动、对话系统、教师班级管控涉及的「响什么 / 不响什么 / 怎么切」必须在本文中找到对应 cue。

音乐目标用一句话概括：

> **BGM 与玩法状态实时同步，不是「全程一首曲子从登录到退出循环到底」**。

为此本文严格执行 `AGENTS.md` 的硬约束与三条音乐设计准则：

| 准则 | 含义 |
| --- | --- |
| **状态绑定** | 每首 BGM cue 必须绑定到 `WorldClock` 的可观测状态（map / season / weather / timeOfDay / festival / dialog / battle），禁止自由换歌 |
| **license 干净** | 仅采用 **CC0 / CC-BY 3.0/4.0 / OpenGameArt** 来源，**严禁 CC-BY-SA 音乐**（SA 传染对教育游戏分发不友好，见 `LICENSE-ART.md` §16 立场声明） |
| **教育优先** | 默认进入静音；课堂教师可一键强制全班静音；所有依赖音效的事件必须有视觉等价反馈 |

**为什么不"一首曲子循环到底"**：4 季 × 6 时段 × 4 天气 × 5 地图 = **480 种状态组合**，全程一曲会让玩家 15 分钟出戏；解锁 KC / 进矿洞 / 节日庆典 / 对话学习都需要听觉反馈强化教学锚点；单课时 25–45 分钟单曲循环 ≥ 8 次必引发疲劳。

**也不能 480 首一一映射**：单曲 1.5MB × 480 = 720MB 不可接受；CC0/CC-BY 优质循环曲池约 200 首；同地图频繁换歌反而"音乐错乱"。

本文给出的答案：**20 主曲 + 10 特殊 cue + EQ 滤镜微调** = **30 个 cue** 覆盖 480 种组合。

---

# 1. Cue 收敛策略

## 1.1 主轴 = 地图 × 季节（5 × 4 = 20 cue）

地图换 = 必然换歌；季节换 = 必然换歌。其余维度只做"音轨内调节"，不切歌。

| 维度 | 是否换歌 | 切换手段 |
| --- | --- | --- |
| **地图（5）** | ✅ 必换 | crossfade 1.5s |
| **季节（4）** | ✅ 必换 | crossfade 2.0s（季节切换是大事件） |
| **时段（6）** | ❌ 不换 | 低通滤波器 800ms 缓变（EQ 200Hz–8kHz 衰减斜率调节） |
| **天气（4）** | ❌ 不换 | 音量降幅 20% × 8s（雨/雪压低）+ 叠加 SFX 雨声/风声 |
| **节日（11）** | ✅ 临时覆盖 | 节日期间强制切到 4 个节日 cue 之一，节日结束自动恢复主轴 |
| **对话** | ❌ 不换 | 音乐 -30dB 200ms ramp（duck），对话结束 +30dB 复原 |
| **战斗（boss）** | ✅ 临时覆盖 | crossfade 0.5s（boss 战必须立即换） |

## 1.2 480 → 30 收敛数学

| 组合维度 | 数量 | 收敛后 cue 数 | 收敛手段 |
| --- | --- | --- | --- |
| 4 季节 × 5 地图 | 20 | **20**（主轴） | 1:1 |
| 6 时段 | 6 | **0**（EQ 调节，不切歌） | EQ 滤镜 |
| 4 天气 | 4 | **0**（音量降幅 + SFX 叠加） | mix |
| 11 节日 | 11 | **4**（合并为 ceremony/market/competition/reflective 四类） | 类型合并 |
| boss 战 | 1 | **1** | 独立 cue |
| 对话默认低音垫 | 1 | **1** | 独立 cue |
| 教学新发现 stinger | 1 | **1** | 独立 cue |
| 任务完成 jingle | 1 | **1** | 独立 cue |
| 任务失败 sting | 1 | **1** | 独立 cue |
| sleep 强退场 fade | 1 | **1** | 独立 cue |
| **合计** | 480+ | **30** | 20 + 10 |

> **为什么这样切**：地图换 = 玩家空间认知重置，必须用换歌强化"我到新地方了"；季节换 = 学期断面，玩家会被横幅强提示，听觉同步配合视觉；时段/天气 = 同场景渐变，EQ 比换歌更自然；节日 = 临时插入仪式感，结束后立即回归日常曲。

---

# 2. 20 主曲清单表（地图 × 季节）

命名约定：`bgm_<map>_<season>`，map ∈ {town_hub, farm_north, mine_south, campus_east, port_west}，season ∈ {spring, summer, autumn, winter}。

文件路径：`frontend/public/assets/audio/bgm/<map>/<season>.ogg`。

> **资产可用性图例**：✅ 已下载在 `assets/inbox/bgm[_extra]/`；🟡 待采购但作者已确定 license；🔵 待采购候选作者（备选 ≥ 2 个，开发期挑选）。

| # | cue_key | 地图 | 季节 | BPM | 风格 | 文件名 | 时长 | License | 作者 | 状态 |
|---:|---|---|---|---:|---|---|---:|---|---|:---:|
| 01 | `bgm_town_hub_spring` | 中央广场 | 春 | 120 | 民谣 + 木管 | `town_hub/spring.ogg` | 1:05 | CC0 | RandomMind（OGA `medieval-market-day`） | ✅ |
| 02 | `bgm_town_hub_summer` | 中央广场 | 夏 | 128 | 欢快小调 | `town_hub/summer.ogg` | 1:31 | CC-BY 3.0 | Hitctrl（OGA `happy-pixie-town`） | ✅ |
| 03 | `bgm_town_hub_autumn` | 中央广场 | 秋 | 100 | 温暖弦乐 + 钢琴 | `town_hub/autumn.ogg` | 5:38 | CC-BY 3.0 | TAD（OGA `settlement-of-the-frontier`） | ✅ |
| 04 | `bgm_town_hub_winter` | 中央广场 | 冬 | 80 | 壁炉钢琴 | `town_hub/winter.ogg` | 1:20 | CC0 / CC-BY 3.0 | MouthlessGames（OGA `warm-fireplace`） | ✅ |
| 05 | `bgm_farm_north_spring` | 北农场 | 春 | 110 | 田园风 chiptune | `farm_north/spring.ogg` | 2:00 ± | CC-BY 4.0 | Eric Skiff `Underclocked`（http://ericskiff.com/music/） | 🟡 |
| 06 | `bgm_farm_north_summer` | 北农场 | 夏 | 120 | 阳光乡村 | `farm_north/summer.ogg` | 2:30 ± | CC-BY 4.0 | Kevin MacLeod `Pixelland`（incompetech.com） | 🟡 |
| 07 | `bgm_farm_north_autumn` | 北农场 | 秋 | 118 | cozy puzzle | `farm_north/autumn.ogg` | 2:10 | CC0 | MintoDog（OGA `cozy-puzzle-in-game-1`） | ✅ |
| 08 | `bgm_farm_north_winter` | 北农场 | 冬 | 95 | 静谧雪原 | `farm_north/winter.ogg` | 2:00 ± | CC-BY 4.0 | Eric Skiff `We're All Under the Stars` | 🟡 |
| 09 | `bgm_mine_south_spring` | 南矿洞 | 春 | 90 | 神秘环境 | `mine_south/spring.ogg` | 1:26 | CC-BY 3.0 | cynicmusic（OGA `crystal-cave-mysterious-ambience`） | ✅ |
| 10 | `bgm_mine_south_summer` | 南矿洞 | 夏 | 140 | 战斗鼓点 | `mine_south/summer.ogg` | 1:19 | CC0 | RandomMind（OGA `medieval-battle`） | ✅ |
| 11 | `bgm_mine_south_autumn` | 南矿洞 | 秋 | 135 | 短促战斗 | `mine_south/autumn.ogg` | 1:23 | CC-BY 3.0 | Spring Spring（OGA `short-and-simple-battle-theme`） | ✅ |
| 12 | `bgm_mine_south_winter` | 南矿洞 | 冬 | 95 | 冷峻洞穴 | `mine_south/winter.ogg` | 2:00 ± | CC-BY 4.0 | Eric Skiff `All of Us` | 🟡 |
| 13 | `bgm_campus_east_spring` | 东校园 | 春 | 100 | 学习专注弦乐 | `campus_east/spring.ogg` | 5:38 | CC-BY 3.0 | TAD（OGA `settlement-of-the-frontier` 节选 loop） | ✅ |
| 14 | `bgm_campus_east_summer` | 东校园 | 夏 | 110 | 喜悦庆祝 | `campus_east/summer.ogg` | 1:43 | CC0 | RandomMind（OGA `medieval-rejoicing`） | ✅ |
| 15 | `bgm_campus_east_autumn` | 东校园 | 秋 | 105 | 自习室 chiptune | `campus_east/autumn.ogg` | 2:00 ± | CC-BY 4.0 | Eric Skiff `Underclocked` 变体 | 🟡 |
| 16 | `bgm_campus_east_winter` | 东校园 | 冬 | 115 | 期末紧凑 | `campus_east/winter.ogg` | 2:00 ± | CC-BY 4.0 | Kevin MacLeod `Sneaky Snitch` | 🟡 |
| 17 | `bgm_port_west_spring` | 西港口 | 春 | 95 | 海港夜曲 | `port_west/spring.ogg` | 2:06 | CC-BY 3.0 | Spring Spring（OGA `late-evening-in-newbie-town`） | ✅ |
| 18 | `bgm_port_west_summer` | 西港口 | 夏 | 125 | 商队欢腾 | `port_west/summer.ogg` | 2:30 ± | CC-BY 4.0 | Kevin MacLeod `Caprese` | 🟡 |
| 19 | `bgm_port_west_autumn` | 西港口 | 秋 | 100 | 凉风渔港 | `port_west/autumn.ogg` | 1:45 | CC0 | RandomMind（OGA `medieval-the-old-tower-inn`） | ✅ |
| 20 | `bgm_port_west_winter` | 西港口 | 冬 | 85 | 冬港低吟 | `port_west/winter.ogg` | 2:00 ± | CC0 | OpenGameArt LPC Music Bundle（待筛选） | 🔵 |

**汇总**：12 首 ✅ 已下载（占 60%），7 首 🟡 待采购（作者已锁定 Eric Skiff / Kevin MacLeod，license CC-BY 4.0），1 首 🔵 LPC Bundle 待筛选。**所有 20 首均承诺 CC0 或 CC-BY 4.0/3.0，绝无 CC-BY-SA**。

---

# 3. 10 特殊 Cue 清单（覆盖、jingle、stinger）

命名约定：`bgm_<scenario>` 或 `sfx_jingle_<event>`，节日合并为 4 类。

| # | cue_key | 场景 | BPM | 风格 | 文件名 | 时长 | License | 作者 | 状态 |
|---:|---|---|---:|---|---|---:|---|---|:---:|
| 21 | `bgm_bug_mine_boss` | Bug 矿洞 boss 战 | 160 | 紧张战斗 | `boss/bug_boss.ogg` | 1:30 ± | CC-BY 4.0 | Eric Skiff `Chibi Ninja` | 🟡 |
| 22 | `bgm_festival_ceremony` | 仪式型节日（spring_opening / finals_festival / new_year_eve） | 110 | 仪式管弦 | `festival/ceremony.ogg` | 2:00 ± | CC-BY 4.0 | Kevin MacLeod `Wallpaper` | 🟡 |
| 23 | `bgm_festival_market` | 集市型节日（port_fair / spring_finale_race） | 130 | 集市鼓乐 | `festival/market.ogg` | 1:05 | CC0 | RandomMind（OGA `medieval-market-day` 加速变体） | ✅ |
| 24 | `bgm_festival_competition` | 竞赛型节日（spring_quiz / midterm_festival / bug_carnival / final_warmup） | 140 | 比赛紧凑 | `festival/competition.ogg` | 1:43 | CC0 | RandomMind（OGA `medieval-rejoicing` 加速变体） | ✅ |
| 25 | `bgm_festival_reflective` | 静谧型节日（silent_night / winter_showcase） | 75 | 冥想钢琴 | `festival/reflective.ogg` | 1:20 | CC0 / CC-BY 3.0 | MouthlessGames（OGA `warm-fireplace` 变体） | ✅ |
| 26 | `bgm_dialog_pad` | 对话默认弱化背景（NPC 长台词时压低主 BGM 自动叠加） | 60 | 低音 pad | `dialog/pad.ogg` | 0:30 loop | CC0 | OpenGameArt LPC Music Bundle | 🔵 |
| 27 | `sfx_jingle_kc_unlock` | 解锁新 KC stinger（不打断 BGM，叠加播放） | 140 | 8-bit 上升音阶 | `jingle/kc_unlock.ogg` | 0:06 | CC0 | Spring Spring（OGA `sega-mega-drivegenesis-victory-jingle`） | ✅ |
| 28 | `sfx_jingle_quest_complete` | 任务完成（叠加播放，不切 BGM） | 120 | 胜利号角 | `jingle/quest_complete.ogg` | 0:32 | CC0 | RandomMind（OGA `medieval-victory-theme`） | ✅ |
| 29 | `sfx_jingle_quest_fail` | 任务失败 / Bug 触发（叠加播放，不切 BGM） | 90 | 低沉小调 | `jingle/quest_fail.ogg` | 0:08 | CC0 | RandomMind（OGA `medieval-defeat-theme` 节选 8 秒） | ✅ |
| 30 | `bgm_sleep_fade` | sleep 强退场（推日入口，全屏黑屏过渡 5 秒） | — | 钢琴 outro fade | `system/sleep_fade.ogg` | 0:05 | CC0 | 自制提案（钢琴单音 fade out） | 🟡 |

**汇总**：7 首 ✅ / 2 首 🟡 / 1 首 🔵。**节日 11 个 → 合并 4 个 cue** 的依据：开学 / 期末 / 新年都是仪式型；港口集市 / 春末赛跑都是集市鼓乐；春末入门考 / 期中 / Bug 节 / 期末预热都是竞赛紧凑；雪夜 / 答辩展都是冥想反思。同一类型节日共享 cue，加上节日 HUD 横幅、装饰 sprite、特殊 NPC 对话，足以区分节日身份，无需 11 首独立曲。

> **特殊 cue 不计入 20 主曲循环**：玩家进入节日 → 当前主曲 crossfade 0.8s 到节日 cue → 节日 `world_event(event_type='FESTIVAL_END')` 触发 → 自动 crossfade 1.5s 回到主曲。对话 / 战斗同理，但是临时覆盖更短。

---

# 4. 切换规则（WorldClock listener 驱动）

## 4.1 监听三层事件

订阅入口：`frontend/src/composables/useAudio.ts` 新增 `bindWorldClock(clock, mapKey)` 方法，在 `WorldScene.create()` 中调用一次。事件分发：`day-changed` 忽略（不直接驱动音频）；`season-changed` → `playBgm(resolveCue(map, season), { crossfadeMs: 2000 })`；`time-of-day-changed` → `applyEqFilter(profile, { durationMs: 800 })`；`weather-changed` → `applyWeatherDuck(weather)`。

> `applyEqFilter` / `applyWeatherDuck` 是本文提案的 `useAudio.ts` 扩展 API（v1.1 实施，§12 附录），新增方法不破坏现有 `playBgm` 行为。

## 4.2 切换矩阵

| 触发 | 动作 | 时长 | 实现要点 |
| --- | --- | --- | --- |
| **场景切换**（玩家走到地图边缘） | crossfade 到新 cue | **1.5s** | `playBgm(newCue, { crossfadeMs: 1500 })`（现有 API 已支持） |
| **季节切换**（dayIndex 越过 28/56/84/112） | crossfade 到新 cue | **2.0s** | `playBgm(newCue, { crossfadeMs: 2000 })`，配合 HUD 横幅 |
| **同场景内时段变化**（DAWN→MORNING 等） | 低通滤波器 EQ 缓变 | **800ms** | BiquadFilter `lowpass` cutoff 8kHz↔3kHz 缓变；不切歌 |
| **同场景内天气变化**（SUNNY→RAINY 等） | 主 BGM 音量 -20% + 叠加 SFX 雨/风 | **8s** | `gainNode.gain.linearRampToValueAtTime(0.48, t+8)`（默认 60% → 48%） |
| **对话开始** | 音乐 -30dB 200ms ramp（duck） | **200ms** | 对话开始 → BGM 增益 ×0.0316；对话结束 → ramp 回 1.0 |
| **boss 战进入** | crossfade 0.5s 到 `bgm_bug_mine_boss` | **0.5s** | 立即换歌，boss 战需要强紧迫感 |
| **boss 战结束** | crossfade 1.5s 回到主轴 | **1.5s** | 恢复 `bgm_mine_south_<season>` |
| **节日进入** | crossfade 0.8s 到节日 cue | **0.8s** | 节日 HUD 横幅同步弹出 |
| **节日结束** | crossfade 1.5s 回到主轴 | **1.5s** | 自动恢复 |
| **sleep 触发** | 4 秒淡出 + 5 秒 sleep_fade + 黑屏 + 醒来 1.5s 淡入新日 | **10.5s 总** | `WorldAdvanceService.sleep()` → 4s fadeOut → 黑屏 5s 播 `bgm_sleep_fade` → 1.5s fadeIn 新一日主曲 |
| **stinger 叠加**（KC 解锁 / 任务完成 / 任务失败） | 不切 BGM，直接 `playSfx` 叠加 | 6/32/8s | 主 BGM 持续，stinger 短曲叠加播放，自然结束 |

## 4.3 EQ 时段 profile 表

| 时段 | lowpass cutoff | highpass cutoff | 主观感受 |
| --- | ---: | ---: | --- |
| DAWN | 5 kHz | 80 Hz | 朦胧、温润 |
| MORNING | 12 kHz | 60 Hz | 明亮、开阔（基准） |
| NOON | 14 kHz | 60 Hz | 极清晰 |
| AFTERNOON | 10 kHz | 70 Hz | 略柔 |
| EVENING | 6 kHz | 100 Hz | 暖、收敛 |
| NIGHT | 4 kHz | 120 Hz | 安静、低沉 |

> **为什么用 EQ 而不是换歌**：玩家在同一地图过完整一天（24 真实分钟 / 24 游戏小时），换 6 次歌会产生「音乐拼贴感」；EQ 缓变 800ms 让玩家感觉「同一首曲子在自然变化」，类似真实昼夜的听觉感受。

---

# 5. 音量与 Mix

## 5.1 默认音量层级

| 频道 | 默认值 | 用户可调范围 | 说明 |
| --- | ---: | --- | --- |
| **BGM** | 60% | 0–100% 步进 5% | 背景音乐主频道 |
| **SFX** | 80% | 0–100% 步进 5% | 战斗 / 行走 / 工具音 / 环境音 |
| **UI** | 50% | 0–100% 步进 5% | 按钮 / hover / tooltip 提示音 |

> **3 个频道独立可调**：玩家可在 `settings/audio` 面板三条独立滑块。Pinia store `useSettingsStore().audio = { bgm: 0.6, sfx: 0.8, ui: 0.5, muted: true }`。

## 5.2 默认进入静音

**Pythontopia 教育版默认 mute = true**，理由：

1. **课堂场景**：学生在教室上课，突然响起 BGM 会干扰其他同学。
2. **autoplay 政策**：浏览器要求音频必须由用户交互触发，强制默认静音 + 首次按键弹「是否开启音乐」对话框，符合 W3C 推荐。
3. **可访问性**：听障 / 静音环境用户默认无障碍。

首次按键 / 点击弹「是否开启背景音乐」对话框（标题 + 「课堂默认静音」说明 + 「开启音乐」/「保持静音」二选一 + 「不再询问」勾选），结果写入 `localStorage.pyt_audio_consent = 'enabled' | 'muted'`，30 天有效。

## 5.3 教师 dashboard 强制静音

教师在班级管理面板可发布 **「班级强制静音」指令**（如开始课堂讲解时）：

| 教师动作 | 后端 API | 学生 client 行为 |
| --- | --- | --- |
| 教师勾选「强制全班静音」并保存 | `POST /api/classroom/{id}/audio-policy { forceMute: true }` | 学生 client SSE 推送 → `audio.setMuted(true)` 立即生效，且禁用 settings 中 BGM/SFX/UI 三个滑块（toast 提示「教师已锁定音频」） |
| 教师取消 | `POST /api/classroom/{id}/audio-policy { forceMute: false }` | 学生 client SSE 推送 → 解锁滑块，BGM 不自动恢复（等学生主动按 settings 打开） |

> 教师强制静音是 R-13 班级视角拓展的前置能力；R-12 W6 实现 student 端被动接收，R-13 W2 实现教师 dashboard 主动发布。

---

# 6. 音频格式与体积约束

| 项 | 值 | 理由 |
| --- | --- | --- |
| **主选格式** | `.ogg` (Vorbis 96 kbps) | 现代浏览器原生支持率 ≥ 95%，CC0/CC-BY 库主流格式 |
| **fallback 格式** | `.mp3` (128 kbps) | iOS Safari 旧版本兼容、教师 PC 投影 IE/Edge 旧版 |
| **比特率上限** | 96 kbps ogg / 128 kbps mp3 | BGM 是背景音，96 kbps 足够；体积优先 |
| **单曲文件大小** | ≤ **1.5 MB** | 30 cue × 1.5 MB ≤ 45 MB，含 fallback 约 90 MB |
| **总 BGM 包** | ≤ **40 MB**（仅 .ogg）/ ≤ **80 MB**（含 mp3 fallback） | 首屏不加载，按地图懒加载控制 |
| **循环长度** | 60–180 秒推荐 | 短于 60s 听觉疲劳，长于 180s 体积超标 |
| **采样率** | 44.1 kHz | 与 OGA 原素材一致，避免重采样失真 |
| **声道** | 立体声 | 教学游戏不强制单声道；耳机用户体验优先 |

## Fallback 检测

```typescript
const supportsOgg = (() => {
  const audio = document.createElement('audio');
  return audio.canPlayType('audio/ogg; codecs="vorbis"') !== '';
})();
const suffix = supportsOgg ? '.ogg' : '.mp3';
```

> Phaser 的 `loader.audio(key, [url1, url2])` 接受 URL 数组，会按浏览器支持自动选第一个可播的，无需手动检测。但本项目优先 `.ogg` 主源，节省加载并发。

---

# 7. 预加载策略

## 7.1 三层加载

| 层级 | 加载时机 | 加载内容 | 失败处理 |
| --- | --- | --- | --- |
| **L1 BootScene 启动** | 应用启动 | 全部 7 个 stinger / jingle / sleep_fade（共 ~250 KB） + `bgm_dialog_pad`（30 KB） | 单首失败 → console.warn，继续启动 |
| **L2 进入地图** | 首次进入 town_hub | `bgm_town_hub_<currentSeason>`（~1.5 MB） | 失败 → fallback 到静音模式 + console.warn |
| **L3 跨地图懒加载** | 玩家走到地图边缘前 5 tile（触发预加载警戒线） | 邻近地图当前季节主曲 | 失败 → 切换瞬间用 `bgm_dialog_pad` 顶住，console.warn |

## 7.2 缓存策略

| 状态 | 处理 |
| --- | --- |
| 已加载且 30 分钟内访问过 | 保留在 `Phaser.Cache` |
| 已加载但 30 分钟内未访问 | 触发清理（`scene.sound.removeByKey()`） |
| 跨季节切换 | 旧季节同地图主曲保留 10 分钟（玩家可能频繁跨季穿越） |

## 7.3 失败 fallback

```typescript
// useAudio.ts 提议扩展
export function playBgm(key: BgmKey, options: PlayBgmOptions = {}): void {
  const game = getPhaserGame();
  if (!game || !game.cache.audio.exists(key)) {
    console.warn(`[useAudio] BGM cue ${key} not loaded, falling back to silence`);
    stopBgm();
    return;
  }
  // ... 现有 crossfade 逻辑
}
```

> **静默 fallback 而非红屏**：教育游戏 BGM 缺失不应阻断学习流程；console.warn 上报到 Sentry 即可，玩家体验是「这一刻没有 BGM」，不影响判题和教学。

---

# 8. 教师可控（班级视角）

## 8.1 班级音频策略 API

| API | 方法 | 用途 | 实现节点 |
| --- | --- | --- | --- |
| `GET /api/classroom/{id}/audio-policy` | GET | 学生 client 拉取当前策略 | R-12 W6 |
| `POST /api/classroom/{id}/audio-policy` | POST | 教师设定 `{ forceMute: boolean, allowSfx: boolean }` | R-13 W2 |
| `SSE /api/classroom/{id}/audio-stream` | SSE | 学生 client 订阅策略变更推送 | R-13 W2 |

## 8.2 学生 client 行为

| 教师策略 | 学生 client 行为 |
| --- | --- |
| `forceMute = true` | 立即 `audio.setMuted(true)`；settings BGM/SFX/UI 滑块禁用，toast「教师已锁定音频」；保存到 `sessionStorage.pyt_audio_teacher_locked = true` |
| `forceMute = false, allowSfx = true` | 解锁滑块，BGM/SFX/UI 恢复用户上一次设置（从 `localStorage` 读取） |
| `forceMute = false, allowSfx = false` | BGM 滑块解锁，SFX/UI 强制 0%；用于教师讲解时学生可有 BGM 但无操作反馈音 |

## 8.3 教师 UI 入口

教师 dashboard `/classroom/{id}` 顶部工具栏新增「音频策略」下拉，三档：全班开放（默认） / 仅 BGM（讲解时） / 全班静音（小测时）。操作日志写入 `classroom_audio_log(classroom_id, teacher_id, policy_json, created_at)`，留 90 天审计。

---

# 9. License 全清单与署名

## 9.1 推荐资源池

| 作者 | 主要协议 | 主页 | 推荐曲风 | 已下载 |
| --- | --- | --- | --- | :---: |
| **Eric Skiff** | CC-BY 4.0 | http://ericskiff.com/music/ | 8-bit chiptune、田园、童趣 | ❌（R-12 待采购） |
| **Kevin MacLeod** | CC-BY 4.0 | https://incompetech.com/ | 全风格、影视级管弦 | ❌（R-12 待采购） |
| **OpenGameArt LPC Music Bundle** | CC0 | https://opengameart.org/content/lpc-music-bundle | LPC 像素游戏适配 | ❌（R-12 待筛选） |
| **Marcio Pereira** | CC0 | https://opengameart.org/users/marcio-pereira | 钢琴、cozy ambient | ❌（R-12 待采购） |
| **RandomMind** | CC0 | https://opengameart.org/users/randommind | medieval、市集、战斗 | ✅（5 首已下载） |
| **TAD** | CC-BY 3.0 | https://opengameart.org/users/tad | 弦乐、settlement 风 | ✅（1 首已下载） |
| **Hitctrl** | CC-BY 3.0 | https://opengameart.org/users/hitctrl | pixie town、欢快 | ✅（2 首已下载） |
| **Spring Spring** | CC-BY 3.0 / CC0 | https://opengameart.org/users/spring-spring | 钢琴、战斗、jingle | ✅（3 首已下载） |
| **MintoDog** | CC0 | https://opengameart.org/users/mintodog | cozy puzzle、解谜 | ✅（1 首已下载） |
| **MouthlessGames** | CC0 / CC-BY 3.0 | https://opengameart.org/users/mouthlessgames | 壁炉钢琴、冬日 | ✅（1 首已下载） |
| **cynicmusic / The Cynic Project** | CC-BY 3.0 | https://opengameart.org/users/cynicmusic | 神秘环境、洞穴 ambient | ✅（1 首已下载） |
| **Chloe Wolfe (Kiira)** | CC-BY 3.0 | https://opengameart.org/users/kiira | 8-bit 错误音、jingle | ✅（1 首已下载） |

## 9.2 必须更新的 License 文件

每首新曲入库必须同时更新：(1) `LICENSE-ART.md` §「BGM 资产盘点」表新增行；(2) `frontend/public/assets/audio/LICENSE.md`（新增文件，结构参考 `pixel/LICENSE.md`）；(3) `frontend/public/credits/audio.html`（R-12 W7 新增，玩家可见署名页）。

CC-BY 3.0/4.0 署名格式：`- "<曲名>" by <作者> — <URL> — licensed CC BY <ver>`。例：`"Underclocked" by Eric Skiff — http://ericskiff.com/music/ — licensed CC BY 4.0`。

## 9.3 严禁条目

| 协议 / 来源 | 原因 | 处置 |
| --- | --- | --- |
| **CC-BY-SA 3.0/4.0 音乐** | SA 传染导致项目分发受限，与代码层 MIT 冲突 | 全部拒收 |
| **CC-BY-NC（任意版本）** | 教育项目可能涉及收费（如校园授权），NC 限制商用 | 全部拒收 |
| 星露谷 / 牧场物语 / 任天堂任意官方 OST | 商业版权 | 全部拒收 |
| YouTube 提取 / Bandcamp 试听版未署 license | 来源不可追溯 | 全部拒收 |
| AI 生成音乐（Suno / Udio 等） | 当前 AI 音乐 license 法律地位未明 | R-12 拒收，R-13 视法律环境再议 |

---

# 10. 可访问性

| 项 | 要求 | 验证 |
| --- | --- | --- |
| **可关全部音频** | settings 面板「整体静音」开关，一键 mute BGM/SFX/UI 三通道 | 单元测试 + 手动 |
| **视觉等价反馈** | 所有依赖音效的事件必须有等价视觉提示，色盲 / 听障也能识别 | 见 §10.1 表 |
| **不依赖音频判定胜负** | KC 解锁 / 任务完成 / boss 击败必须有明确视觉变化，不依赖 stinger 听到 | 灰度截图自检 |
| **音量平衡** | BGM 与对话 SFX 同时播放时，对话清晰可辨（duck 后 BGM ≤ -30dB） | 主观听感 + 频谱分析 |
| **避免高频刺耳** | 所有 cue 12 kHz 以上能量 ≤ -12 dB，避免老旧投影音箱产生刺耳 | 频谱分析 |
| **prefers-reduced-motion 联动** | 用户启用 reduced-motion 时，BGM crossfade ×0.5 提速；EQ 不缓变直接切 | CSS 媒体查询 + JS 检测 |

## 10.1 视觉等价反馈对照表

| 事件 | 音频反馈 | 视觉等价反馈（必须有） |
| --- | --- | --- |
| KC 解锁 | `sfx_jingle_kc_unlock`（6s 8-bit 上升） | HUD 右上角金色 ✓ 弹窗 + 1500ms 弹跳动画 + KC 名称 toast |
| 任务完成 | `sfx_jingle_quest_complete`（32s 胜利号角） | 全屏金光闪烁 200ms + 任务面板项目高亮 + 「+XX Pythoco」飘字 |
| 任务失败 | `sfx_jingle_quest_fail`（8s 低沉小调） | 任务面板红边闪烁 600ms + 「✗ 任务失败」toast 4000ms |
| boss 战进入 | `bgm_bug_mine_boss` 切歌 | 全屏红边发光 400ms + 屏幕轻微抖动 200ms + boss 血条入场 |
| 节日进入 | 节日 cue 切歌 | 全屏金色横幅 1200ms + 节日名称 + 装饰粒子（樱花 / 雪 / 落叶） |
| sleep 推日 | `bgm_sleep_fade` + 黑屏 | 全屏 fade to black 4s + 「Day N」醒来文字 + fade in 1.5s |
| 雨天进入 | 主 BGM 音量降 20% + `sfx_rain_loop` | 屏幕雨粒子 + HUD 雨天图标点亮 |

> **核心原则**：玩家关闭所有音频后，游戏可玩性、教学完整性、信息可达性必须不打折。音频只是「锦上添花」，不是「不可或缺」。

---

# 11. 验收与生效

## 11.1 单元测试（vitest）

新增文件：`frontend/tests/composables/useAudio.spec.ts`

必须覆盖以下断言：

| 用例编号 | 用例 | 预期 |
| --- | --- | --- |
| UA-01 | 调用 `playBgm('bgm_town_hub_spring')` | `currentBgmKey === 'bgm_town_hub_spring'` |
| UA-02 | 季节切换 SPRING → SUMMER | `playBgm` 被调用且 key 为 `bgm_<map>_summer` |
| UA-03 | 时段切换 DAWN → MORNING | `playBgm` **未被调用**（仅 EQ 调节） |
| UA-04 | 天气 SUNNY → RAINY | 主 BGM gain 在 8s 内从 0.6 ramp 到 0.48 |
| UA-05 | 对话开始 → 结束 | BGM gain 200ms 内 ramp 到 0.0316 → ramp 回 1.0 |
| UA-06 | boss 战进入 | `playBgm('bgm_bug_mine_boss', { crossfadeMs: 500 })` |
| UA-07 | 节日 spring_opening 触发 | `playBgm('bgm_festival_ceremony', { crossfadeMs: 800 })` |
| UA-08 | 节日结束 | crossfade 1.5s 回到原主轴 cue |
| UA-09 | sleep 触发 | 4s fadeOut + 5s `bgm_sleep_fade` + 1.5s fadeIn 新日 |
| UA-10 | 教师强制静音 | `audio.setMuted(true)` 调用，settings 滑块 `disabled` |
| UA-11 | cue 加载失败 | console.warn 触发，BGM 停止，无异常抛出 |
| UA-12 | KC 解锁 stinger | `playSfx('sfx_jingle_kc_unlock')` 调用，主 BGM 不被打断 |

## 11.2 端到端测试（Playwright）

新增 spec：`frontend/tests-e2e/audio-flow.spec.ts`

| 用例 | 操作 | 验证 |
| --- | --- | --- |
| E2E-01 | 启动应用 → 截图 | 弹「是否开启音乐」对话框可见 |
| E2E-02 | 点击「开启音乐」 → 等待 1s | `audio.isMuted() === false`，BGM 正在播放（通过 `getCurrentBgmKey()` 断言） |
| E2E-03 | 走到地图边缘切换 town_hub → farm_north | 1.5s 内 `currentBgmKey` 变更，crossfade 完成 |
| E2E-04 | 等待节日 day 1 触发 | `currentBgmKey === 'bgm_festival_ceremony'` |
| E2E-05 | 教师后台发「全班静音」 → 学生端 | 学生 `audio.isMuted() === true`，settings 滑块 `disabled === true` |
| E2E-06 | 「无声验收」：全程 mute 模式下走完 30 分钟首循环 | 所有 KC / 任务 / boss 进度均完成，视觉反馈 100% 可辨 |

## 11.3 性能验收

| 指标 | 阈值 | 测量方法 |
| --- | ---: | --- |
| BGM 首屏加载耗时 | ≤ 800 ms（含 BootScene 全部 stinger + town_hub 当前季节主曲） | Chrome DevTools Network |
| crossfade CPU 占用 | ≤ 5% 单核 | Chrome DevTools Performance 录制 |
| 总 BGM 包大小（仅 .ogg） | ≤ 40 MB | `du -sh frontend/public/assets/audio/bgm/` |
| 总 BGM 包大小（含 .mp3 fallback） | ≤ 80 MB | 同上 |
| 内存峰值（同时缓存 3 个主曲） | ≤ 30 MB | Chrome DevTools Memory |

## 11.4 License 验收

| 检查项 | 验收 |
| --- | --- |
| 所有 30 cue 在 `LICENSE-ART.md` 有作者 / 来源 / 协议条目 | grep 自检 |
| `frontend/public/assets/audio/LICENSE.md` 存在且与 `LICENSE-ART.md` 一致 | diff 自检 |
| `frontend/public/credits/audio.html` 包含所有 CC-BY 作者署名 | 浏览器访问 |
| 无 CC-BY-SA / CC-BY-NC / 商业 OST 残留 | `rg -i 'CC-BY-SA\|CC-BY-NC\|stardew\|nintendo' frontend/public/assets/audio/` |

完成上述 11.1–11.4 全部用例后，本文档由 Draft 升级为 Active（v1.0 → v1.0-released）。

---

# 12. 附录：useAudio.ts 扩展 API 提案（v1.1）

下列方法是本文音乐宪法落地需要 `useAudio.ts` 新增的接口，**本文仅提案**，实际改动由 R-12 W6 子任务执行。

```typescript
export interface AudioComposable {
  playBgm: typeof playBgm;
  stopBgm: typeof stopBgm;
  playSfx: typeof playSfx;
  setMuted: typeof setMuted;
  isMuted: () => boolean;
  getCurrentBgmKey: () => BgmKey | null;
  unlockAudio: typeof unlockAudio;

  bindWorldClock(clock: WorldClock, mapKey: MapKey): () => void;
  applyEqFilter(profile: EqProfile, options: { durationMs: number }): void;
  applyWeatherDuck(weather: Weather): void;
  duckForDialog(): void;
  unduckAfterDialog(): void;
  setChannelVolume(channel: 'bgm' | 'sfx' | 'ui', value: number): void;
  setTeacherLock(locked: boolean): void;
}

export type CueKey =
  | `bgm_${MapKey}_${Season}`            // 20 主曲
  | 'bgm_bug_mine_boss'                  // boss
  | 'bgm_festival_ceremony'              // 仪式节日
  | 'bgm_festival_market'                // 集市节日
  | 'bgm_festival_competition'           // 竞赛节日
  | 'bgm_festival_reflective'            // 静谧节日
  | 'bgm_dialog_pad'                     // 对话默认
  | 'bgm_sleep_fade'                     // sleep 退场
  | 'sfx_jingle_kc_unlock'               // KC 解锁 stinger
  | 'sfx_jingle_quest_complete'          // 任务完成
  | 'sfx_jingle_quest_fail';             // 任务失败
```

> 实际改动 `useAudio.ts` 时必须保留现有所有方法签名，仅新增；现有 `BgmKey` 类型扩充为 `CueKey` 时需做迁移测试，避免破坏 R-100 已落地的 BootScene / WorldScene 调用方。

---

> **本文是宪法，不是说明书**。任何新音频需求（如教师视角对讲机音、班长广播音、Bug 矿洞 boss 二阶段切歌）必须先回到本文第 1 节 cue 收敛策略找语义，找不到再补充新提案。**严禁在组件层硬编码 `playBgm('xxxx')` 直接换歌**，必须经 `bindWorldClock` 自动驱动或显式标记为「特殊 cue」走 §3 流程。
