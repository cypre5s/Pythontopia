# Pythontopia 角色 NPC 映射表

> 用途: 把 `assets/inbox/characters/` 中的 sprite 与 Pythontopia 设计文档要求的角色 / NPC 对齐。
> 同时列出 `assets/inbox/kenney_characters/` 与 `assets/inbox/buildings_npc/` 中可复用的素材。
> **本目录文件未做切割/二次加工,均为原始 sprite sheet,后续 art pipeline 会按需切帧。**

> **R-100 严格模式色块说明:** 表格中"License"列标 ✅ 表示严格符合 R-100 (CC0 / CC-BY 4.0);
> ⚠️ 表示 CC-BY 3.0(无 SA/GPL 传染,但严格 R-100 4.0 派认为有瑕疵,可作为备选)。

## 主角 (Player Avatars,支持 4 方向 walk)

| 角色 | 推荐 sprite | License | 备注 |
|------|-------------|---------|------|
| 学生主角 (男) | `fleurman_tiny_cc0/Males/M_01.png` | ✅ CC0 | 16x16 chibi,4 方向 walk,无 attribution 负担,适合默认头像 |
| 学生主角 (女) | `fleurman_tiny_cc0/Females/F_01.png` | ✅ CC0 | 16x16 chibi,4 方向 walk,与 M_01 同套风格 |
| 学生主角 (中性) | `fleurman_tiny_cc0/Females/F_05.png` 或 `Males/M_05.png` | ✅ CC0 | 作者明确说明 "some are pretty neutral",可任选 |
| 学生主角 (Zelda 风备选) | `armm1998_zelda_cc0/character.png` (第 1-3 行,4 方向 × 多帧) | ✅ CC0 | 272×256 sheet 含 ≥6 角色变体 × 4 方向 walk,适合需要更精细像素感的"探险家"主角 |
| 学生主角 (多发色变体) | `armm1998_zelda_cc0/character.png` (第 4-6 行,深肤/浅发等调色板换装) | ✅ CC0 | 同 sheet 提供肤色 / 发色变体,可作为可访问性主角池 |
| 主角备选 (24x32 男) | `cabbit_24x32/Heroes/Fighter-M-01.png` | ⚠️ CC-BY 3.0 | 24x32,3 帧 × 4 方向,带学习者"探险家"感 |
| 主角备选 (24x32 女) | `cabbit_24x32/Heroes/Ranger-F-01.png` | ⚠️ CC-BY 3.0 | 24x32,适合"户外型学习者"画像 |

## 教师 NPC

| 角色 | 推荐 sprite | License | 备注 |
|------|-------------|---------|------|
| 教师 NPC (中年男) | `cabbit_24x32/Heroes/Mage-M-01.png` | ⚠️ CC-BY 3.0 | 长袍学者形象,适合"算法导论老教授" |
| 教师 NPC (中年女) | `cabbit_24x32/Heroes/Mage-F-01.png` | ⚠️ CC-BY 3.0 | 女性学者,适合"程序设计课主讲" |
| 严格 CC0 备选 (教师男) | `armm1998_zelda_cc0/character.png` (深色西装变体行,4 方向) + Kenney `roguelike-characters/Spritesheet/roguelikeChar_transparent.png` | ✅ CC0 | R-100 严格模式下可用,无 attribution 负担 |
| 严格 CC0 备选 (教师女) | `fleurman_tiny_cc0/Females/F_08.png` (成熟造型) | ✅ CC0 | 16x16 chibi,配合 Kenney Toon 系列作为对话立绘 |
| 普通教师男 | `cabbit_24x32/NPC/Townfolk-Adult-M-002.png` | ⚠️ CC-BY 3.0 | 中年市民,适合"实验课老师" |
| 普通教师女 | `cabbit_24x32/NPC/Townfolk-Adult-F-005.png` | ⚠️ CC-BY 3.0 | 中年女市民 |

## 助教 NPC (TA)

