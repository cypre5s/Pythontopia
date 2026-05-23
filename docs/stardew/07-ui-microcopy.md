---
title: Pythontopia UI 微文案规范（教育优先、像素友好、零打击）
version: v1.0
status: Draft
last_updated: 2026-05-24
owner: UX 写作 + 教育体验设计师
audience: 前端工程师 / 教学策划 / NPC 对白作者 / 教师视角运营 / 本地化
scope: 全端 Web HUD 按钮 / toast / 空状态 / 错误 / NPC 对话 / 班级模板 / aria-label 文案
依赖文档:
  - /home/cypress/Pythontopia/docs/stardew/00-visual-system-overview.md
  - /home/cypress/Pythontopia/docs/stardew/01-palette-tokens-css.md
  - /home/cypress/Pythontopia/docs/gameplay/npc-ecosystem.md
  - /home/cypress/Pythontopia/docs/gameplay/social-classroom.md
  - /home/cypress/Pythontopia/frontend/src/components/hud/HudShell.vue
  - /home/cypress/Pythontopia/AGENTS.md
---

# 0. 文档定位

本文是 Pythontopia 全端**所有玩家可见文本**的规范底本，覆盖按钮 / toast / 空状态 / 错误 / HUD 标签 / NPC 对白 / 教师班级模板 / 屏幕阅读器 aria-label。任何 Vue 组件、Phaser 场景、Android 挂件新加文本之前，先翻这里；新文案落在 i18n 文件之前，先在本表登记 key。

**为什么需要专门的微文案规范**：

1. **教育版伦理刚性约束**：面向高校 Python **零基础初学者**，文案必须不嘲讽、不催促、不利用焦虑、不暗示"你不够聪明"。AGENTS.md 已硬性规定"教育优先，游戏承载"——一句无心吐槽就能毁掉两节课的兴趣维护。
2. **像素字体的物理上限**：主字体 `Ark Pixel 12` 12 px 单字宽度 12 px；HUD 顶部条单 chip 内可视宽度约 220 px，**一行最多 18 个汉字**（含标点）。超出就被 9-slice 截断或换行毁版。
3. **NPC 多人格协同**：20 个 NPC 各有不同性格（见 `docs/gameplay/npc-ecosystem.md`），让每位作者各写各的会风格漂移；必须用一份规范统一"友好基线"再分发个性。
4. **班级架构教学场景**：教师发布的任务、班级广播必须**专业**且**安全**，不能误传"绝对、必须、保证通过"等暗示性话术。
5. **未来 i18n**：文案先入中文表，再镜像 en-US，避免业务代码里散装硬编码。

**本文的产出物**：

- 30 个核心按钮文案表；20 条 toast；10 条空状态；15 条错误；6 个 HUD 标签；NPC 对话规范 + 11 位主要 NPC 口头禅；10 个班级模板；i18n key 命名空间；aria-label 清单；禁用词清单；vitest 抽样断言。

> 本文不涉及"动画时长 / 字号阶梯 / 颜色"——这些在 `00-visual-system-overview.md` 与 `01-palette-tokens-css.md` 已规范；本文只管"**说什么字**"。

---

# 1. 语气总则

## 1.1 五条核心原则

| # | 原则 | 正例 | 反例 |
| --- | --- | --- | --- |
| 1 | **用「你」不用「您」** | 你完成了今天的任务 | 您完成了今天的任务 |
| 2 | **短句 ≤ 20 字** | 能量低了，去医务那儿歇会儿 | 您的能量数值已经降到了较低水平，建议您前往医务处恢复 |
| 3 | **一行 ≤ 18 汉字**（像素 12px） | 任务完成，可领奖励 | 任务已经顺利完成，可以前往领取奖励 |
| 4 | **禁感叹号**（除节日/成就） | 任务完成。 | 任务完成！！！ |
| 5 | **拒绝口号 / 套话** | 试试这道题 | 加油，相信你一定行 |

