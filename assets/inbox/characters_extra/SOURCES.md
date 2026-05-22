# Pythontopia 角色素材来源 (characters_extra/)

本目录由 R-100 阶段一素材爬取子代理 **#02 OGA-Characters-Walk-Idle** 生成，收录 **7 套** 互补的 **俯视角 / LPC 兼容 / walk + idle 四方向** 像素角色 spritesheet，用于补全 Pythontopia 像素小镇中**学生（多发型/肤色/校服）、教师、商人 NPC、矿洞冒险者**形象。

> **本目录与现有 `characters/` 的关系：** `characters/` 已收录 5 套 16×16 / 24×32 通用 RPG 角色，但缺少 **LPC 64×64 角色（教师候选）与 walk+idle 全套四方向动画系统**。`characters_extra/` 补上这一空白，并额外提供 Pepper&Carrot 名字 NPC、isaiah658 现代风学生、Sharm 扩展 / 3-4 视角变体。
>
> **未与 `kenney_characters/` 重复：** 本目录全部来源于 OGA，与 Kenney roguelike-characters / toon-characters 无内容重叠。

所有素材均来自 OpenGameArt.org，license 全部位于 Pythontopia R-100 白名单 (CC0 / CC-BY 4.0 / CC-BY 3.0 / OGA-BY 3.0)。本目录**未引入任何 CC-BY-SA-only 或 GPL-only 资产**，与现有 `characters/` 的合规策略一致。

---

## 1. lpc_medieval_fantasy/ — LPC Medieval Fantasy Character Sprites (wulax)

- **作者:** Johannes Sjölund ("wulax")（基于 Stephen Challener "Redshrike" 的 LPC 风格）
- **OGA 页面:** <https://opengameart.org/content/lpc-medieval-fantasy-character-sprites>
- **License:** CC-BY-SA 3.0 / GPL 3.0 / **OGA-BY 3.0** — Pythontopia 选 **OGA-BY 3.0** (非 SA)
- **风格:** **LPC 64×64 模块化套件**（BODY + HEAD + TORSO + LEGS + FEET + HANDS + BELT + WEAPON 分层）
- **核心动画:** walk + idle（9 帧 × 4 方向）+ hurt + slash + spellcast + thrust + bow
- **核心子目录:**
  - `lpc_entry/png/walkcycle/` — 576×256 px walk+idle (col 0=idle, cols 1–8=walk; 4 方向)
  - `lpc_entry/png/{slash,spellcast,thrust,bow,hurt}/` — 其他动画
  - `expansion_pack/png/{64x64,192x192}/` — bow 扩展、slash 长剑、thrust 长矛、combat_dummy 死亡动画
- **Pythontopia 用途:** 学生（裤衫层）/ 教师（robe 层）/ 商人 NPC（皮甲 + 帽）/ 矿洞冒险者（plate / chain 甲套）/ 骷髅敌人

## 2. tiny16_expanded_sharm/ — Tiny 16: Expanded Character Sprites

- **作者:** Lanea Zimmerman ("Sharm")
- **OGA 页面:** <https://opengameart.org/content/tiny-16-expanded-character-sprites>
- **License:** CC-BY 3.0 / **OGA-BY 3.0**
- **风格:** 16×16 像素，Tiny16 系列扩展（男 / 女 / 骷髅 / 裸体基底）
- **本地文件:** 6 个 PNG，含 male / female / skeleton / nude base 共约 9 个角色变体
- **Pythontopia 用途:** 学生男女多变体 / 肤色基底（裸体基底用于重新上色） / Bug 矿洞游荡骷髅

## 3. sharm_overworld_34/ — A Load of Overworld 3/4 RPG Sprites

- **作者:** Lanea Zimmerman ("Sharm")
- **OGA 页面:** <https://opengameart.org/content/a-load-of-overworld-34-rpg-sprites>
- **License:** CC-BY 3.0 / **OGA-BY 3.0**
- **风格:** 3/4 俯视 RPG，约 32×48 cell，单张大图含多个 NPC
- **本地文件:** `batch_one.png` (486×706, RGB 透明键色)
- **Pythontopia 用途:** 广场杂兵 NPC / 班级背景人物 / 备用商人 NPC

