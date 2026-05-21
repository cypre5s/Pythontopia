# Pythontopia 9 类建筑素材映射（MANIFEST）

本文件把 Pythontopia 像素小镇的 **9 类核心建筑** 映射到 `assets/inbox/buildings/`、`assets/inbox/buildings_npc/`、`assets/inbox/sprout_lands/` 中现有的 CC0 / CC-BY / CC-BY-SA 像素素材。

> 命名约定：`<building_id>` 用 snake_case，对应 `frontend/src/game/world/` 与 `services/pythontopia-graph/` 的 `building_id` 字段。
>
> "外观（exterior）"指主世界（overworld）上的建筑正面像素图；"内部地图（interior）"指玩家进入后看到的暗示性室内 tile 组合（不是严格的玩法地图，仅作美术参考）。
> "屋顶色调"用于统一小镇视觉风格，避免素材混用时违和。

## 1. 画像屋 `profile_house`

> 含义：玩家初登小镇后，由 `ProfileAgent` 在此屋内完成 8 维画像访谈（专业背景 / 知识基础 / 学习目标 / 认知风格 / 学习偏好 / 易错模式 / 实践能力 / 情绪与坚持度）。

| 槽位 | 文件 |
| --- | --- |
| 外观（主） | `buildings_npc/lpc_thatched-roof-cottage/cottage.png`（512×512 中的 **左上小屋**，金色茅草顶 + 木骨石墙） |
| 外观（备） | `buildings/kenney_tiny-town/Tiles/`（红顶小屋系列 tile，需在 Tiled 中组合） |
| 内部地图 | `buildings/lpc_house_interior/interior.png`（**左上 1/4** 区域：橱柜 + 蒸馏器 + 木地板 + 一面书架，表现"个人空间"） |
| 屋顶色调 | 茅草金（#E8C76F），与小镇主屋一致 |
| 备注 | LPC 32×32 + thatched 双授权，必须保留 `cottage.png` 同包的 `CREDITS.txt`。 |

## 2. 图书馆 `library`

> 含义：课件 RAG 服务（`packId=43` Python语言基础）的可视化入口；7 份文档 / 561 页 / 7 章可在此查询，证据页码可被引用。

| 槽位 | 文件 |
| --- | --- |
| 外观（主） | `buildings_npc/kenney_medieval-rts/PNG/Default size/Structure/medievalStructure_05.png`（大型石质两层主体，作为知识塔的外观） |
| 外观（备） | `buildings/tiny16_basic/basictiles_2.png` 中的 **上方石塔 tile**（与城堡门同色调），适合 16×16 主城风格 |
| 内部地图 | `buildings/lpc_house_interior/interior.png`（**右上 1/2** 区域：连排书架 + 阅读桌 + 壁挂武器（可替换为"代码卷轴"）），暗示丰富文献 |
| 屋顶色调 | 灰青石（#5E6A78），与图书馆"严肃感"匹配 |
| 备注 | Kenney medieval-rts 是 64×64 顶视角，与小镇 16×16 风格不直接兼容；Beta 阶段建议改用 `tiny16_basic` 的石塔 + LPC 室内组合。 |

## 3. 知识田 `knowledge_field`

> 含义：KC（知识点）= 知识种子；学习资源浇水/施肥；练习除草；掌握度即作物成长。是 Pythontopia 教育隐喻的核心地块。

| 槽位 | 文件 |
| --- | --- |
| 外观（主） | `sprout_lands/sprout_lands_basic/Sprout Lands - Sprites - Basic pack/Tilesets/Tilled_Dirt_Wide_v2.png`（耕地地块） |
| 外观（备） | `sprout_lands/lpc_farm/lpc-farm/barn.png` 旁边的耕地区域（LPC 32×32） |
| 装饰：作物 | `sprout_lands/sprout_lands_basic/Sprout Lands - Sprites - Basic pack/Objects/Basic Plants.png`（成长 4 阶段，正好对应"萌发 → 复述 → 应用 → 综合"四级掌握度） |
| 装饰：栅栏 | `sprout_lands/sprout_lands_basic/Sprout Lands - Sprites - Basic pack/Tilesets/Fences.png` |
| 装饰：稻草人/工具 | `sprout_lands/sprout_lands_basic/Sprout Lands - Sprites - Basic pack/Characters/Tools.png` |
| 装饰：水车 | `sprout_lands/lpc_farm/lpc-farm/water-wheel.png`（"知识活水"灌溉） |
| 内部地图 | 田地本身即"地图"，无独立内景 |
| 屋顶色调 | 不适用（开放地块）；田垄棕（#7C5A3C） |
| 备注 | Sprout Lands CC-BY 4.0，作者 Cup Nooble。Sprout Lands 整套 16×16 与 Kenney Tiny Town 16×16 在比例上完美匹配，可作为知识田与小镇主体的桥接。 |