> 「您」一字像素字下与「你」一致宽度，但产生"客服距离感"，与"小镇邻里"叙事冲突，**全局禁用**（仅教师后台一处例外：教师身份的合规通知保留"您"）。

## 1.2 标点用法

| 标点 | 用法 | 备注 |
| --- | --- | --- |
| `。` | 默认结句 | 全角中文句号 |
| `，` `、` | 同上 | 不使用半角 |
| `？` | 提问、引导思考 | 不连用 |
| `！` | 仅限节日彩蛋、KC 解锁、师徒达成；其它场景禁 | 频率上限：玩家单日 ≤ 5 次 |
| `…` | 表示等待、思考；不超过 3 个点 | NPC 自然停顿专用 |
| `——` | 强调或解释 | NPC 教学时常用 |
| 表情 / emoji | HUD / toast 允许，正式按钮禁 | 必须有 aria-label 文字替代 |

## 1.3 三种语境基调

| 语境 | 基调 | 用词偏好 | 例 |
| --- | --- | --- | --- |
| **系统提示**（toast、错误） | 平静、克制、可操作 | 名词、动词、不评价 | 「保存成功」「网络断了，再试一次」 |
| **NPC 对话** | 邻里、温暖、有人情味 | 称呼、生活比喻 | 「今儿天好，地里那株 KC-031 看上去精神」 |
| **教师/班级** | 专业、清晰、留余地 | 时间、范围、动作 | 「本周班级任务：阅读 p124–p140，周日 22:00 截止」 |

---

# 2. 按钮文案表（30 个）

> 字符数按"汉字数 + 半角字符 ÷ 2 向上取整"计；像素 12px 时每"汉字位"占 12 px。

| # | 场景 | 默认文案 | 字符数 | aria-label | 反例 |
| --- | --- | --- | --- | --- | --- |
| 1 | 确认 | 确认 | 2 | 确认操作 | OK / 是的我确认 |
| 2 | 取消 | 取消 | 2 | 取消操作 | 不取消 / 不要 |
| 3 | 关闭 | 关闭 | 2 | 关闭面板 | × / 关掉 |
| 4 | 保存 | 保存 | 2 | 保存当前进度 | 存档保存到本地 |
| 5 | 删除 | 删除 | 2 | 删除该条目 | 永久删除请谨慎 |
| 6 | 重置 | 重置 | 2 | 恢复默认 | 还原回最初状态 |
| 7 | 提交 | 提交 | 2 | 提交答案给服务端 | 提交 OK |
| 8 | 下一步 | 下一步 | 3 | 进入下一步 | NEXT |
| 9 | 上一步 | 上一步 | 3 | 返回上一步 | 退后 |
| 10 | 查看 | 查看 | 2 | 查看详情 | 看一下 |
| 11 | 选择 | 选择 | 2 | 选中此项 | 选它 |
| 12 | 完成 | 完成 | 2 | 标记为完成 | 搞定 |
| 13 | 跳过 | 跳过 | 2 | 跳过此步 | 不做了 |
| 14 | 重试 | 再试一次 | 4 | 重试当前操作 | 重新来 |
| 15 | 返回 | 返回 | 2 | 返回上一页 | 退回去 |
| 16 | 退出 | 退出 | 2 | 退出当前模式 | 走人 |
| 17 | 帮助 | 帮助 | 2 | 打开帮助 | 帮我 |
| 18 | 设置 | 设置 | 2 | 打开设置 | 配置 |
| 19 | 开始 | 开始 | 2 | 开始 | START |
| 20 | 暂停 | 暂停 | 2 | 暂停游戏 | 停一下 |
| 21 | 继续 | 继续 | 2 | 继续游戏 | 接着玩 |
| 22 | 开始游戏 | 开始游戏 | 4 | 开始新对局 | 进入游戏 |
| 23 | 加载游戏 | 加载存档 | 4 | 加载已有存档 | 读档 |
| 24 | 新存档 | 新建存档 | 4 | 新建一个存档 | 新档 |
| 25 | 复制 | 复制 | 2 | 复制到剪贴板 | 拷贝 |
| 26 | 粘贴 | 粘贴 | 2 | 从剪贴板粘贴 | 贴上 |
| 27 | 编辑 | 编辑 | 2 | 编辑此项 | 改一改 |
| 28 | 收藏 | 收藏 | 2 | 加入收藏 | 喜欢 |
| 29 | 分享 | 分享 | 2 | 分享此内容 | 给别人看 |
| 30 | 反馈 | 反馈 | 2 | 提交反馈 | 吐槽 |

