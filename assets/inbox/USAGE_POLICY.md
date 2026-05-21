# Pythontopia 素材使用原则（USAGE POLICY）

> 维护人: R-100 子代理 #10（license 审计）
> 生效日期: 2026-05-21
> 适用范围: `/home/cypress/Pythontopia/assets/` 全部子目录（含 inbox/、待整理与已归一化素材）
> 上位文档: `AGENTS.md` "禁止事项" + Pythontopia 项目硬约束

本文件是 Pythontopia 素材采购、入库、引用、再分发的**强制规则**。每位维护者（人类或 Agent）在新增 / 迁移 / 删除素材前必须先读此文件。配套审计报告见同目录 `LICENSE_AUDIT.md`。

---

## 1. 协议允许清单（白名单）

只有满足以下条件之一的素材**才允许**入库：

| 协议 | 缩写 | 商用 | 修改 | 再分发 | 署名 | 传染性 |
| --- | --- | :---: | :---: | :---: | --- | :---: |
| Creative Commons Zero 1.0 Universal | CC0 1.0 | ✅ | ✅ | ✅ | 推荐 | ❌ |
| Creative Commons Attribution 3.0 | CC-BY 3.0 | ✅ | ✅ | ✅ | **强制** | ❌ |
| Creative Commons Attribution 4.0 | CC-BY 4.0 | ✅ | ✅ | ✅ | **强制** | ❌ |
| SIL Open Font License 1.1 | OFL-1.1（仅限字体） | ✅ | ✅ | ✅ | **强制 + 命名约束** | 衍生字体需同协议 |
| OGA-BY 3.0（仅当与 CC-BY 同时提供） | OGA-BY 3.0 | ✅ | ✅ | ✅ | **强制** | ❌ |

**只在项目级集体决策接受后**才可纳入：

| 协议 | 状态 | 风险 |
| --- | --- | --- |
| CC-BY-SA 3.0 / 4.0 | ⚠️ 条件接受 | ShareAlike 会传染到最终游戏二进制 |
| GPL 3.0+ | ⚠️ 条件接受 | 会传染到代码层，不建议 |

---

## 2. 协议禁止清单（黑名单）

下列协议或条款的素材**严禁**入库，已入库的必须**立即清理**：

| 协议 / 条款 | 禁止理由 |
| --- | --- |
| 任何 **NonCommercial (NC)** 协议（CC-BY-NC, CC-BY-NC-SA, Cup Nooble Sprout Lands Basic 等专有 NC） | 违反 Pythontopia 比赛 demo 商用化、未来运营 |
| 任何 **NoDerivatives (ND)** 协议（CC-BY-ND, CC-BY-NC-ND） | 阻断教学化裁切、归一化、上色 |
| **No AI Training** / **No AI Use** 条款 | 违反 Pythontopia 多智能体生成、RAG、画像核心架构 |
| **No Redistribution** / **No Resale** 条款 | 仓库公开 / Docker 镜像分发即违规 |
| **来源未知**、**无 license 凭证**、**只有 EULA 没有协议名**的素材 | 默认假设保留全部权利，不可使用 |
| **星露谷物语 (Stardew Valley)** 官方素材（含像素图、音频、字体、地图、NPC） | AGENTS.md 硬约束 |
| **`Notyourbing/StardewValley`** 仓库（含 fork） | AGENTS.md 硬约束 |
| 任何二次反编译、抓取游戏 ROM、APK 提取的素材 | 版权高风险 |
| 任何带 **Adobe Stock / Shutterstock / Envato / Freepik Premium** 水印或 EULA 限制的素材 | 与开源 / CC 协议体系不兼容 |

---

## 3. 入库流程（mandatory）

新素材入库到 `assets/` 任何子目录之前，必须按此顺序完成：

1. **协议核验**
   - 在原始页面读取 license 字段（OpenGameArt: `<field-name-field-art-licenses>`；Kenney: `License.txt`；itch.io: 项目页 License/Description；GitHub: `LICENSE`）
   - 截图或保存 license 字段文本到 `assets/inbox/<pack>/LICENSE.md`
   - 若同时提供多协议，按本文件 §1 白名单选取最宽松、不传染的协议路径