## 4. 资源工坊 `workshop`

> 含义：`ResourceDesignerAgent` + `StoryboardAgent` 在此把 KC 锻造成讲义/思维导图/卡片/故事板等学习资源。

| 槽位 | 文件 |
| --- | --- |
| 外观（主） | `sprout_lands/lpc_farm/lpc-farm/barn.png`（双开门大型谷仓，作为"知识锻造工坊"的外观） |
| 外观（备） | `buildings/kenney_tiny-town/Tiles/`（红顶储物棚 tile，参见 `Sample.png` 中左侧带条纹外墙的房子） |
| 内部地图 | `buildings/lpc_house_interior/interior.png`（**右下 1/3** 区域：金属灶 + 木桶酒架 + 蒸馏器 + 木桌椅，暗示"思维加工"） |
| 装饰：工具 | `buildings/kenney_tiny-town/Tiles/`（独立锤子/斧头/钥匙等小物 tile）<br>`buildings_npc/lpc_medieval-village-decorations/decoration_medieval/decorations-medieval.png`（铁砧、推车、木匠桌） |
| 屋顶色调 | 红木栗（#8C4A2A），区别于画像屋的茅草金 |
| 备注 | LPC barn + LPC interior 同属 CC-BY-SA 3.0+，授权一致便于二次合成。 |

## 5. 训练场 `training_ground`

> 含义：`PracticeAgent` + `CodeLabAgent` 在此发布编程练习；玩家提交代码 → 服务端判题 → 通过 / 失败 / 错误 三类结果。

| 槽位 | 文件 |
| --- | --- |
| 外观（主） | `buildings/kenney_tiny-town/Tiles/`（参见 `Sample.png` **右上**：3 个红色箭靶 + 木栅栏围合的开放场地） |
| 外观（备） | `buildings_npc/lpc_medieval-village-decorations/decoration_medieval/decorations-medieval.png` 中的 **铁砧 + 武器架 + 营帐**（适合 LPC 32×32 风格） |
| 装饰：箭靶 | `buildings/kenney_tiny-town/Tilemap/tilemap.png` 中右上角红圈靶子（CC0） |
| 装饰：木桩/围栏 | `buildings/kenney_tiny-town/Tiles/`（水平 + 垂直木栅栏 tile） |
| 内部地图（室内备用） | `buildings/lpc_house_interior/interior.png`（**中下** 区域：盔甲架 + 武器陈列墙，作为雨天训练的备用室内） |
| 屋顶色调 | 不适用（开放场地）；木桩棕（#9C6B3F） |
| 备注 | "提交一次代码 = 射一箭"的视觉隐喻；通过率即靶子命中率。 |

## 6. Bug 矿洞 `bug_mine`

> 含义：练习失败时触发的 Bug 调试副本；`DebugAgent` 在此引导玩家定位 NameError / TypeError / IndexError / 缩进错误等典型 Python 易错点。