**通用约束**：

- 单按钮宽度上限 64 px（像素 12 px × 4 汉字 + 边距）；超 4 字必须用图标 + tooltip 替代。
- "确认 / 取消" 永远成对出现，且**确认在右**（中文语境的"先思考再行动"习惯）。
- "删除" 必须有二次确认弹窗，且确认按钮文字改为「删除」（不写「确认删除」绕回安全感），同时把"删除"按钮配色用 `--pyt-color-negative #D9534F`。
- "重试"统一写「再试一次」，**不用**「重试」二字——后者像服务器响应，前者像同学拍肩。

---

# 3. Toast 文案（20 条）

> Toast 上限 24 字（≈ 1.5 像素行）；持续时间默认 2400 ms；关键操作 4000 ms。

| key | 触发场景 | 文案 | 字符 | 持续 |
| --- | --- | --- | --- | --- |
| `toast.quest.complete` | 任务完成 | 任务完成，去 NPC 处领奖 | 11 | 3000 |
| `toast.item.gained` | 物品获得 | 收到 {item}，在背包里 | 9 | 2400 |
| `toast.energy.low` | 能量 ≤ 20 | 能量低了，去医务歇一会 | 11 | 4000 |
| `toast.energy.zero` | 能量耗尽 | 今天累了，明早再来吧 | 10 | 4000 |
| `toast.network.offline` | 网络断开 | 网络断了，进度已暂存 | 10 | 4000 |
| `toast.network.online` | 网络恢复 | 网络回来了，进度已同步 | 11 | 2400 |
| `toast.save.success` | 保存成功 | 已保存 | 3 | 1600 |
| `toast.save.cloud.synced` | 云存档同步 | 云端已同步，可换设备继续 | 12 | 2400 |
| `toast.kc.mastered` | KC ≥ 0.8 | KC「{kcName}」掌握度提升 | 13 | 3000 |
| `toast.kc.new` | 新 KC 首次解锁 | 解锁新知识点：{kcName} | 11 | 3000 |
| `toast.area.unlocked` | 解锁新地图 | {areaName} 已开放，去看看 | 13 | 3000 |
| `toast.area.locked.hint` | 走到锁住的边界 | 这里还没开，先去找守门 | 11 | 3000 |
| `toast.pyodide.timeout` | Pyodide 超时 | 本机预览久了，先停一下 | 11 | 3000 |
| `toast.pyodide.ready` | Pyodide 就绪 | 本机预览准备好啦 | 8 | 1600 |
| `toast.judge.queued` | 提交进入判题队列 | 服务端判题中，稍等 | 9 | 2400 |
| `toast.judge.passed` | 判题通过 | 通过了，今天又稳一步 | 10 | 3000 |
| `toast.judge.failed` | 判题失败 | 没过，看下用例 1 的差异 | 12 | 4000 |
| `toast.friendship.up` | NPC 好感 +3 | 与 {npc} 的关系近了一些 | 12 | 2400 |
| `toast.classroom.broadcast` | 教师广播到达 | 班级公告：{title} | 8 | 4000 |
| `toast.skill.unlocked` | 技能树解锁 | 新技能可学：{skillName} | 11 | 3000 |

**Toast 风格约束**：

- **不用感叹号**（除"通过了"等情绪可放松的成功类，并仍然落句号）。
- **能量 / 网络 / 超时**类先告知"发生了什么"，再给"下一步动作"，**不评价玩家**。
- **失败类**指引"下一步看哪里"（"看下用例 1 的差异"），不重复"你错了"。