2. **作者与来源记录**
   - 在 `assets/inbox/<pack>/LICENSE.md` 中写明：作者全名、作者主页 URL、原始素材页 URL、协议名、协议链接、下载时间、下载代理
3. **冗余检测**
   - 同一素材包 / 同一 zip 已存在时，复用现有路径，不允许在多个目录重复解压
4. **命名与品牌**
   - 目录名不得包含他人未授权品牌词（如 `sprout_lands`、`stardew`、`harvest_moon`）
   - 推荐使用功能词 (`farm_topdown/`, `pixel_ui/`, `weather_fx/`) 或作者署名 (`kenney_*`, `bluecarrot16_*`, `daneeklu_*`)
5. **校验**
   - PNG 用 `file` 命令确认非伪造扩展名
   - zip 用 `unzip -tq` 校验完整性
   - 关键 spritesheet 用预览图人工或 OCR 比对，避免下错版本
6. **从 inbox 提升到正式目录**
   - 完成 §1-§5 后，从 `assets/inbox/<pack>/` 拷贝（不是移动）到 `assets/<theme>/<pack>/`
   - `inbox/<pack>/LICENSE.md` 与原 zip 必须保留至少 6 个月作为审计证据

---

## 4. 署名（Attribution）规范

### 4.1 全局署名页

`frontend/public/credits/index.html` 与 `docs/credits.md` 必须包含每个 CC-BY / CC-BY-SA / CC-BY 3.0 / CC-BY 4.0 素材的完整署名块：

```text
"<素材名>" by <作者姓名 + OGA/itch 用户名>
<协议名> (<协议 URL>)
Source: <原始素材页 URL>
```

CC0 素材**推荐**纳入致谢页（不强制），格式同上但可省略协议链接。

### 4.2 LPC（CC-BY-SA）专项要求

LPC 素材的 `CREDITS-*.txt` 文件**全文**必须在以下位置可访问：

- 仓库源码: `assets/<theme>/<lpc-pack>/CREDITS-*.txt`（原文件不得改名 / 改内容）
- 运行时: `frontend/public/credits/<lpc-pack>.txt`（构建脚本 `vite.config.ts` 静态拷贝）
- 文档: `docs/credits.md` 中链接到运行时路径

### 4.3 字体（OFL-1.1）专项要求

OFL-1.1 字体（如 Ark Pixel Font / Fusion Pixel Font）：
- 必须保留原始 `OFL.txt` / `LICENSE` 文件
- 衍生字体禁止使用原作者预留的字体名（Reserved Font Names）
- 字体内嵌的 `name` 表禁止删改作者与协议元数据

---

## 5. ShareAlike (SA) 传染处理

LPC 素材（CC-BY-SA 3.0+ / GPL 3.0+）的处理矩阵：

| 场景 | 处置 |
| --- | --- |
| Pythontopia **代码层**（backend/frontend/services） | **完全隔离**：不嵌入 LPC PNG 字节流，仅运行时按路径加载；代码层维持 MIT / Apache-2.0 |
| Pythontopia **静态资源层**（`frontend/public/assets/`） | 标注为 CC-BY-SA 3.0，与代码层分层授权 |
| **打包二进制**（如 docker compose 镜像、桌面 Tauri 包） | 镜像 README 中明确：素材层 CC-BY-SA 3.0，代码层 MIT / Apache-2.0 |
| **AI 生成的派生作品** | 若派生包含 LPC 像素，输出仍按 CC-BY-SA 3.0 发布；不得作为闭源资产销售 |

**禁止行为**：
- 将 LPC 素材嵌入 NPM / PyPI / Maven 软件包的二进制资源段（绕过 SA 标注）
- 用 LPC 素材训练专有图像模型
- 在游戏内 hash / 加盐处理 LPC 素材以掩盖来源

---

## 6. 反幻觉 / 反伪造规范（与 AGENTS.md 一致）