| 角色 | 推荐 sprite | License | 备注 |
|------|-------------|---------|------|
| 助教 (年轻男) | `cabbit_24x32/Heroes/Healer-M-01.png` | ⚠️ CC-BY 3.0 | 年轻治愈者形象,适合"耐心讲解的助教学长" |
| 助教 (年轻女) | `cabbit_24x32/Heroes/Healer-F-01.png` | ⚠️ CC-BY 3.0 | 配对 Healer-M,适合"贴心助教学姐" |
| 严格 CC0 备选 (年轻男) | `fleurman_tiny_cc0/Males/M_03.png` | ✅ CC0 | 16x16,适合小尺寸场景 |
| 严格 CC0 备选 (年轻女) | `fleurman_tiny_cc0/Females/F_03.png` | ✅ CC0 | 16x16 |
| 严格 CC0 备选 (Zelda 风) | `armm1998_zelda_cc0/character.png` 中第 4 行附近浅色调变体 | ✅ CC0 | 同 sheet 不同发色,可作"学姐学长"风格区分 |

## 特色 NPC

| 角色 | 推荐 sprite | License | 备注 |
|------|-------------|---------|------|
| 守门精灵 | `cabbit_24x32/Monsters/GrassSpirit-01.png` | ⚠️ CC-BY 3.0 | 草精灵,适合"小镇入口引导你完成新手任务" |
| 守门精灵 (备选) | `cabbit_24x32/Monsters/Mushroom-01.png` | ⚠️ CC-BY 3.0 | 蘑菇精灵,可爱风 |
| 守门精灵 (CC0 替代) | Kenney `kenney_tilemaps/roguelike-caves-dungeons/` 内 sprite 或 ArMM `NPC_test.png` 内某个小生物变体 | ✅ CC0 | 严格 R-100 闭环时使用 |
| 农场主 | `cabbit_24x32/NPC/Townfolk-Adult-M-001.png` | ⚠️ CC-BY 3.0 | 朴实中年男,对应"知识种子-浇水-施肥"机制的农场主 |
| 农场主 (备选女版) | `cabbit_24x32/NPC/Townfolk-Adult-F-001.png` | ⚠️ CC-BY 3.0 | 备选农场女主人 |
| 农场主 (CC0 替代) | `armm1998_zelda_cc0/NPC_test.png` (4 列 × 8 行 NPC 变体) + `fleurman_tiny_cc0/Males/M_07.png` (壮年男) | ✅ CC0 | 严格 R-100 闭环 |
| 矿洞老人 | `cabbit_24x32/NPC/Townfolk-Old-M-001.png` | ⚠️ CC-BY 3.0 | 老人形象,对应"Bug 矿洞" 入口指引,讲述提交失败的传说 |
| 矿洞老人 (备选) | `cabbit_24x32/NPC/Townfolk-Old-M-002.png` | ⚠️ CC-BY 3.0 | 第二款老人 |
| 矿洞老人 (CC0 替代) | `fleurman_tiny_cc0/Males/M_11.png` (年长造型) | ✅ CC0 | 严格 R-100 闭环 (CC0 老人形象选择有限,可考虑配 Kenney Toon 立绘) |
| 图书管理员 | `cabbit_24x32/NPC/Aristocrate-F-01.png` | ⚠️ CC-BY 3.0 | 优雅女性,适合"课件 RAG 图书馆" 管理员 |
| 图书管理员 (备选) | `cabbit_24x32/NPC/NPC_F-(Amanda).png` | ⚠️ CC-BY 3.0 | 平民版图书馆员 |
| 图书管理员 (CC0 替代) | `fleurman_tiny_cc0/Females/F_06.png` + `armm1998_zelda_cc0/character.png` 学者造型变体 | ✅ CC0 | 严格 R-100 闭环 |
| 工坊主 | `cabbit_24x32/Heroes/Fighter-M-02.png` | ⚠️ CC-BY 3.0 | 体格健壮,适合"代码工坊/CodeLab" 老板,可铸"代码护甲" |
| 工坊主 (备选女版) | `cabbit_24x32/Heroes/Fighter-F-02.png` | ⚠️ CC-BY 3.0 | 女版工坊主 |
| 工坊主 (CC0 替代) | `fleurman_tiny_cc0/Males/M_10.png` (壮汉造型) | ✅ CC0 | 严格 R-100 闭环 |
| 复盘导师 | `cabbit_24x32/NPC/Snow-M-01.png` | ⚠️ CC-BY 3.0 | 冬装智者形象,适合"复盘与再播种" 导师,氛围沉稳 |
| 复盘导师 (备选) | `cabbit_24x32/NPC/Bard-M-01.png` | ⚠️ CC-BY 3.0 | 吟游诗人,适合"用故事帮你复盘" 风格 |
| 复盘导师 (CC0 替代) | `armm1998_zelda_cc0/character.png` 内某老者变体 + Kenney `toon-characters/Robot/` 配立绘 | ✅ CC0 | 严格 R-100 闭环 |