---

# 4. 空状态文案（10 条）

> 空状态卡片高度 ≥ 80 px，文案 1 行主 + 1 行副；主文案 ≤ 12 字、副文案 ≤ 18 字。

| key | 场景 | 主文案 | 副文案 |
| --- | --- | --- | --- |
| `empty.inventory` | 背包空 | 背包空空的 | 完成任务或在野外采集会有惊喜 |
| `empty.classroom.quest` | 班级任务空 | 还没有班级任务 | 等老师发布，或先做日常签到 |
| `empty.friends` | 好友列表空 | 还没有同学加你 | 去港口公告板贴个名片试试 |
| `empty.mistakes` | 错题本空 | 错题本是空的 | 写错也没关系，先去做题积累 |
| `empty.skill.locked` | 技能未解锁 | 还没解锁技能 | 完成对应章节即可点亮 |
| `empty.path.plan` | 路径规划尚未生成 | 路径还没规划 | 与镇长聊聊，他会帮你看看 |
| `empty.dialog.history` | NPC 对话记录空 | 还没和 TA 聊过 | 走近一些按 F 就能开口 |
| `empty.classroom.member` | 班级成员空 | 班里还没人 | 把 joinCode 发给同学就行 |
| `empty.bug.mine.log` | 矿洞失败日志空 | 矿洞还没下过 | 提交一次失败用例就能进入 |
| `empty.notification` | 通知中心空 | 暂时没有通知 | 这里很安静，挺好 |

**空状态风格约束**：

- 不用"暂无数据"等开发腔；不用"空空荡荡"等略带消极词。
- 副文案必须给"下一步去哪"（动作指引），让空状态本身也变成引导。

---

# 5. 错误消息（15 条）

> 错误必须"不恐吓 + 可操作"。任何 5xx / 网络异常都不能裸出后端 stacktrace。

| key | 触发 | 文案 | 备注 |
| --- | --- | --- | --- |
| `error.network.offline` | 无网络 | 网络好像断了，刷新看看 | 提供「再试一次」按钮 |
| `error.server.5xx` | HTTP 5xx | 服务器打了个喷嚏，刷新试试 | 重试 ≥3 次后引导反馈通道 |
| `error.auth.expired` | JWT 过期 | 登录有点久了，重新进一下 | 引导跳登录页 |
| `error.auth.forbidden` | 403 | 这里需要先解锁权限 | 不暗示"你不行" |
| `error.not.found` | 404 资源 | 找不到这个东西，可能被搬家了 | 提供"返回上一页" |
| `error.validate.required` | 表单必填 | {field} 还没填 | {field} 用人类标签 |
| `error.validate.format` | 表单格式 | {field} 格式不太对 | 给出格式样例 |
| `error.judge.runtime` | 判题运行错 | 代码跑到一半停了，看 traceback | 显示行号 + KC 链接 |
| `error.judge.timeout` | 判题超时 | 用时超过 3 秒，看看是不是死循环 | 链向 Ch4 流程自动化 |
| `error.upload.size` | 上传超限 | 文件大了一点，压一压再传 | 给上限：5 MB |
| `error.classroom.join.code` | 班级码无效 | 加入码不对，找老师再要一份 | 不写"无效"（冷硬） |
| `error.classroom.join.full` | 班级满员 | 这班人多了，先告诉老师 | 软处理"软上限 200" |
| `error.save.conflict` | 存档冲突 | 别处也在存，等几秒再来 | 多端冲突避免数据丢 |
| `error.pyodide.boot` | Pyodide 启动失败 | 本机预览没起来，可走服务端 | 给"服务端提交"按钮 |
| `error.rate.limit` | 限流 | 操作太快啦，喘口气 | 显式倒计时 5–10 s |

**错误风格约束**：

- 主语用"东西 / 服务器 / 它"，**不用"您 / 系统拒绝您"**。
- 不显示英文异常字符串（如 `NullPointerException`）；技术细节只在 console / 反馈包里。
- "服务器打了个喷嚏"是拟人化招牌——可在 Doc 馆长口头禅与之呼应，统一吉祥物形象。

