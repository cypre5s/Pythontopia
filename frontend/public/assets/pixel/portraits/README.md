# Pythontopia NPC Portraits

20 个 NPC 的对话立绘。本目录由 W2-NPC 子代理在 R-M3 阶段生成，规格、用途、来源详见下表。

## 文件总览

- 文件数：20（每个 NPC 一张同名 PNG，命名 `portrait_<npc_key>.png`）
- 总文件大小预计 <500KB
- 与 `data/npcs/<npc_key>/meta.json` 中的 `portrait` 字段一一对应

## 素材来源

所有素材来自 `assets/inbox/characters_extra/`：

| 来源包 | 作者 | License | 文件数（已用） |
| --- | --- | --- | --- |
| `isaiah658_pack1/Characters/` | isaiah658 | **CC0 1.0**（公有领域） | 13 |
| `peppercarrot_24x32/` | Svetlana Kushnariova ("Cabbit") + David Revoy | CC-BY 4.0 / CC-BY 3.0 | 5 |
| **合计独立素材** |  |  | **18** |
| **NPC 数** |  |  | **20** |
| **必要复用** |  |  | **2**（详见下表） |

合规要点：

- isaiah658（CC0）无任何署名要求，可自由使用。
- peppercarrot CC-BY 4.0：需在游戏致谢页注明作者与原 OGA 页面。详见 `assets/inbox/characters_extra/peppercarrot_24x32/README_SOURCE.md`。
- 本批不引入任何 CC-BY-SA-only / GPL-only 资产，与 R-100 白名单一致。

## 文件格式提示

- **isaiah658 角色** 是单文件单角色（48×N 像素，正面 idle 帧 + 行走帧上下排），可直接当头像渲染，建议在 UI 层截取上半身或缩放到 64×64。
- **peppercarrot 角色** 是 spritesheet（约 153–187×128 像素，3 列 × 4 行 × 4 方向 walk+idle）。当前直接以原 spritesheet 拷贝过来，**对话面板渲染时须裁取首列首行（即朝下 idle 帧，24×32）** 作为头像，否则会显示出 12 个小人。W2-DIALOG 子代理将处理这一裁剪逻辑。

## NPC → Portrait 完整映射表

### 教育核心 NPC（6，全部不同 portrait）

| NPC key | 中文名 | portrait 文件 | 源文件 | 类型 |
| --- | --- | --- | --- | --- |
| `mayor_py` | 镇长 Py | `portrait_mayor_py.png` | `isaiah658_pack1/Characters/character-1.png` | 单帧 (48×72) |
| `farmer_kc` | 田老 | `portrait_farmer_kc.png` | `isaiah658_pack1/Characters/noah.png` | 单帧 (48×72) |
| `foreman_bug` | 矿头 Bug | `portrait_foreman_bug.png` | `isaiah658_pack1/Characters/green-cap-character.png` | 单帧 (48×72) |
| `librarian_doc` | 馆长 Doc | `portrait_librarian_doc.png` | `isaiah658_pack1/Characters/cloaked-figure.png` | 单帧 (48×96) |
| `coach_q` | 教务 Q | `portrait_coach_q.png` | `isaiah658_pack1/Characters/tuxedo-man.png` | 单帧 (48×72) |
| `sage_review` | 复盘师 Sage | `portrait_sage_review.png` | `peppercarrot_24x32/pepper.png` | spritesheet (180×128) **需裁剪** |

### 学生 NPC（6，全部不同 portrait）