| 槽位 | 文件 |
| --- | --- |
| 外观（主：入口） | `buildings_npc/kenney_roguelike-rpg-pack/Spritesheet/roguelikeSheet_transparent.png`（**Sample1.png 左中**可见的山体黑口洞穴 tile，16×16） |
| 外观（备） | `buildings/tiny16_basic/basictiles_2.png` 中的 **黑色方形传送门 tile**（CC-BY 3.0，可作传送阵入口） |
| 内部地图 | **🟡 待补绘**：矿洞内部需要洞穴墙体、岩石、矿石、Bug 怪物。<br>临时方案：可以用 `buildings_npc/kenney_roguelike-rpg-pack/Spritesheet/roguelikeSheet_transparent.png` 中的灰岩 tile 拼接，但视觉风格略偏 RPG，不够"调试感"。<br>建议补绘：发光 Bug 水晶（红/黄/蓝代表三类报错）+ 调试灯笼。 |
| 屋顶色调 | 不适用（山体洞口）；岩灰（#4A4A4A） |
| 备注 | Bug 矿洞是 Pythontopia 教育游戏化的关键载体，**外观可复用，但内部 tileset 需要后续补绘或额外下载**。可考虑下载 [opengameart.org/content/cave-tileset-2](https://opengameart.org/content/cave-tileset-2)（Sharm，CC-BY 3.0）作为内部矿洞补充。 |

## 7. 公告板 `board`

> 含义：班级公告 + 教师发布的班级任务 + 全镇活动看板；学生在此领取任务、查看 KC 热力图入口。

| 槽位 | 文件 |
| --- | --- |
| 外观（主） | `buildings_npc/lpc_medieval-village-decorations/decoration_medieval/decorations-medieval.png`（**上中** 区域有金边镶木告示牌 + 中部木质告示板，正面/侧面 / 带顶棚多变体） |
| 外观（备） | `buildings/kenney_tiny-town/Tiles/`（独立木牌 + 木桩 tile，Sample.png 中部下方的褐色立式招牌） |
| 内部地图 | 不适用（户外结构，玩家走近触发 UI 弹窗） |
| 屋顶色调 | 木顶棕（#7A4A24）；告示纸面米黄（#F2E2B8） |
| 备注 | 公告板必须在主城最显眼位置；建议三块并列（"班级公告 / 全镇活动 / 教师任务"）。 |

## 8. 教师屋 `teacher_house`

> 含义：教师视角入口；教师在此创建班级、布置任务、查看班级 KC 热力图、任务完成率、高频误区、资源使用情况。学生可走近问问题但无法进入。

| 槽位 | 文件 |
| --- | --- |
| 外观（主） | `buildings_npc/lpc_thatched-roof-cottage/cottage.png`（512×512 中的 **中上较大双联屋**，棕顶 + 双层结构，与画像屋的小金顶屋形成视觉区分） |
| 外观（备） | `buildings_npc/kenney_medieval-rts/PNG/Default size/Structure/medievalStructure_03.png` 或 `_04.png`（带塔楼的中型房子，符合"师长"威严） |
| 内部地图 | `buildings/lpc_house_interior/interior.png`（**左中** 区域：木地板 + 书架 + 写字台 + 壁炉，暗示"备课书房"） |
| 屋顶色调 | 茶褐（#6B4226），区别于画像屋的金茅草、复盘大厅的灰青石 |
| 备注 | 教师屋门口建议放一个公告板 mini 实例，标"教师答疑时间"。 |

## 9. 复盘大厅 `review_hall`

> 含义：玩家完成阶段性任务后，在此回顾掌握度变化、遗忘曲线、错题集；`MindMapAgent` 在此渲染知识图谱与学习路径。

| 槽位 | 文件 |
| --- | --- |
| 外观（主） | `buildings_npc/kenney_roguelike-rpg-pack/Spritesheet/roguelikeSheet_transparent.png`（**Sample2.png** 整张大宅 = 三厅一堂，正面有阶梯入口） |
| 外观（备） | `buildings/kenney_tiny-town/Tiles/`（城堡门楼 + 城墙 tile，Sample.png 右下灰石拱门城堡） |
| 内部地图 | `buildings/lpc_house_interior/interior.png` 全图最佳：**右上书架 + 中央大厅 + 左下铠甲展示 + 中下武器墙**，可作"知识展览大厅"完整布局 |
| 屋顶色调 | 灰青石（#5E6A78），与图书馆呼应（学习闭环的两端） |
| 备注 | 复盘大厅与图书馆形成"输入（library）— 输出（review_hall）"的对称结构，建议放在小镇南北两端。 |

---

## 9 类建筑映射速查

```text
画像屋 (profile_house)     -> buildings_npc/lpc_thatched-roof-cottage/cottage.png（左上小屋）
                            + buildings/lpc_house_interior/interior.png（左上 1/4）
图书馆 (library)           -> buildings_npc/kenney_medieval-rts/PNG/Default size/Structure/medievalStructure_05.png
                            + buildings/lpc_house_interior/interior.png（右上 1/2 书架区）
知识田 (knowledge_field)   -> sprout_lands/sprout_lands_basic/.../Tilesets/Tilled_Dirt_Wide_v2.png
                            + sprout_lands/sprout_lands_basic/.../Objects/Basic Plants.png
                            + sprout_lands/sprout_lands_basic/.../Tilesets/Fences.png
                            + sprout_lands/lpc_farm/lpc-farm/water-wheel.png
资源工坊 (workshop)        -> sprout_lands/lpc_farm/lpc-farm/barn.png
                            + buildings/lpc_house_interior/interior.png（右下 1/3 灶台区）
                            + buildings_npc/lpc_medieval-village-decorations/.../decorations-medieval.png（铁砧/推车）
训练场 (training_ground)   -> buildings/kenney_tiny-town/Tiles/（红圈靶 + 木栅栏）
                            + buildings_npc/lpc_medieval-village-decorations/.../decorations-medieval.png（铁砧 + 武器架）
Bug 矿洞 (bug_mine)        -> buildings_npc/kenney_roguelike-rpg-pack/Spritesheet/roguelikeSheet_transparent.png（山体黑口）
                            + 🟡 内部矿洞 tileset 待补绘（建议下载 OGA "Cave Tileset 2" by Sharm 补齐）
公告板 (board)             -> buildings_npc/lpc_medieval-village-decorations/.../decorations-medieval.png（告示牌组）
                            + buildings/kenney_tiny-town/Tiles/（独立木牌备选）
教师屋 (teacher_house)     -> buildings_npc/lpc_thatched-roof-cottage/cottage.png（中上双联屋）
                            + buildings/lpc_house_interior/interior.png（左中书房）
复盘大厅 (review_hall)     -> buildings_npc/kenney_roguelike-rpg-pack/Spritesheet/roguelikeSheet_transparent.png（Sample2.png 大宅）
                            + buildings/lpc_house_interior/interior.png（整图作展览大厅）
```

## 缺漏清单（🟡 待补绘 / 待补下载）

| 编号 | 缺漏项 | 严重度 | 建议方案 |
| --- | --- | --- | --- |
| 1 | **Bug 矿洞内部 tileset**：洞穴墙体、岩石、矿石、Bug 怪物 | 🟡 中 | 下载 [OGA Cave Tileset 2](https://opengameart.org/content/cave-tileset-2)（Sharm，CC-BY 3.0）或 [OGA Mining Tileset](https://opengameart.org/content/dungeon-mining-tileset)；或由像素美术补绘"Bug 水晶 + 调试灯笼" |
| 2 | **Pythontopia 专属告示牌纹理**：班级公告/教师任务的中文模板 | 🟢 低 | 在现有 LPC 告示牌底图上做局部贴图（中文标题需用 9-12px 中文像素字体，如方正像素 12） |
| 3 | **统一屋顶色调的色相调整图层** | 🟢 低 | 在 Phaser 加载阶段做 tint，无需替换原 PNG |
| 4 | **大型石质建筑（图书馆 / 复盘大厅）的 16×16 版本** | 🟡 中 | 当前 Kenney medieval-rts 是 64×64 顶视角，与 16×16 小镇不直接兼容。建议用 `buildings/kenney_tiny-town/` 城堡门楼组合，或下载 [OGA Tiny Castle](https://opengameart.org/content/tiny-castle) 作为补充 |
| 5 | **画像屋 / 教师屋的角色站位标记** | 🟢 低 | 不属于素材范畴，由 `frontend/src/game/world/buildings.ts` 在 Tiled JSON 中定义 anchor 点即可 |
| 6 | **公告板上的「KC 热力图」预览图标** | 🟢 低 | 自绘小型 16×16 矩阵图标 |
| 7 | **训练场的"通过 / 失败 / 错误"三类靶环颜色** | 🟢 低 | Tiny Town 自带红圆靶 1 种，绿/橙变体可在 Phaser 中 tint |

## 统计

| 项 | 数量 |
| --- | --- |
| 本 inbox 子目录数（本目录新增） | 3（`kenney_tiny-town/`、`tiny16_basic/`、`lpc_house_interior/`） |
| 本目录文件总数 | 145 |
| 本目录磁盘占用 | 892 KB |
| 引用的现有目录数 | 6（`buildings_npc/` 4 个 + `sprout_lands/` 2 个相关包） |
| 9 类建筑映射完整度 | 9/9（**外观全部 OK**；7/9 内部素材 OK，2/9 待补绘：Bug 矿洞内部 + 大型 16×16 主体） |
| 许可证类型 | CC0 / CC-BY 3.0 / CC-BY-SA 3.0+ / CC-BY 4.0 全部合规 |

## 整理操作建议

1. **Phaser tile size 分层**：在 `frontend/src/game/world/` 按 `tile16/`、`tile32/`、`tile64/` 分目录加载，避免渲染时的比例错位。
2. **`license/` 目录**：把所有 CREDITS、LICENSE、credits.txt 集中复制到 `frontend/public/credits/`，在游戏内"鸣谢"入口可访问，满足 CC-BY / CC-BY-SA 的署名要求。
3. **建筑 anchor 协议**：每个建筑对应一份 `building_id.json`，记录 `entrance_x`、`entrance_y`、`interior_map_id`、`npc_anchors[]`，由前端 + `services/pythontopia-graph/` 共享。
4. **不引入星露谷官方素材**：本 manifest 全部来自 CC0 / CC-BY / CC-BY-SA 社区作者，符合 Pythontopia 硬约束。