---

# 6. HUD 标签（6 个）

与 `frontend/src/components/hud/HudShell.vue` 现有 6 项一一对应（**禁改键 / 改大小写**）。

| HUD chip | 中文标签 | testid | 取值示例 | aria-label |
| --- | --- | --- | --- | --- |
| `weather` | 天气 | `hud-weather` | 晴 / 雨 / 雪 / 雾 | 当前天气 |
| `dayIndex` | Day | `hud-day` | 1–112 | 第几天 |
| `timeOfDay` | 时段 | `hud-time` | 早晨 / 上午 / 中午 / 下午 / 傍晚 / 夜间 | 当前时段 |
| `season` | 季节 | `hud-season` | 春 / 夏 / 秋 / 冬 | 当前季节 |
| `energy` | 能量 | `hud-energy` | 0–100 | 能量值 |
| `coin` | 金币 | `hud-coin` | 任意非负整数 | 金币数 |

**HUD 文案约束**：

- "Day" 不译为「天」——避免与「天气」串视觉；保留英文与现有像素字 fallback 一致。
- 「掌握」chip（meta.masteryAvg）已存在，与上述 6 个**并列但不重叠**，独立 key `hud.label.mastery` = 「掌握」。
- 能量条满 80% 时显示 `--pyt-color-positive #6BBF59`；≤ 20% 显示 `--pyt-color-negative #D9534F`，**但文字仍为标签「能量」**——不改写成"危险"等情绪词。

---

# 7. NPC 对话规范

## 7.1 通用规则

| # | 规则 | 例 |
| --- | --- | --- |
| 1 | 一段对话 ≤ 4 句 | 超出走"打字机分段" |
| 2 | 教学要点直说，不绕弯 | 「KC-031 的关键是循环条件，先看 p128」 |
| 3 | 每段第一句先称呼或动作 | 「（抬头）你来了，今天精神不错」 |
| 4 | 一段必有一个动作或一个问题 | 让玩家有"按 E 继续"以外的反馈空间 |
| 5 | NPC 不评价玩家智力 | 禁「这么简单都不会」「你怎么又错」 |
| 6 | NPC 可自嘲、可示弱 | 「上次我也写错过这个循环」 |

## 7.2 11 位主要 NPC 专属口头禅

> 与 `docs/gameplay/npc-ecosystem.md` 人设严格对齐；每位口头禅出现频率 ≤ 30%，避免单调。

| NPC | 人设 | 口头禅（≤ 12 字） | 用法 |
| --- | --- | --- | --- |
| `mayor_py` 镇长 Py | 入镇导览、整体规划 | 「来，路径我陪你看」 | greet / weekly_recap |
| `farmer_kc` 田老 | KC 庄稼比喻 | 「这株 KC 该浇水了」 | KC mastery 提醒 |
| `librarian_doc` 馆长 Doc | 文档检索权威 | 「翻到这页就明白」 | find_evidence / hallucination_warn |
| `coach_q` 教务 Q | 推题、按掌握度推荐 | 「下一题，看准节奏」 | next_problem |
| `foreman_bug` 矿头 Bug | 矿洞副本工头 | 「带上头灯，下去看看」 | bug_mine 触发 |
| `sage_review` 复盘师 Sage | 周五复盘殿 | 「坐一坐，说一句心得」 | weekly_retro |
| `merchant` 港口商人 | 卖书、卖 buff | 「今儿这批新到的不错」 | shop |
| `class_president` 班长 | 班级公告唯一入口 | 「老师让我转给你」 | classroom_quest 发放 |
| `postman` 邮差 | 跨地图投递 | 「正好路过，给你这个」 | daily 签到 |
| `chef` 厨师 | KC 主题菜单 | 「今天新出列表沙拉」 | KC buff 道具 |
| `student_anxious` 焦虑小安 | 同伴互助 | 「错也没关系，再来一次」 | peer_pair 拉伴 |

