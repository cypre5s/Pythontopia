# Pythontopia 角色素材来源 (characters/)

本目录收录 4 套互补的像素角色 sprite，用于 Pythontopia 像素小镇的学生主角与各类 NPC。
所有素材均来自 OpenGameArt，license 为 CC0 / CC-BY 3.0 / CC-BY 4.0 / OGA-BY 3.0，符合 Pythontopia
"教育优先、可商用 demo、保留 attribution" 的合规要求。**未引入任何星露谷官方素材。**

> 已有的 `kenney_characters/` (709 文件，CC0) 与 `buildings_npc/` (292 文件) 中的角色资源可直接复用，
> 本目录不重复下载 Kenney 系列素材。详见 `MANIFEST.md` 的 "已有可复用" 段。

---

## 1. tiny16_sharm/ — Tiny 16: Basic

- **作者:** Lanea Zimmerman (Sharm)
- **OGA 页面:** <https://opengameart.org/content/tiny-16-basic>
- **License:** CC-BY 4.0 **/** CC-BY 3.0 **/** OGA-BY 3.0 (多 license,任选其一,Pythontopia 采用 CC-BY 4.0)
- **归属要求:** "Please credit Lanea Zimmerman. The anti-DRM clause of CC-BY 3.0 is waved."
- **发布日期:** 2013-05-30
- **风格:** 16x16 像素,Dawnbringer 16 色板,经典俯视 RPG 风
- **本地文件:**
  - `characters_1.png` (9.9 KB,192×128) — 多角色 sprite sheet,含 4 方向 2 帧 walk
  - `things_0.png` (6.9 KB) — 杂物/道具
  - `dead_1.png` (2.3 KB) — 死亡/睡眠姿势
  - `basictiles_2.png` (7.5 KB) — 基础地形 tile (备用)
  - `fontlarge.png` (5.3 KB) — 像素字体 (备用)

## 2. cabbit_24x32/ — 24x32 Characters with Faces (Big Pack)

- **作者:** Svetlana Kushnariova (Cabbit), 联系方式 lana-chan@yandex.ru
- **OGA 页面:** <https://opengameart.org/content/24x32-characters-with-faces-big-pack>
- **License:** CC-BY 3.0 **/** OGA-BY 3.0
- **归属要求:** "credit me as Svetlana Kushnariova and give my email address, lana-chan@yandex.ru"
- **发布日期:** 2014-06-03
- **风格:** 24x32 像素角色 + 16x16 地形兼容,角色含表情头像,3 帧 walk × 4 方向
- **本地文件总数:** 53 个 (10 Heroes + 36 NPC + 7 Monsters)
- **子目录:**
  - `Heroes/` — Fighter / Healer / Mage / Ranger 男女各 (10 文件)
  - `NPC/` — Aristocrate, Bard, Clown, Cultist, Dancer, King, Pirate, Prince/Princess, Townfolk (adult/child/old, 男女), Snow-M (冬装人), Mask-M (节日面具), NPC_F-Amanda 等 (36 文件)
  - `Monsters/` — Flower, GrassSpirit, Mermaid, Mushroom, Poprigunchik, Seed, Shrump (7 文件,其中 Mermaid 为 .bmp)

## 3. fleurman_tiny_cc0/ — Tiny Characters Set (32 chars)

- **作者:** Fleurman (基于 GrafxKid 的 RPG character sprites)
- **OGA 页面:** <https://opengameart.org/content/tiny-characters-set>
- **License:** **CC0 1.0** (公有领域,完全自由使用,无强制 attribution)
- **发布日期:** 2017-02-16
- **风格:** 16x16 chibi 像素,Pokemon/GameBoy 复古风,4 方向 walk
- **作者备注:** "12 males and 12 females but some of them are pretty neutral, so it's up to you"
- **本地文件:**
  - `All.png` (7.9 KB,288×204) — 32 角色合集
  - `Females/F_01.png` ~ `F_12.png` + `Females.png` (合集)
  - `Males/M_01.png` ~ `M_12.png` + `Males.png` (合集)

## 4. grafxkid_rpg_cc0/ — RPG character sprites (base)

- **作者:** GrafxKid
- **OGA 页面:** <https://opengameart.org/content/rpg-character-sprites>
- **License:** **CC0 1.0** (公有领域)
- **发布日期:** 2016-10-27
- **风格:** 16x16 chibi 模板角色 (短发/长发/裙装/模板),Fleurman 包的原始基础
- **本地文件:**
  - `RPG_assets.png` (1.9 KB,128×128) — 模板 sprite sheet

---

## License 兼容性总结

| 包 | License | Attribution 必填 | Pythontopia 用途 |
|----|---------|------------------|------------------|
| tiny16_sharm | CC-BY 4.0 | 是 (Lanea Zimmerman) | 16x16 经典风,辅助 NPC |
| cabbit_24x32 | CC-BY 3.0 | 是 (Svetlana Kushnariova) | 主力 NPC 库 (变体最丰富) |
| fleurman_tiny_cc0 | CC0 | 否 (推荐感谢) | 学生主角候选 (无 attribution 负担) |
| grafxkid_rpg_cc0 | CC0 | 否 | 模板/扩展参考 |

游戏发布时,前 2 套需在 `docs/credits.md` 注明作者;后 2 套虽 CC0 但建议同样致谢。

## 下载来源代理

下载时通过 `HTTP_PROXY=http://127.0.0.1:7892`，原始 URL 已记录在各小节。所有 PNG 经过 `file(1)` 验证为合法 PNG/8-bit RGB(A)。