## 4. peppercarrot_24x32/ — 24x32 Pepper&Carrot Characters

- **作者:** Svetlana Kushnariova ("Cabbit") (像素改编) + David Revoy (原始角色设计)
- **OGA 页面:** <https://opengameart.org/content/24x32-peppercarrot-characters>
- **Pepper&Carrot 原始项目:** <https://www.peppercarrot.com/>
- **License:** **CC-BY 4.0** / CC-BY 3.0
- **风格:** 24×32 像素，3 cols × 4 rows walk + idle × 4 方向
- **本地文件:** Pepper / Carrot / Coriander / Saffron / Shichimi 共 5 个 PNG
- **Pythontopia 用途:** **客串 NPC**（魔法屋 / 图书馆门口），不做主角

## 5. isaiah658_pack1/ — isaiah658's Pixel Pack #1（仅 Characters 子集）

- **作者:** isaiah658
- **OGA 页面:** <https://opengameart.org/content/isaiah658s-pixel-pack-1>
- **License:** **CC0 1.0** (公有领域)
- **风格:** 16×16 像素，3 cols × 4 rows walk + idle × 4 方向，现代休闲服角色
- **本地文件:** `Characters/` 子目录共 13 个 PNG（含 child × 3、named NPC × 5、tuxedo-man、cloaked-figure、green-cap 等）+ 原始 `credit.txt`
- **Pythontopia 用途:** 现代风学生 NPC / 名字同学 / 西装教师 (tuxedo-man) / 神秘披风商人 / 路人
- **未包含:** 原 ZIP 的 Doors / Furniture / Fences / Food / Buildings（属其他子代理领域）

## 6. four_lpc_entries/ — Four Characters: My LPC Entries

- **作者:** Stephen Challener ("Redshrike") + Zi Ye (Sinbad 设计)
- **OGA 页面:** <https://opengameart.org/content/four-characters-my-lpc-entries>
- **License:** **CC-BY 3.0** / CC-BY-SA 3.0 / OGA-BY 3.0 — Pythontopia 选 **CC-BY 3.0** (非 SA)
- **风格:** LPC 64×64，含 walk + idle（9 帧 × 4 方向）+ spellcast + slash + fall
- **本地文件:** Mage (walk / cast / fall 共 3 PNG) + Baldric 骑士 (walk / slash / spell / fall 共 4 PNG) + unfinishedchars1.PNG
- **Pythontopia 用途:** **教师**（Mage 整套 walk + spellcast 可演绎"上课施法"）/ **矿洞冒险者**（Baldric 骑士整套 walk + slash 可演绎打 Bug）

## 7. lpc_sara/ — LPC Sara（OGA 吉祥物）

- **作者:** Stephen Challener ("Redshrike") + William.Thompsonj + 原 Sara 角色 Mandi Paugh (OGA 吉祥物)
- **OGA 页面:** <https://opengameart.org/content/lpc-sara>
- **License:** **CC-BY 4.0** / CC-BY 3.0 / GPL 3.0 / GPL 2.0 / OGA-BY 3.0 — Pythontopia 选 **CC-BY 4.0**
- **特殊条款:** 必须在致谢中包含 OGA 主页链接 `https://opengameart.org/`
- **风格:** LPC 64×64 完整通用 sheet (832×1344, 13 cols × 21 rows)，自带分层 (body + hair × 3 + shirt + leggings + shoes)
- **本地文件:** SaraFullSheet.png + 6 个分层 PNG + 原始 `credit.txt`
- **Pythontopia 用途:** **女主角 / 女学生主角**（重新上色制服）/ 客串巫师 NPC / 备用女教师 (加 robe 覆盖)

---

## License 兼容性总结