## 7.3 三段式打字机示例（馆长 Doc · find_evidence 节点）

```text
（抬头）你来啦。
KC-031 在 p128 第三段，那里写得最清。
不放心可以翻到 p131 看变体。
有需要我帮你标出来。
```

> 4 句、每句 ≤ 18 字、教学要点（页码）明示、收尾给行动空间。

## 7.4 禁止的 NPC 话术

- 「你怎么连这都不会」「这么简单」「真笨」 → **禁**
- 「这道题没做出来你就别想毕业」 → **禁催促**
- 「绝对是正确答案，照抄就行」 → **禁误导**（且违反 AGENTS.md 防幻觉硬约束）
- 任何政治 / 地域 / 性别 / 体貌 调侃 → **零容忍**

---

# 8. 教师 / 班级文案

## 8.1 发布任务模板（5 个）

> 模板字段：`{title} / {chapter} / {pageRange} / {due} / {note}`；教师后台预填，可编辑。

| key | 类型 | 模板 |
| --- | --- | --- |
| `tpl.quest.read` | 阅读 | 本周阅读：{chapter}（p{pageStart}–p{pageEnd}），{due} 截止 |
| `tpl.quest.practice` | 练习 | 推荐练习：{count} 道题，覆盖 {kcList}，{due} 截止 |
| `tpl.quest.bugmine` | Bug 矿洞 | Bug 副本：把 {kcName} 的失败样例做完一轮，{due} 前提交 |
| `tpl.quest.retro` | 复盘 | 周五复盘：写一句本周收获，{due} 22:00 关 |
| `tpl.quest.peer` | 共写 | 组队共写：与同学共写 {kcName}，{due} 前提交合并版 |

## 8.2 班级广播模板（5 个）

> 广播长度 ≤ 60 汉字，自动追加教师署名；不允许写"必须 / 一定 / 否则"。

| key | 场景 | 模板 |
| --- | --- | --- |
| `tpl.bcast.welcome` | 开课 | 欢迎来到 {className}，本学期一起把 Python 走完一遍 |
| `tpl.bcast.weekly` | 周计划 | 本周重点：{focusKc}，建议节奏 {pacePerDay} 题 / 天 |
| `tpl.bcast.review` | 临近期中 | 期中前一周复盘提醒：把错题本翻一翻，挑 3 题再做 |
| `tpl.bcast.encourage` | 鼓励 | 看到大家最近活跃度上来了，继续保持节奏就好 |
| `tpl.bcast.deadline` | 截止提醒 | {title} 今晚 22:00 截止，还没交的同学留意一下 |

**教师文案约束**：

- 教师端是**唯一保留"您"的场景**（针对教师身份的合规通知，如「您发布的任务已生效」）；面向学生的广播仍用「你 / 大家」。
- 不允许 toast 强弹"老师在监视你"——R-13 教师视角拓展不包含实时监控大屏（AGENTS.md 已禁）。

---

# 9. i18n 准备

## 9.1 文件位置

| 文件 | 用途 | 现状 |
| --- | --- | --- |
| `frontend/src/i18n/zh-CN.json` | 中文文案主表 | **尚未创建** —— R-12 接入 vue-i18n 时一并落地 |
| `frontend/src/i18n/en-US.json` | 英文镜像表 | 同上 |
| `frontend/src/i18n/index.ts` | i18n 装载器 | 同上 |

## 9.2 key 命名空间

按"组件类 + 元素 + 语义"三段式：

| 命名空间 | 用法 | 示例 |
| --- | --- | --- |
| `hud.btn.<action>` | HUD 按钮 | `hud.btn.confirm` |
| `hud.label.<chip>` | HUD 标签 | `hud.label.energy` |
| `toast.<scene>.<state>` | toast | `toast.energy.low` |
| `empty.<panel>` | 空状态 | `empty.inventory` |
| `error.<domain>.<reason>` | 错误 | `error.network.offline` |
| `npc.<npcKey>.<nodeId>` | NPC 对话 | `npc.mayor_py.greet` |
| `tpl.quest.<kind>` | 教师任务模板 | `tpl.quest.read` |
| `tpl.bcast.<kind>` | 教师广播模板 | `tpl.bcast.weekly` |
| `aria.<componentSlug>.<action>` | 屏读标签 | `aria.hud-shell.openMap` |