## 备用与扩展 (随机 NPC 群众/学生同学)

| 用途 | sprite 池 | 数量 | License |
|------|-----------|------|---------|
| 同学群众 (高频出现) | `fleurman_tiny_cc0/Females/F_02.png` ~ `F_12.png` + `Males/M_02.png` ~ `M_12.png` | 22 | CC0 |
| 小镇市民 (背景人群) | `cabbit_24x32/NPC/Townfolk-Adult-M-003.png` ~ `M-009.png` | 7 | CC-BY 3.0 |
| 儿童 NPC (附属) | `cabbit_24x32/NPC/Townfolk-Child-F-001.png` `F-002` `M-001` `M-002.png` | 4 | CC-BY 3.0 |
| 特殊事件 NPC | `cabbit_24x32/NPC/Clown_01.png`,`King_01.png`,`Prince-01.png`,`Princess-01.png`,`Pirate-F-01.png` | 5 | CC-BY 3.0 |
| 邪典/谜团 NPC | `cabbit_24x32/NPC/Cultist-01.png` | 1 | CC-BY 3.0 |
| 田野精灵 (装饰) | `cabbit_24x32/Monsters/Flower-01.png`,`Seed-01.png`,`Shrump-01.png`,`Poprigunchik_01.png` | 4 | CC-BY 3.0 |

## sprite sheet 切帧参考

| Sprite Sheet | 总尺寸 | 单帧 | 布局 | 用途 |
|--------------|-------|------|------|------|
| `tiny16_sharm/characters_1.png` | 192×128 | 16×16 | 12 列 × 8 行,大致 4 方向 × 2 帧 ≈ 12 角色 | Tiny16 风加点群众 |
| `fleurman_tiny_cc0/Males/M_XX.png` | 64×51 | ~16×17 | 4 列 (walk frame) × 3 行 (方向,以朝下/侧/朝上) | 主角 walk 动画 |
| `fleurman_tiny_cc0/Females/F_XX.png` | 64×51 | ~16×17 | 同上 | 主角 walk 动画 |
| `cabbit_24x32/Heroes/*.png`,`NPC/*.png` | 多为 153×128 或类似 | 24×32 | 3 帧 × 4 方向 × 类别变体 | NPC walk + idle |
| `grafxkid_rpg_cc0/RPG_assets.png` | 128×128 | 16×16 | 模板 + 长发 + 短发 + 裙装 | 自定义衣装时的参考 base |
| `armm1998_zelda_cc0/character.png` | 272×256 | 16×16 | 17 列 × 16 行 = 272 cells; 实际是 6+ 个角色变体 × (4 方向 walk × 2-3 帧) | Zelda 风主角/NPC 4 方向 walk |
| `armm1998_zelda_cc0/NPC_test.png` | 64×128 | 16×16 | 4 列 × 8 行 = 32 NPC 缩略变体 | NPC pop 池,可作小镇路人 |
| `armm1998_zelda_cc0/font.png` | 240×144 | (字符) | 像素字体 | 配套像素字体(备用,字体主线见 `assets/inbox/fonts/`) |

后续 `art pipeline` 任务会按上述参数把多人 sprite sheet 切成单角色 + 单方向的精细帧。

---

## 已有可复用 (来自 `assets/inbox/kenney_characters/`,CC0,不重复下载)