| 包 | License | R-100 严格符合 | Attribution 必填 | 主要角色 | walk + idle 4 方向 |
|----|---------|----------------|-------------------|----------|---------------------|
| `lpc_medieval_fantasy` | OGA-BY 3.0 | ✅ | 是 (Johannes Sjölund + Redshrike) | LPC 模块化 (knight, mage, archer, monk, skeleton...) | ✅ |
| `tiny16_expanded_sharm` | CC-BY 3.0 / OGA-BY 3.0 | ✅ | 是 (Lanea Zimmerman) | male × 5 / female × 3.5 / skeleton × 3 / nude base × 2 | ✅ |
| `sharm_overworld_34` | CC-BY 3.0 / OGA-BY 3.0 | ✅ | 是 (Lanea Zimmerman) | 大杂烩 ~15 + NPC | ✅ |
| `peppercarrot_24x32` | CC-BY 4.0 / CC-BY 3.0 | ✅ | 是 (Cabbit + David Revoy) | Pepper / Carrot / Coriander / Saffron / Shichimi | ✅ |
| `isaiah658_pack1` | CC0 | ✅ (最严) | 否（推荐感谢） | 13 个现代休闲角色 | ✅ |
| `four_lpc_entries` | CC-BY 3.0 / OGA-BY 3.0 | ✅ | 是 (Redshrike + Zi Ye for Sinbad) | Mage / Baldric | ✅ |
| `lpc_sara` | CC-BY 4.0 / CC-BY 3.0 / OGA-BY 3.0 | ✅ | 是 (Redshrike + W.Thompsonj + Paugh + OGA 主页链接) | Sara 女主角 (完整分层) | ✅ |

游戏发布时必须在 `docs/credits.md` 致谢：
- Johannes Sjölund ("wulax")
- Lanea Zimmerman ("Sharm" / "Antifarea")
- Svetlana Kushnariova ("Cabbit") 与 David Revoy
- isaiah658（虽然 CC0 不强制，仍记录）
- Stephen Challener ("Redshrike")
- Zi Ye (for Sinbad design, if Sinbad asset is later added)
- William.Thompsonj
- Mandi Paugh (Sara 原角色)
- OpenGameArt.org（主页链接 `https://opengameart.org/`）

---

## 排除清单（已审阅但未下载或下载后退出）

| 候选页面 | 排除原因 |
|----------|----------|
| `liberated-pixel-cup-lpc-base-assets-sprites-map-tiles` | License 只有 CC-BY-SA 3.0 + GPL 3.0（SA 传染） |
| `lpc-character-bases` | 同上，CC-BY-SA 3.0 + GPL 3.0 |
| `lpc-revised-character-basics` | ZIP 83.5 MB，**超出单源 30 MB 上限**；改用 wulax LPC 套件替代 |
| `lpc-sage` / `lpc-gentleman` / `lpc-hakuna-matetee` | License 只有 CC-BY-SA 3.0/4.0 + GPL 3.0 |
| `lpc-in-battle-rpg-sprites` | License 只有 CC-BY-SA 3.0 + GPL 3.0 |
| `lpc-submissions-merged` | License 只有 CC-BY-SA 3.0 + GPL + CC-BY 3.0（混合 SA） |
| `24x32-characters-with-faces-big-pack` | License 合规但**已在 `characters/cabbit_24x32/` 中**（去重） |
| `animated-top-down-survivor-player` | 13.7 MB 持枪生存者，主题（射击 / 丧尸）与 Pythontopia 教育氛围不符 |
| `opp2017-sprites-characters-objects-effects` | 实际为**侧视角平台跳跃**，不满足"俯视角 RPG"硬约束 |
| `lpc-teacher-shaman` / `lpc-extended-male-base` / `lpc-castle-elements` | 任务指南给出的 URL 在 OGA 已 404 |

## 下载来源代理

下载时通过 `HTTP_PROXY=http://127.0.0.1:7892`，原始 URL 在各 `LICENSE.md` 与 `README_SOURCE.md` 中分别记录。所有 PNG 经 Pillow `Image.open()` + `Image.verify()` 双重验证（详见 `MANIFEST.md`）。OPP2017 包内 3 个伪 PNG (`spr_m_aviator{1,2,3}.png` 实为 HTML 404 页) 在审阅阶段即已发现并随该包整体剔除。