## 9.3 key 写入流程

1. 新增组件时，**先**在本文档登记 key（追加表行 + PR review 通过）。
2. **再**在 `zh-CN.json` 写中文（占位 `__TODO_EN__` 落 `en-US.json`）。
3. 组件代码用 `t('hud.btn.confirm')` 调用，**禁止硬编码中文字符串**。
4. CI 增加 `scripts/check-i18n-key.mjs`：扫描 `*.vue / *.ts` 中文字符串硬编码 → fail。

## 9.4 占位符与单复数

- 占位符统一 `{name}` 双花括号风格（与 vue-i18n 默认 `{}` 一致；ICU 复数 R-13 再上）。
- 英文翻译"first item"等需复数时落 `t('toast.item.gained', {item: name})`，复数交给 ICU。

---

# 10. 屏读 / 盲文友好

## 10.1 aria-label 完整性清单

| 控件类 | 必须 aria-label | 例 |
| --- | --- | --- |
| 仅图标按钮（≤ 4 字省略） | ✅ | `<button aria-label="关闭面板">×</button>` |
| Emoji 装饰 | ✅ 文字替代 | `<span aria-hidden="true">🌱</span><span class="sr-only">种子</span>` |
| 进度条 | ✅ + `role=progressbar` + `aria-valuenow/min/max` | 见 `HudShell.vue` 能量条 |
| toggle / aria-pressed | ✅ + `aria-pressed` | 见 `HudShell.vue` quickslot |
| 模态 | ✅ + `role=dialog` + `aria-modal=true` | DialogModal、BoardPanel |
| 装饰性图片 | `alt=""` + `aria-hidden=true` | 节日彩蛋小图 |

## 10.2 emoji 替代约定

| Emoji | 出现位置 | 文字替代 |
| --- | --- | --- |
| 🌱 | 背包种子 | 种子 |
| 🔧 | 背包工具 | 工具 |
| 💧 | 背包水壶 | 水壶 |
| ⛏️ / ⚒️ | 矿镐 / 锤子 | 矿镐 |
| 🪲 | Bug 罐 | 虫罐 |
| 📖 | 课件 | 课件 |
| 📜 | 任务卷轴 | 任务 |
| 🎁 | 礼物 | 礼物 |
| 🧪 | 消耗品 | 药水 |

> 所有装饰性 emoji 必须包裹一层 `<span class="sr-only">`，否则屏读器跳过将丢失语义；这条已写入 `scripts/check-a11y.mjs` 计划清单。

## 10.3 颜色不依赖

- 能量条用颜色 + 数字双通道；
- 错误 / 成功 toast 用图标 + 文字双通道（不依赖红绿单一信号）；
- 焦点环必须用 `--pyt-color-focus-ring #F1C453` 2 px 实线，禁止仅靠 `:hover` 颜色变化判定选中。

---

# 11. 禁用词清单

## 11.1 绝对禁用（任何上下文）

| 类别 | 词 | 备选 |
| --- | --- | --- |
| 人格贬低 | 笨 / 傻 / 蠢 / 白痴 / 弱智 / 智障 | 「再来一次」「这次差一点」 |
| 歧视 | 任何性别 / 地域 / 民族 / 残障调侃 | 全部删 |
| 政治敏感 | 国家领导人 / 主权争议词 | 全部删 |
| 暗示伤害 | 自杀 / 抑郁 / 想死 | 全部删 |
| 暴力 | 揍 / 打死 / 弄死 | NPC 不出现暴力威胁 |
| 商业混淆 | 「正版 / 盗版 / 破解」 | 不讨论游戏本身的盗版语境 |