1. 不得伪造课件页码、章节、题目、作者或教材内容
2. 不得伪造素材作者署名（例如把 Kenney 素材标为 "Pythontopia 团队"）
3. 不得在游戏内插入未授权的 IP（星露谷、宝可梦、口袋妖怪、塞尔达、最终幻想 等）
4. 不得在 AI 生成资源 / RAG 引用中虚构来源 URL；引用必须可被 `curl -sI` 验证 HTTP 200
5. 不得在多智能体输出中删除素材的 license 字段或作者字段

---

## 7. 撤回与替换流程

如素材作者撤回协议、协议变更、或发现潜在版权问题，**24 小时内**：

1. 在 `LICENSE_AUDIT.md` 高风险清单顶部添加新条目，附复现链接
2. 将素材从 `frontend/public/assets/`、`backend/resources/`、docker image 全部移除
3. 在游戏内提供占位图（CC0 替代品）并附 "素材替换中" 提示
4. 用同主题的 CC0 / CC-BY 4.0 / OFL-1.1 替代品，更新 `LICENSE_AUDIT.md`
5. 在 `CHANGELOG.md` 中记录变更原因和受影响版本

---

## 8. 黑名单素材记录

如下素材曾出现在 inbox/ 中，但因违反本策略已（或必须）移除，禁止再次入库：

| 路径 | 移除原因 | 替代方案 |
| --- | --- | --- |
| `assets/inbox/sprout_lands/sprout_lands_basic/` | Cup Nooble Non-commercial 专有协议（违反白名单） | Kenney `medieval-rts` + LPC `lpc-farm`（CC-BY 4.0） |
| `assets/inbox/sprout_lands/simple_farm_tiles/farm_tiles.png` | 来源未知，无 license 凭证 | `crops/farming-crops-16x16/Crop_Spritesheet.png`（CC0） |

---

## 9. 决策记录（必读 / 待定）

| ID | 决策点 | 推荐 | 状态 |
| --- | --- | --- | --- |
| D-001 | 是否接受 LPC 系列 CC-BY-SA 传染 | 接受：素材层 CC-BY-SA 3.0 + 代码层 MIT | **待项目主理人确认** |
| D-002 | `sprout_lands_basic/` 处置 | 立即删除 + 加 `.gitignore` | **待执行** |
| D-003 | `simple_farm_tiles/farm_tiles.png` 处置 | 立即删除 | **待执行** |
| D-004 | 冗余 LPC 目录处置 | 删 `sprout_lands/daneeklu_farming/` 与 `opengameart_farming/bluecarrot16/` | **待执行** |
| D-005 | 顶层 LICENSE.md 补齐 | 为 `pixel_ui/`、`sprout_lands/`（清理后）添加 | **待执行** |

决策结果落地后，更新本表"状态"列与 `LICENSE_AUDIT.md` 高风险清单。

---

## 10. 工具与脚本

- 协议字段抓取: `curl -s "$URL" | rg -i 'field-name-field-art-licenses' -A 8`
- 重复检测: `find <dir> -type f -name '*.png' -exec md5sum {} +` 然后用 `sort | uniq -c -w32` 找重复
- 入库审计模板: 复用 `assets/inbox/bgm/LICENSE.md` 表头作为新素材的标准模板
- 全量审计: 每月运行 `assets/inbox/LICENSE_AUDIT.md` 中 "验证元数据" 章节列出的命令，更新表格

---

## 11. 红线（IRON RULE）

如发现以下任一情况，**必须立即停止素材使用并撤回相关代码 / 文档**：

- 任何 NonCommercial / NoDerivatives / No-AI-Training 素材进入仓库
- 任何来源未知素材进入仓库
- 任何星露谷物语官方素材或 Notyourbing/StardewValley 副本进入仓库
- 任何二次反编译素材进入仓库
- LPC 素材未保留 CREDITS 或未做 SA 标注
- 任何素材被冒充为 Pythontopia 团队原创

发现违反红线后的 24 小时内须形成事件报告，写入 `docs/incidents/asset-<日期>.md`，并在 commit message 中标注 `INCIDENT:` 前缀。

---

**生效日期**: 2026-05-21
**下次审计**: 2026-06-21（每月例行）/ 任何素材变更时立即追加