| 资源 | 路径 | 风格 | 适合的 Pythontopia 用途 |
|------|------|------|------------------------|
| Kenney Roguelike Characters | `kenney_characters/roguelike-characters/Spritesheet/roguelikeChar_transparent.png` | 16x16 顶视 chibi sprite sheet,多角色 | 主角备选 / 矿洞副本群怪 / 备用 NPC |
| Kenney Toon Characters (Female Adventurer) | `kenney_characters/toon-characters/Female adventurer/PNG/Poses/` 及 `Poses HD/` | 侧视卡通大尺寸,多 pose 静帧 | 对话立绘 / 大头像 (非走路场景) |
| Kenney Toon Characters (Male Adventurer) | `kenney_characters/toon-characters/Male adventurer/PNG/Poses/` 及 `Poses HD/` | 侧视卡通大尺寸,多 pose 静帧 | 对话立绘 / 大头像 |
| Kenney Toon Characters (Female Person) | `kenney_characters/toon-characters/Female person/PNG/` | 侧视卡通大尺寸,日常打扮 | 教师/助教 对话立绘备选 |
| Kenney Toon Characters (Male Person) | `kenney_characters/toon-characters/Male person/PNG/` | 侧视卡通大尺寸,日常打扮 | 教师/助教 对话立绘备选 |
| Kenney Toon Characters (Robot) | `kenney_characters/toon-characters/Robot/PNG/` | 机器人,大尺寸 | AI 助教/Agent 化身 (ProfileAgent/PathPlanner 等) |
| Kenney Toon Characters (Zombie) | `kenney_characters/toon-characters/Zombie/PNG/` | 僵尸,大尺寸 | Bug 副本 boss / 错误模式拟人化 (慎用,避免恐怖感) |

> **风格混用注意:** Kenney Toon 是侧视卡通,Tiny16/Cabbit/Fleurman 是俯视像素。两套不要在同一画面混合,
> 建议俯视像素用于小镇 walk,Kenney Toon 用于对话立绘 / 头像 / 加载页插画。

## 与其它 inbox 资源的关联

| 资源 | 路径 | 备注 |
|------|------|------|
| Building NPC 内嵌人物 | `assets/inbox/buildings_npc/kenney_roguelike-rpg-pack/` | RPG 包附带的少量 NPC sprite,与本目录形成补充 |
| Sprout Lands 农场风人物 | `assets/inbox/sprout_lands/` | 该包以建筑/作物为主,极少角色,本目录补齐人物缺口 |

## 数据汇总

| 项 | 数值 |
|----|------|
| 子目录 (sprite 套数) | **5** (armm1998_zelda_cc0 / cabbit_24x32 / fleurman_tiny_cc0 / grafxkid_rpg_cc0 / tiny16_sharm) |
| 总文件数 | **92** = 88 sprite + 1 `Mermaid_01.bmp` + 3 元数据 (`SOURCES.md`、`MANIFEST.md`、`armm1998_zelda_cc0/LICENSE.txt`) |
| PNG sprite 数 | **88** = 52 cabbit + 27 fleurman + 1 grafxkid + 5 tiny16 + 3 armm |
| 磁盘占用 | **676 KB** (`du -sh .`) |

**License 分布 (按 sprite 文件数):**

| 协议 | 文件数 | 占比 | 严格 R-100 |
|------|--------|------|-----------|
| CC0 1.0 | **31** = 27 fleurman + 1 grafxkid + 3 armm | 35 % | ✅ |
| CC-BY 4.0 (多许可可选) | 5 (tiny16_sharm) | 6 % | ✅ |
| CC-BY 3.0 + OGA-BY 3.0 | 52 + 1 bmp (cabbit) | 60 % | ⚠️ (3.0 非 4.0,无 SA/GPL 传染,严格派可移除) |
| CC-BY-SA / GPL / Non-commercial / 未知 | 0 | 0 % | — |

**关键能力清单:**

- ✅ 含 walk 4 方向动画的 sprite 套: 4 (`tiny16_sharm`、`cabbit_24x32`、`fleurman_tiny_cc0`、`armm1998_zelda_cc0`);`grafxkid_rpg_cc0` 是基底模板,非完整动画
- ✅ 严格 R-100 (CC0 / CC-BY 4.0) 4 方向 walk 套数: **3** (`fleurman_tiny_cc0`、`armm1998_zelda_cc0`、`tiny16_sharm`) — 超出任务要求的"至少 2 套"
- ✅ 学生主角 (4 方向 walk) 候选: ≥ 5 个 (`fleurman` 12 男 + 12 女,中性 2-4 个;`armm1998` character.png 6+ 角色变体)
- ✅ 教师 / 助教 / 特色 NPC 全部 9 角色映射齐备(每角色含主推 + 备选 + CC0 严格替代)
- ❌ **未引入星露谷官方素材** / **未引入未确认 license 的素材** / **未引入 LPC CC-BY-SA 系列** / **未引入 Non-commercial 素材**