## 11.2 慎用（仅在确切场景）

| 词 | 慎用原因 | 允许场景 |
| --- | --- | --- |
| 必须 / 一定 / 绝对 | 学习领域不存在"绝对" | 仅限技术约束："此处必须填正整数" |
| 加油 | 易显空洞 | 仅同伴 NPC 偶发；教师禁 |
| 简单 / 容易 | 暗示"你不会很丢人" | 仅 NPC 自嘲："这题我以前也错过，没那么简单" |
| 立刻 / 马上 / 现在就 | 制造焦虑 | 紧急网络异常允许"再试一次" |
| 永远 / 从此 | 教育避免绝对承诺 | 禁 |

## 11.3 政治 / 地域 / 群体审查表

- 一切涉及国家、地区、民族、宗教、性别、性取向、外貌、收入、家庭背景的**比较性表述**全部禁止；
- NPC 名字、地名只用虚构或通用词（"港口 / 校园 / 矿洞"），不取真实城市名；
- 教师广播禁止涉及学校内部敏感事件（如某次考试评分争议）。

---

# 12. 验收：vitest 抽样断言

新建 `frontend/tests/i18n/microcopy.spec.ts`，抽样断言 5 按钮 + 5 toast 与本规范一致；任何 i18n 文件修改触发 CI 运行该 spec。

```ts
import { describe, expect, it } from 'vitest';
import zh from '@/i18n/zh-CN.json';

describe('microcopy zh-CN 抽样规范', () => {
  it.each([
    ['hud.btn.confirm', '确认'],
    ['hud.btn.cancel', '取消'],
    ['hud.btn.close', '关闭'],
    ['hud.btn.retry', '再试一次'],
    ['hud.btn.startGame', '开始游戏'],
  ])('按钮 %s 文案为 "%s"', (key, expected) => {
    expect(getByPath(zh, key)).toBe(expected);
  });

  it.each([
    ['toast.energy.low', '能量低了，去医务歇一会'],
    ['toast.network.offline', '网络断了，进度已暂存'],
    ['toast.save.success', '已保存'],
    ['toast.judge.passed', '通过了，今天又稳一步'],
    ['toast.kc.mastered', /KC「.+」掌握度提升/],
  ])('toast %s 文案符合规范', (key, expected) => {
    const v = getByPath(zh, key);
    if (expected instanceof RegExp) expect(v).toMatch(expected);
    else expect(v).toBe(expected);
  });

  it('禁用词不得出现', () => {
    const banned = ['笨', '傻', '蠢', '白痴', '弱智', '智障'];
    const flat = JSON.stringify(zh);
    for (const w of banned) expect(flat.includes(w)).toBe(false);
  });

  it('「您」仅允许出现在 tpl.teacher.* 域', () => {
    const offenders: string[] = [];
    walk(zh, '', (k, v) => {
      if (typeof v === 'string' && v.includes('您') && !k.startsWith('tpl.teacher.')) {
        offenders.push(`${k} -> ${v}`);
      }
    });
    expect(offenders).toEqual([]);
  });
});

function getByPath(obj: Record<string, unknown>, path: string): unknown {
  return path.split('.').reduce<unknown>((acc, p) =>
    acc && typeof acc === 'object' ? (acc as Record<string, unknown>)[p] : undefined, obj);
}
function walk(obj: unknown, prefix: string, cb: (k: string, v: unknown) => void): void {
  if (obj && typeof obj === 'object') {
    for (const [k, v] of Object.entries(obj)) {
      const path = prefix ? `${prefix}.${k}` : k;
      cb(path, v);
      walk(v, path, cb);
    }
  }
}
```

**验收门槛**：

- 上述 4 个 `it` 全部 pass；
- `npm run typecheck` 通过；
- 任意提交触碰 `frontend/src/i18n/*.json` 必须同步更新本文档表格 —— 在 `package.json` `lint:i18n` 脚本里加入 docs 校验。

---

-- 子代理生成于 2026-05-24 (UTC+8)
