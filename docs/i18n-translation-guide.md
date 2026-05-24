---
title: Pythontopia i18n 翻译规范（zh-CN ↔ en-US）
version: v1.0
status: Draft
last_updated: 2026-05-24
owner: 国际化（i18n）工程师 + UX 写作
audience: 前端工程师 / NPC 对白作者 / 教师视角运营 / 翻译外包 / QA
scope: frontend/src/i18n/*.json + 组件 t/tn 调用 + 资源生成层文本回流
依赖文档:
  - /home/cypress/Pythontopia/docs/stardew/07-ui-microcopy.md
  - /home/cypress/Pythontopia/frontend/src/i18n/index.ts
  - /home/cypress/Pythontopia/frontend/src/composables/useI18n.ts
  - /home/cypress/Pythontopia/AGENTS.md
---

# 1. 定位

本指南是 Pythontopia **所有"会被翻译"文本**的工程操作手册：写规范（命名 / 占位符 / 复数）、
流程规范（PR 与 `07-ui-microcopy.md` 双向同步）、工具链规范（校验脚本、CI 门、
`useI18n` composable 用法）。

> 文案语气（用「你」不用「您」、禁感叹号、禁催促）属 UX 写作，已在
> `docs/stardew/07-ui-microcopy.md` § 1 规范化，本指南不重复，只在第 3 章给"翻译复检
> 最小集"和反例。

**不涉及**：像素字距字号（→ `00-visual-system-overview.md`）；NPC 性格人设
（→ `docs/gameplay/npc-ecosystem.md`）；班级广播业务流（→ `docs/gameplay/social-classroom.md`）；
防幻觉证据校验（→ `services/pythontopia-graph` GroundingCritic）。

---

# 2. key 命名规范

## 2.1 三段式

格式：`<域>.<元素>.<语义>`，全小写，单词内驼峰，段间用 `.`。

| 域前缀 | 用途 | 例 |
| --- | --- | --- |
| `hud.btn.*` | HUD 顶部 / 底部按钮 | `hud.btn.confirm` |
| `hud.label.*` | HUD chip 文字标签 | `hud.label.energy` |
| `toast.*` | 全局 toast | `toast.energyLow` |
| `empty.*` | 空状态面板 | `empty.inventory` |
| `error.<domain>.<reason>` | 错误消息 | `error.network.offline` |
| `npc.<npcKey>.<nodeId>` | NPC 对白节点 | `npc.mayor_py.greeting` |
| `tpl.classroom.*` | 教师任务 / 广播模板 | `tpl.classroom.welcome` |
| `aria.<componentSlug>.<action>` | 屏读 aria-label | `aria.hud-shell.openMap` |

## 2.2 命名硬约束

| 约束 | 说明 |
| --- | --- |
| 段数 ≥ 2，≤ 4 | 太短缺语义，太长难维护 |
| 单段长度 ≤ 24 字符 | 防止 IDE 折行毁可读 |
| 单词内驼峰 `kcName` | 与 JS 对象 key 风格保持一致 |
| 严禁中文 / 全角 | key 必须 ASCII，文案 zh-CN 在值里 |
| 严禁动词单独成段 | `toast.click` ✗，`toast.click.success` ✓ |
| NPC key 用 `<npcKey>` snake_case | 与 `data/npcs/<key>/meta.json` 对齐 |

## 2.3 命名反例

| 反例 | 问题 | 推荐 |
| --- | --- | --- |
| `Confirm` | 单段 + 大写驼峰 | `hud.btn.confirm` |
| `toast.energy_low` | 段内下划线 | `toast.energyLow` |
| `提示.能量低` | 中文 key | `toast.energyLow` |
| `error.500` | 缺 domain | `error.backend.500` |

---

# 3. 文案语气（翻译时复检最小集）

详见 `07-ui-microcopy.md` § 1。本节给翻译外包的"5 条不踩雷红线"：

| # | 规则 | zh-CN 例 | en-US 等价 |
| --- | --- | --- | --- |
| 1 | 不用「您」（除 `tpl.teacher.*`） | 你完成了任务 | You finished the quest. |
| 2 | 不嘲讽智力 | 再来一次 | Try again. |
| 3 | 不催促 | 留意一下截止时间 | Heads up on the deadline. |
| 4 | 错误不裸出 stacktrace | 服务器打了个喷嚏 | The server sneezed. |
| 5 | 末尾标点：中文 `。`，英文 `.` | 已保存。 | Saved. |

**翻译反例**（必须 reject）：

| ❌ | 问题 | ✅ |
| --- | --- | --- |
| You are too slow!!! | 三个感叹号 + 催促 | You're offline. Progress is cached. |
| Hey idiot, retry. | 人格贬低 | Try again. |
| 您必须立刻提交！ | 「您」+「必须」+「立刻」+「！」 | 今晚 22:00 截止，没交的同学留意 |
| Server returned NPE | 裸技术名 | The server sneezed. Try refreshing. |

---

# 4. 字符宽度与排版

## 4.1 像素字体物理上限

| 字体 | 像素宽 | HUD chip 内可视宽 | 单行上限 |
| --- | --- | --- | --- |
| `Ark Pixel 12`（中文） | 12 px / 字 | 220 px | 18 汉字 |
| `Press Start 2P`（英文） | 8 px / 字（粗体阶梯） | 220 px | 27 字母 |

## 4.2 文案长度门槛

| 元素 | zh-CN 上限 | en-US 上限 | 备注 |
| --- | --- | --- | --- |
| HUD 按钮 | 4 汉字 | 8 字母 | 超出转图标 + tooltip |
| Toast 主体 | 24 汉字 | 48 字母 | 一行半像素行 |
| 空状态主文案 | 12 汉字 | 24 字母 | 一行 |
| 空状态副文案 | 18 汉字 | 36 字母 | 给"下一步" |
| NPC 单段对白 | 18 汉字 / 4 段 | 36 字母 / 4 段 | 打字机 |
| 班级广播 | 60 汉字 | 120 字母 | 带教师署名 |

## 4.3 英译扩张系数

经验：英文比中文长 1.8–2.2 倍。zh-CN 接近 18 汉字极限时，en-US 大概率溢出 36 字母上限。
翻译应**主动重写**而非逐字 literal——例如 `能量低了，去医务那儿歇会儿` 应译为
`Energy is low. Rest at the clinic.`，而不是 `Your energy is low, please go to the clinic to rest a while`。

---

# 5. 复数规则

## 5.1 当前实现（R-12 Beta）

`frontend/src/composables/useI18n.ts` 提供 `tn(key, count, params?)`：

- **zh-CN**：透传，不做形态变化（中文名词无复数）；
- **en-US**：`count === 1` / `-1` 单数；其余触发"末尾英文段 +s / +es / +ies"简化规则。

简化规则覆盖范围：

| 末尾词形 | 处理 | 例 |
| --- | --- | --- |
| 普通 | +s | `bag → bags` |
| 词尾 s / x / z / ch / sh | +es | `box → boxes` |
| 辅音 + y | -y → ies | `city → cities` |
| 元音 + y | +s | `day → days` |
| 不规则单复数 | **不支持** | `child → children` ✗ |

## 5.2 编写复数文案的硬约束

| 约束 | 反例 | 正例 |
| --- | --- | --- |
| 文案必须以可数单数名词收尾 | "Check your bags." | "Check your bag." |
| 句号 / 引号置于词后 | "1 item ready." | OK，简化规则会跳过标点 |
| 不规则名词 → 走两条独立 key | `tn('toast.children', n)` ✗ | `tn('toast.child.singular', 1)` + `tn('toast.child.plural', n)` |
| `{count}` 必须出现在文案中 | 翻译丢 `{count}` | 翻译保留 `{count}` |

## 5.3 R-13 迁移到 ICU

R-13 引入 vue-i18n + ICU 后，复数语法切换为
`{count, plural, one {1 item} other {# items}}`；`tn` 函数签名保持不变，
切换时只迁移 JSON、删除 `pluralizeEnglishTail`，组件层无需改。

---

# 6. 占位符

## 6.1 语法

统一 `{name}` 单花括号风格（vue-i18n 默认；ICU 复数等价 `{#}` 形式）。

| 形式 | 说明 |
| --- | --- |
| `{item}` | 单变量替换 |
| `{count}` | `tn()` 自动注入的数量 |
| `{p1} - {p2}` | 同一文案多变量 |
| `\{literal\}` | **不支持转义**——文案需要字面量花括号时改用全角 `｛｝` |

## 6.2 双语保持同名

| key | zh-CN | en-US |
| --- | --- | --- |
| `toast.itemGet` | `收到 {item}，已放进背包` | `Got {item}. Check your bag.` |
| `toast.kcUp` | `KC「{kcName}」掌握度提升` | `KC "{kcName}" mastery is up.` |

**硬约束**：同一 key 的占位符集合（变量名）必须完全一致。CI（见 § 13.2）扫描 JSON 双向 diff，
任一边新增 / 删除占位符就 fail。

## 6.3 标准占位符词典

占位符仅是"插槽"——类型 / 取值范围必须在调用处 JSDoc 或下表说明，否则容易导致翻译错位
（例：`{due}` 是 ISO 字符串还是已格式化的 "周日 22:00"？）。

| 占位符 | 类型 | 含义 | 出现 key 前缀 |
| --- | --- | --- | --- |
| `{item}` | string | 已本地化物品名 | `toast.*` |
| `{count}` | number | 数量（≥ 0） | `tn()` 自动注入 |
| `{kcName}` | string | KC 中文名 | `toast.kc*`, `tpl.*` |
| `{npc}` | string | NPC 显示名 | `toast.gift*` |
| `{className}` | string | 班级名 | `toast.classroom*`, `tpl.classroom.welcome` |
| `{areaName}` | string | 地图区域名 | `toast.unlockArea` |
| `{festivalName}` | string | 节日名 | `toast.festival*` |
| `{chapter}` | string | 章节名 | `tpl.classroom.read` |
| `{pageStart} / {pageEnd}` | number | 页码 | `tpl.classroom.read` |
| `{due}` | string（已格式化） | 截止时间 | `tpl.classroom.*` |
| `{focusKc}` | string | 本周 KC | `tpl.classroom.weekly` |
| `{pacePerDay}` | number | 推荐节奏 | `tpl.classroom.weekly` |
| `{field}` | string | 表单字段名 | `error.validation.*` |
| `{title}` | string | 任务标题 | `tpl.classroom.deadline` |

---

# 7. 时间日期

## 7.1 不进 i18n 的部分

绝对时间戳 / 日期格式化交由调用方用 `Intl.DateTimeFormat` 处理，**i18n 文件不存任何
"周一 / Monday" 字面量**——区域偏好（24/12 时制、周首日）和 locale 解耦不清，
且后端推送的 ISO-8601 字符串不应混在 UI 文本里二次解析。

## 7.2 调用约定

```ts
import { useI18n } from '@/composables/useI18n';

const { locale } = useI18n();
const dtFmt = new Intl.DateTimeFormat(locale.value, {
  weekday: 'short',
  hour: '2-digit',
  minute: '2-digit',
});
dtFmt.format(new Date(quest.dueAtIso));
```

把格式化结果作为 `{due}` 传给 `t('tpl.classroom.read', { due: formatted })`。

## 7.3 双语显式差异

| 维度 | zh-CN 偏好 | en-US 偏好 |
| --- | --- | --- |
| 周首日 | 周一 | Sunday |
| 时制 | 24 h（`22:00`） | 12 h（`10 PM`），允许保留 24 h 给 deadline |
| 日期顺序 | 年-月-日 | Month Day, Year |
| 相对时间 | 「3 分钟前」 | `3 min ago` |

---

# 8. 货币：Pythoco

## 8.1 命名硬约束

Pythontopia 内置货币英文名 **Pythoco**（缩写 PYC），中文名 **派币**——**禁止翻译为
"金币 / Coin / Gold / Bitcoin"**。原因：

- 「金币」是 HUD chip 通用标签（`hud.label.coin`），与货币本体名称解耦；
- 「派币 / Pythoco」是教育版的"虚拟教学积分"，不暗示真实货币 / 加密资产，避免合规风险。

## 8.2 单复数

Pythoco 类似 `yen / fish`，作为计量名词**不变形**：0/1/N Pythoco 一律保留原词。
`tn('hud.label.coin', n)` 中文透传，英文 count !== 1 时 JSON 写 plural 形式但**不加 s**
——本规则在 § 13.3 单元测试中显式断言。

## 8.3 货币格式化

`Intl.NumberFormat(locale.value)` 处理千分位（zh-CN `1,234 派币` / en-US `1,234 Pythoco`），
**不要在 JSON 里写死 `,`**。

---

# 9. 不译术语清单

下列术语在 **任何语言** 下都保持原貌（不翻译、不替换、不全角化），CI 会扫描双向 JSON 检查。

| 术语 | 含义 | 备注 |
| --- | --- | --- |
| `KC` | Knowledge Component | 单数复数都用 `KC` |
| `KC-031` | KC 编号 | `KC-001` 至 `KC-065` 都不译 |
| `Pythoco` / `PYC` | 内置货币 | 见 § 8 |
| `Pythontopia` | 产品名 | |
| `Python` | 语言名 | |
| `Phaser` | 游戏引擎 | |
| `Vue` / `Vite` / `Pinia` | 技术栈 | 仅在面向开发者文案出现 |
| `Pyodide` | WASM Python | toast 出现，不译 |
| `for` / `while` / `def` / `class` / `lambda` / `yield` | Python 关键字 | 行内用反引号 |
| `traceback` | Python 报错 | en-US 与 zh-CN 都用原词 |
| `RAG` / `LLM` / `JWT` / `KSP` | 缩略语 | 不展开译 |

**反例**：

| ❌ | 问题 | ✅ |
| --- | --- | --- |
| "知识组件 KC-031" | KC 已是不译术语，前面冗余 | "KC-031" |
| "蟒蛇 3.11" | Python 不译 | "Python 3.11" |
| "派森拓邦" | Pythontopia 不译 | "Pythontopia" |

---

# 10. 性别中立

## 10.1 中文

不用「他 / 她」指代未指明性别的玩家——用「你」直呼或「这位同学」第三人称替代。
NPC `class_president`、`student_anxious`、`merchant` 等人设**不指定性别**，对白统一用
「TA / 这位 / 同学 / 那位」。教师后台描述学生统一用「学生 / 同学」，禁用「男生 / 女生」。

## 10.2 英文

单数 `they/them/their` 作为性别中立代词（APA 7th & Chicago Manual 已采纳）；禁用
`he/she`、`he or she`、`s/he`。NPC 自介绍可用 first-person `I`，不强制声明 pronouns。
称呼玩家直接 `you`，不用 `sir / madam / mr / ms`。

## 10.3 反例

| 反例 | 问题 | 修正 |
| --- | --- | --- |
| "她正在做实验" | 假设性别 | "这位同学正在做实验" |
| "He or she should submit by 22:00" | 冗长 | "They should submit by 22:00" |
| "男同学请走左边" | 性别分组无业务必要 | "请走左边" |

---

# 11. 添加新 key 流程

## 11.1 步骤

```text
1. 在 docs/stardew/07-ui-microcopy.md 对应章节登记 key + zh-CN 文案；
2. 修改 frontend/src/i18n/zh-CN.json，按 § 2 命名规范插入；
3. 同步 frontend/src/i18n/en-US.json，占位符变量名必须一致；
4. 组件层用 const { t } = useI18n() + t('xxx.yyy') 调用，禁止硬编码中文；
5. 触发 npm run typecheck + npm run test 至少跑 useI18n + microcopy 两个 spec；
6. PR 描述里附"i18n key diff"段落，列出新增 / 修改 / 删除清单；
7. UX 写作 + 翻译同事 review 通过后 merge。
```

## 11.2 反流程（禁止）

| 反流程 | 后果 |
| --- | --- |
| 只改 zh-CN.json 不动 en-US.json | CI fail（双向 key diff 不一致） |
| 直接 import baseSetLocale 切语言 | localeRef 不更新，组件不重渲染 |
| .vue 模板硬编码中文字符串 | `lint:i18n` 扫描中文字面量直接 fail |
| `t()` 结果再做 `.replace` 替换占位符 | 应通过 `t(key, params)` 完成 |
| 复制 zh 文案到 en 占位 | 校验脚本检测到 CJK 字符出现在 en-US.json fail |

## 11.3 删除 key 流程

- grep `t('key.path')` 确认无引用 → 双向 JSON 同步删除 → `07-ui-microcopy.md` 删行 + changelog 注明 PR 链接。

---

# 12. 翻译 review checklist

翻译外包提交 en-US.json diff 后，UX 写作 + 工程师按下表逐项打勾：

| # | 检查项 | 通过条件 |
| --- | --- | --- |
| 1 | 占位符一致 | 与 zh-CN 同 key 的 `{name}` 集合完全相同 |
| 2 | 字符长度 | 不超 § 4.2 表格上限 |
| 3 | 复数 | 文案以可数单数名词收尾（若被 `tn` 调用） |
| 4 | 不译术语 | § 9 表格中的术语原样出现，未误翻 |
| 5 | 货币 | "Pythoco" 拼写正确，不写 "Pythocos" |
| 6 | 性别中立 | 无 `he` / `she` / `he or she` |
| 7 | 标点 | 末尾用半角 `.`，问句 `?`，感叹仅限节日 / 成就 |
| 8 | 大小写 | 句首大写；产品名 `Pythontopia` 不全大写 |
| 9 | 禁用词 | § 3 表中的禁用词及其英文等价不出现 |
| 10 | 语气 | 通过抽样 5 条 NPC 对白人工朗读，无"客服腔 / 催促腔" |

通过率 < 100% 直接打回，不允许"小问题先合，后续修"。

---

# 13. 工具链

## 13.1 useI18n composable 用法

```ts
import { useI18n } from '@/composables/useI18n';

const { t, tn, locale, setLocale } = useI18n();

// 基础翻译
t('hud.btn.confirm');                          // -> '确认'

// 占位符替换
t('toast.itemGet', { item: '种子袋' });        // -> '收到 种子袋，已放进背包'

// 复数（zh-CN 透传，en-US 末尾 +s）
tn('toast.itemGet', 5, { item: 'seed' });      // en-US -> 'Got seed. Check your bags.'

// 切语言（同步触发所有组件重渲染）
setLocale('en-US');

// 响应式订阅 locale
import { watch } from 'vue';
watch(locale, (next) => console.log('locale changed to', next));
```

## 13.2 校验脚本（计划 R-12 末尾交付）

| 脚本 | 触发 | 作用 |
| --- | --- | --- |
| `scripts/check-i18n-keys.mjs` | pre-commit + CI | zh-CN ↔ en-US key 集合 diff = ∅ |
| `scripts/check-i18n-placeholders.mjs` | 同上 | 每 key 占位符集合一致 |
| `scripts/check-i18n-hardcoded.mjs` | `npm run lint` | 扫描 `*.vue / *.ts` CJK 硬编码 |
| `scripts/check-i18n-banned.mjs` | CI | 禁用词扫描（笨 / 傻 / idiot 等） |

> 校验脚本目前**尚未**全部落地；本表是 R-12 收尾前的工作清单，先做前两个最关键的。

## 13.3 单元测试

| 文件 | 覆盖范围 |
| --- | --- |
| `frontend/tests/composables/useI18n.spec.ts` | t / tn / 插值 / 复数 / 响应式 / 单例（12 用例） |
| `frontend/tests/i18n/microcopy.spec.ts`（计划） | 抽样 5 按钮 + 5 toast 文案断言 + 禁用词扫描 |
| `frontend/tests/i18n/currency.spec.ts`（计划） | Pythoco 不加 s、千分位格式 |

## 13.4 IDE 与本地体验

VSCode 装 `i18n Ally` 插件：悬停 key 自动显示 zh-CN / en-US 双语对照；推荐打开
`Show key as inlay hint` 让 `t('hud.btn.confirm')` 旁直接渲染「确认」。ESLint 规则
（计划）：禁止 `.vue` template 包含 ≥ 2 个连续 CJK 字符的字面量。

---

# 14. 测试覆盖与验收门槛

## 14.1 当前覆盖

`useI18n.ts` 行覆盖率 100% / 分支 87.8%（仅 `interpolate` 缺参分支未触发）；
`i18n/index.ts` 行 64.4% / 分支 84.6%（`tList / hasKey / listKeys` 待补）。

## 14.2 验收门槛

| # | 门槛 | 检查方式 |
| --- | --- | --- |
| 1 | `npm run typecheck` 通过 | `vue-tsc --noEmit` |
| 2 | `useI18n.spec.ts` 全绿 | `vitest run tests/composables/useI18n.spec.ts` |
| 3 | `useI18n.ts` 行覆盖率 ≥ 95% | `vitest run --coverage` |
| 4 | i18n JSON key 集合双语完全一致 | `scripts/check-i18n-keys.mjs`（CI） |
| 5 | 占位符变量集合双语完全一致 | `scripts/check-i18n-placeholders.mjs`（CI） |
| 6 | 禁用词零出现 | `scripts/check-i18n-banned.mjs`（CI） |
| 7 | 任意修改 `frontend/src/i18n/*.json` 必须同步 `07-ui-microcopy.md` | PR review 人工 + CI 抽样 |

## 14.3 升级路径（R-13）

| 当前 R-12 | R-13 切换目标 | 影响 |
| --- | --- | --- |
| 自研 `useI18n` + 简化复数 | `vue-i18n` + ICU | 文件结构改，组件 API 不变 |
| `tn('xxx', n)` | `t('xxx', { n })` + ICU plural 块 | 调用点一次性 codemod |
| 扁平化 JSON + 模块级 ref | vue-i18n 嵌套 + 内置 reactive | 删 `flatten()` / `localeRef` |

R-13 切换 PR 必须保证：第 1–12 章规则**全部不变**；只动 13.1 与 14.1；业务组件**禁止改动**，便于回滚。

---

-- 子代理（i18n 工程师）生成于 2026-05-24 (UTC+8)