| NPC key | 中文名 | portrait 文件 | 源文件 | 类型 |
| --- | --- | --- | --- | --- |
| `student_diligent` | 勤勉小柒 | `portrait_student_diligent.png` | `isaiah658_pack1/Characters/luke.png` | 单帧 (48×64) |
| `student_procrast` | 拖延小迟 | `portrait_student_procrast.png` | `isaiah658_pack1/Characters/zack.png` | 单帧 (48×80) |
| `student_anxious` | 焦虑小安 | `portrait_student_anxious.png` | `isaiah658_pack1/Characters/salley.png` | 单帧 (48×64) |
| `student_late_starter` | 后赶小赶 | `portrait_student_late_starter.png` | `isaiah658_pack1/Characters/kaylee.png` | 单帧 (48×76) |
| `student_top` | 拔尖小尖 | `portrait_student_top.png` | `peppercarrot_24x32/coriander.png` | spritesheet (187×128) **需裁剪** |
| `student_hobby` | 兴趣小好 | `portrait_student_hobby.png` | `peppercarrot_24x32/shichimi.png` | spritesheet (153×128) **需裁剪** |

### 生活 NPC（8，含 2 个必要复用）

| NPC key | 中文名 | portrait 文件 | 源文件 | 类型 / 复用说明 |
| --- | --- | --- | --- | --- |
| `craftsman` | 工长 | `portrait_craftsman.png` | `isaiah658_pack1/Characters/luke-swimming-trunks.png` | 单帧 (48×64) |
| `class_president` | 班长 | `portrait_class_president.png` | `isaiah658_pack1/Characters/tuxedo-man.png` | 单帧 (48×72) **复用 coach_q** |
| `merchant` | 商人 | `portrait_merchant.png` | `peppercarrot_24x32/saffron.png` | spritesheet (163×128) **需裁剪** |
| `nurse` | 医务 | `portrait_nurse.png` | `isaiah658_pack1/Characters/child-1.png` | 单帧 (48×64) |
| `postman` | 邮差 | `portrait_postman.png` | `isaiah658_pack1/Characters/child-2.png` | 单帧 (48×64) |
| `gardener` | 园丁 | `portrait_gardener.png` | `peppercarrot_24x32/carrot.png` | spritesheet (153×128) **需裁剪** |
| `chef` | 厨师 | `portrait_chef.png` | `isaiah658_pack1/Characters/child-3.png` | 单帧 (48×64) |
| `guard` | 守门 | `portrait_guard.png` | `isaiah658_pack1/Characters/cloaked-figure.png` | 单帧 (48×96) **复用 librarian_doc** |

### 复用说明

| 复用源 | NPC A（首要） | NPC B（复用） | 区分方法 |
| --- | --- | --- | --- |
| `tuxedo-man.png` | `coach_q`（教务 Q，运动 + 西装气质） | `class_president`（班长，同样严肃形象） | 对话面板下方挂不同名字标签即可区分 |
| `cloaked-figure.png` | `librarian_doc`（馆长，神秘学者） | `guard`（守门，斗篷似制服） | 同上，靠名字 + 头像背景色差异 |

W2-DIALOG 子代理在渲染时可在头像下显示 NPC 名 + 角色标签（如 `coach_q` 用蓝色边框、`class_president` 用红色边框）来强化区分。

## 后续 TODO

- [ ] **peppercarrot spritesheet 自动裁剪**：W2-DIALOG 在对话面板渲染时取 frame (0,0)（即首列首行 24×32），缩放至 64×64。当前 PNG 直接展示会显示 12 帧，需 UI 层处理。
- [ ] **isaiah658 多帧角色裁剪**：部分文件高于 64 像素（含行走帧），可只取顶部 48×48 作为头像。
- [ ] **R-M3 后期增补**：如有更高质量 portrait（例如 lpc_sara 渲染单帧、four_lpc_entries 静态半身），可优先替换 `coach_q`、`class_president`、`guard` 这三个复用项。
- [ ] **致谢页更新**：在游戏 Credits 页（`frontend/src/pages/CreditsPage.vue`）已有素材致谢小节加入：isaiah658（CC0，无需署名但建议致谢）+ Pepper&Carrot pixel adaptation（CC-BY 4.0，必须署名作者 + OGA 链接）。
