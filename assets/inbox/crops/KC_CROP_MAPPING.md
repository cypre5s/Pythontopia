# Pythontopia · KC → 知识种子（Crop）映射表

> **教育隐喻**: KC = 知识种子；学习资源 = 浇水 / 施肥；练习 = 除草；Bug = 矿洞病害；掌握度 = 作物生长阶段；遗忘 = 枯萎。
> **覆盖范围**: 7 章 × 共 **65 KC**（pack_id=43，《Python 语言基础》），1 KC 唯一对应 1 种作物，1 种作物唯一 1 个 KC。
> **数据来源**: `data/course-pack-43/json/course_kc.json` + `course_chapter.json`。
> **素材来源**: 见同目录下 `SOURCES.md`、`LICENSE.md`。

---

## 0. 现有素材盘点（不补全结论）

| 包 | 文件 | 分辨率 | 阶段数 | 作物数 | License | 任务期望「5 阶段（seed/sprout/mature/harvest/withered）」 |
| --- | --- | --- | --- | --- | --- | --- |
| `lpc-crops/crops-v2/crops.png` | 1 张 spritesheet | 1024 × 1024 RGBA | **5 帧** 竖排 | **50+** 种 | CC-BY-SA 3.0+ / GPL 3.0+（部分子图 CC0） | ✅ 满足 |
| `farming-crops-16x16/Crop_Spritesheet.png` | 1 张 spritesheet | 192 × 160 RGBA | **5 阶段 + 1 头像** | **20** 种 | CC0 | ✅ 满足 |
| `farming-game-basics/PNG/*.png` | 17 张独立 PNG + 1 SVG | 30~220px 不等 | **4 阶段 + 1 icon** | **4** 种（bean/carrot/paprika/potato）+ 环境瓦片 | CC-BY 4.0 | ⚠️ 仅 4 阶段，可补齐为 5 阶段（最后一帧空地 / 灰阶滤镜模拟枯萎） |
| `lpc-farming-tilesets/submission_daneeklu/tilesets/*.png` | 11 张瓦片 | 16×16 tile | — | 0（仅田地 / 围栏 / 长草 / 已翻耕） | CC-BY-SA 3.0 / GPL 3.0 | 仅作场景背景使用 |

- **总作物种类**: 50 + 20 + 4 = **74 种** > 65 KC，**无需补全**。
- **PNG 文件总数**: 54（含 spritesheet 与独立 PNG）；目录总文件数：65（PNG 54 + SVG 1 + ZIP/tar.gz 4 + README/LICENSE/CREDITS 6）。
- **磁盘占用**: 7.5 MB（其中 farming-game-basics 因高分辨率 PNG + SVG 占 6.1 MB）。
- **是否包含 4 阶段以上动画**: 是。LPC 与 farming-crops-16x16 均 5 帧，farming-game-basics 4 阶段。前端工程师可统一切片为 5 帧标准（seed → sprout → mature → harvest → withered）。

> **任务"是否需要从 OpenGameArt 再下载补全"判定**：**不需要**。素材数量与阶段都已达标，再次下载只会重复内容。直接进入映射阶段。

---

## 1. 章节色调（chapter palette）总览

色调供前端 `useChapterTheme()` 在像素小镇中按 chapter 切换 KC 田垄/作物滤镜与 UI 主题。所有色值已挑选成 Phaser tint / CSS HSL 可直接使用，并保持像素质感。

| chapter_id | 章节 | KC 数 | 主题意象 | 主色调 | 辅色 | Phaser tint | 教育语义 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 179 | 第一章 计算工具与计算思维 | 7 | 古老智识 / 萌芽思维 | 紫罗兰 `#7C3AED` | 古铜 `#8E5B3C` | `0x7c3aed` | 思维启蒙、计算溯源 |
| 180 | 第二章 Python 语言基础 | 9 | 嫩绿初芽 / 入门语法 | 嫩绿 `#22C55E` | 米白 `#F0FDF4` | `0x22c55e` | 生命之始、入门可达 |
| 181 | 第三章 计算机信息表示及计算 | 14 | 二进制金光 / 字符码点 | 橙黄 `#F59E0B` | 金属 `#EAB308` | `0xf59e0b` | 数字光辉、位级度量 |
| 182 | 第四章 流程自动化 | 8 | 循环火苗 / 自动节拍 | 朱红 `#EF4444` | 暖橙 `#F97316` | `0xef4444` | 行动循环、条件分支 |
| 183 | 第五章 数据结构 | 9 | 多样集合 / 序列与映射 | 青蓝 `#06B6D4` | 紫红 `#D946EF` | `0x06b6d4` | 结构多样、序列映射 |
| 184 | 第六章 数据存取与分析 | 6 | 深蓝仓库 / 数据沉淀 | 深蓝紫 `#4338CA` | 深青 `#0F766E` | `0x4338ca` | 持久化、文件分析 |
| 185 | 第七章 归纳与抽象 | 12 | 金色丰收 / 高阶分形 | 金黄 `#FACC15` | 墨绿 `#166534` | `0xfacc15` | 模块抽象、面向对象 |

> 实现建议：在 `frontend/src/features/farm/composables/useKcCropTheme.ts` 暴露 `getPaletteByChapter(chapterId)`，作物精灵默认无 tint；遗忘态使用 `0x9CA3AF`（灰）+ 60% alpha；矿洞病害态使用 chapter 主色 mix 黑 30%。

---

## 2. KC ↔ 作物映射主表（按 KC code 升序）

> 字段说明：
> - **KC code / 中文名 / chapter** 来自 `course_kc.json`
> - **Crop sprite**: 主选作物精灵的标识名（与各 spritesheet 内部命名一致）
> - **Source pack**: `lpc` = `lpc-crops/crops-v2/crops.png`；`fc16` = `farming-crops-16x16/Crop_Spritesheet.png`；`fgb` = `farming-game-basics/PNG/`
> - **Cell hint**: spritesheet 内的网格定位提示（最终切片坐标由前端 atlas 配置确定，这里只给定性位置）
> - **生长隐喻**: KC 与作物在教育语义上的对位
> - **章节色调**: 来自第 1 节配色表（建议作为该作物的 tint 默认值，可被 KC 掌握度状态机覆盖）

### 第一章 · 计算工具与计算思维（chapter_id=179，紫罗兰色调 `#7C3AED`）

| KC code | KC 中文名 | Crop sprite | Source | Cell hint | 生长隐喻 |
| --- | --- | --- | --- | --- | --- |
| PY43-KC-001 | 中国计算机发展成就 | **Purple potatoes**（紫马铃薯） | lpc | "Potato root (DE)" 群组之紫色变体 | 自研根基厚重、本土深耕 |
| PY43-KC-002 | 计算工具的演变 | **Rhubarb**（大黄） | lpc | "Rhubarb / chard" 行 | 古老作物、工具溯源 |
| PY43-KC-003 | 计算思维的方法与特征 | **Red cabbage / Purple cabbage**（紫甘蓝） | lpc | "Cabbage/Cauliflower/Broccoli" 紫变体 | 层层包裹 → 多维特征 |
| PY43-KC-004 | 计算思维的概念与定义 | **Eggplant**（茄子） | lpc | "Zucchini/Squash/Cucumber vine" 紫黑变体 | 紫光泽内核 → 思维之"芯" |
| PY43-KC-005 | 计算机发展历程与趋势 | **Beets**（甜菜根） | lpc | "Beet/Turnip/Rutabaga root" 红紫 | 代际生长、年轮记忆 |
| PY43-KC-006 | 计算的概念 | **Blackberries**（黑莓） | lpc | "{Black,Straw,Rasp}berry bush" 黑色子图 | 粒粒积聚 → 元运算累积 |
| PY43-KC-007 | 计算科学与计算机科学 | **Raspberries**（树莓） | lpc | "{Black,Straw,Rasp}berry bush" 紫红子图 | 与黑莓同根分支 → 同源不同向 |

### 第二章 · Python 语言基础（chapter_id=180，嫩绿色调 `#22C55E`）

| KC code | KC 中文名 | Crop sprite | Source | Cell hint | 生长隐喻 |
| --- | --- | --- | --- | --- | --- |
| PY43-KC-008 | Python开发环境配置与程序运行 | **Bok choy**（小白菜） | lpc | "Bok choy" 行 | 易上手、新手快速发芽 |
| PY43-KC-009 | Python语言概述 | **Iceberg lettuce**（球生菜） | lpc | "Iceberg lettuce" 行 | 清爽简洁、Python 语言"易读"气质 |
| PY43-KC-010 | 变量与运算符 | **Asparagus**（芦笋） | lpc | "Asparagus plant" 行 | 笔直茎秆 → 变量赋值符号 |
| PY43-KC-011 | 基本数据类型 | **Romaine lettuce**（罗马生菜） | lpc | "Romaine lettuce" 行 | 多层叶片 → int/float/str/bool 并列 |
| PY43-KC-012 | 基础函数 | **Cucumber**（黄瓜） | lpc | "Zucchini/Squash/Cucumber vine" 绿色变体 | 饱满规则 → 输入 → 输出 |
| PY43-KC-013 | 控制台输入与数据类型转换 | **Celery**（芹菜） | lpc | "Celery" 行 | 茎秆通道 → input()/eval() 输入管道 |
| PY43-KC-014 | 程序的运行方式 | **Green cabbage**（圆白菜） | lpc | "Cabbage/Cauliflower/Broccoli" 绿变体 | 结球完成 → IDLE 程序整体运行 |
| PY43-KC-015 | 程序设计语言发展历史 | **Green onion / Scallion**（青葱） | lpc | "Onion/Garlic/Leek" 葱绿变体 | 笔直分层 → 机器语言 → 汇编 → 高级 |
| PY43-KC-016 | 程序运行原理 | **Green bell pepper**（青椒） | lpc | "Pepper bush (DE)" 绿变体 | 空腔结构 → 输入 / 处理 / 输出 三段 |

### 第三章 · 计算机信息表示及计算（chapter_id=181，橙黄色调 `#F59E0B`）

| KC code | KC 中文名 | Crop sprite | Source | Cell hint | 生长隐喻 |
| --- | --- | --- | --- | --- | --- |
| PY43-KC-017 | 0和1的计算思维 | **Corn**（玉米，DE 版） | lpc | "Corn (DE)" 行 | 整齐两列颗粒 → 0/1 二元 |
| PY43-KC-018 | format()方法的格式控制 | **Crookneck squash**（弯颈南瓜） | lpc | "Gourd vine" 弯颈变体 | 弯曲可塑 → 槽位 / 对齐 / 宽度自定义 |
| PY43-KC-019 | Python数值类型与运算 | **Yellow bell pepper**（黄椒） | lpc | "Pepper bush (DE)" 黄变体 | 多腔分类 → int/float/complex/bool |
| PY43-KC-020 | Unicode编码与字符函数 | **Cantaloupe melon**（哈密瓜） | lpc | "Melon vine" 网纹变体 | 表皮网格纹理 → 码点矩阵 |
| PY43-KC-021 | 二进制四则运算 | **Honeydew melon**（蜜瓜） | lpc | "Melon vine" 光滑变体 | 光洁规则 → 逢二进一 / 借一当二 |
| PY43-KC-022 | 字符串处理方法 | **Yellow squash / Summer squash**（黄西葫芦） | lpc | "Zucchini/Squash/Cucumber vine" 黄变体 | 可切可削 → strip / split / replace |
| PY43-KC-023 | 字符串格式化 | **Acorn squash**（橡子南瓜） | lpc | "Gourd vine" 橡子变体 | 紧凑外形 → 槽内格式化输出 |
| PY43-KC-024 | 字符串索引与切片 | **Butternut squash**（葫芦南瓜） | lpc | "Gourd vine" 长葫芦变体 | 细长可切 → [M:N:K] 切片 |
| PY43-KC-025 | 整数位数提取 | **Yellow onion / Sweet onion**（黄洋葱） | lpc | "Onion/Garlic/Leek" 黄变体 | 一层一层剥 → //10 取末位、%10 取尾 |
| PY43-KC-026 | 文字信息编码 | **Sweet potatoes**（红薯） | lpc | "Potato root (DE)" 红薯子图 | 多层皮 → ASCII / Latin-1 / UTF-8 多层 |
| PY43-KC-027 | 汉字编码体系 | **Orange bell pepper**（橙椒） | lpc | "Pepper bush (DE)" 橙变体 | 多腔体 → 输入码 / 国标码 / 机内码 / 字形码 |
| PY43-KC-028 | 第三方库的使用 | **Pumpkin**（南瓜） | lpc | "Gourd vine" 南瓜本体 | 大体积装入 → import random as r |
| PY43-KC-029 | 进位计数制与二进制 | **Carrots**（胡萝卜） | lpc | "Carrot root (DE)" 橙变体 | 地下分层 → 位权 1/2/4/8 分层 |
| PY43-KC-030 | 进制转换 | **Lemon**（柠檬） | fc16 | 行 4 col 2（Eggplant, **Lemon**） | 酸味映射 → bin/oct/hex 之间转换 |

### 第四章 · 流程自动化（chapter_id=182，朱红色调 `#EF4444`）

| KC code | KC 中文名 | Crop sprite | Source | Cell hint | 生长隐喻 |
| --- | --- | --- | --- | --- | --- |
| PY43-KC-031 | for循环与遍历 | **Cherry tomatoes**（樱桃番茄） | lpc | "Tomato vine (DE)" 小果变体 | 成串小果 → for i in range / iter |
| PY43-KC-032 | while循环与循环分支结合 | **Large tomatoes**（大番茄） | lpc | "Tomato vine (DE)" 大果变体 | 藤蔓持续生长 → while True + if-break |
| PY43-KC-033 | 分支结构 | **Tomatoes**（番茄，标准） | lpc | "Tomato vine (DE)" 标准变体 | 茎秆分叉 → if / elif / else |
| PY43-KC-034 | 分支语句嵌套 | **Red bell pepper**（红椒） | lpc | "Pepper bush (DE)" 红变体 | 多层腔体 → 嵌套 if |
| PY43-KC-035 | 多条件判断与逻辑运算 | **Hot pepper**（朝天椒） | lpc | "Pepper bush (DE)" 辣变体 | 多种辣度组合 → and / or / not |
| PY43-KC-036 | 循环嵌套 | **Chili peppers**（多色辣椒 Poblano / Habanero / Jalapeño） | lpc | "Pepper bush (DE)" 串果变体 | 多色多层 → 双层 for / for-while |
| PY43-KC-037 | 循环控制语句 | **Radishes**（红萝卜） | lpc | "Carrot root (DE)" 红短变体 | 短促干脆 → break / continue |
| PY43-KC-038 | 顺序结构 | **Strawberry**（草莓） | lpc | "{Black,Straw,Rasp}berry bush" 红色子图 | 自上而下排列 → 顺序执行 |

### 第五章 · 数据结构（chapter_id=183，青蓝色调 `#06B6D4`）

| KC code | KC 中文名 | Crop sprite | Source | Cell hint | 生长隐喻 |
| --- | --- | --- | --- | --- | --- |
| PY43-KC-039 | Random模块与列表 | **Red grapes**（红葡萄） | lpc | "Grapevine" 红色子图 | 成串可随机摘 → random.choice / shuffle |
| PY43-KC-040 | 列表基本操作 | **Green grapes**（青葡萄） | lpc | "Grapevine" 绿色子图 | 整串增删改 → append / pop / sort |
| PY43-KC-041 | 字典在实际问题中的应用 | **Coffee**（咖啡） | lpc | "Coffee tree" 行 | 咖啡豆（key）→ 烘焙口味（value） |
| PY43-KC-042 | 字典基本操作 | **Pineapple**（菠萝） | lpc | "Pineapple plant" 行 | 表面菱格键值对 → dict get/set/del |
| PY43-KC-043 | 字典类型 | **Kiwi**（猕猴桃） | lpc | "Kiwi vine" 行 | 外皮键 / 内瓤值 → 字典内外结构 |
| PY43-KC-044 | 序列基本操作 | **Peas / Snap peas**（豌豆） | lpc | "Pea / green bean vine" 豌豆子图 | 豆荚有序 → 索引 / 切片 / len |
| PY43-KC-045 | 序列类型 | **Green beans**（青豆） | lpc | "Pea / green bean vine" 青豆子图 | 豆荚家族 → list / tuple / str / range |
| PY43-KC-046 | 集合的删除操作 | **Brussels sprouts**（抱子甘蓝） | lpc | "Brussels sprouts" 行 | 一颗颗摘除 → set.remove / discard / pop |
| PY43-KC-047 | 集合类型 | **Hops**（啤酒花） | lpc | "Hops vine" 行 | 多球唯一 → set 元素去重 |

### 第六章 · 数据存取与分析（chapter_id=184，深蓝紫色调 `#4338CA`）

| KC code | KC 中文名 | Crop sprite | Source | Cell hint | 生长隐喻 |
| --- | --- | --- | --- | --- | --- |
| PY43-KC-048 | Python文件操作 | **Artichoke**（朝鲜蓟） | lpc | "Artichoke plant (DE)" 行 | 层层苞叶 → open / read / close 上下文 |
| PY43-KC-049 | 基于文件的数据分析 | **Avocado**（牛油果） | fc16 | 行 9 col 2（Orange, **Avocado**） | 果肉=数据 / 果核=分析结论 |
| PY43-KC-050 | 文件写入操作 | **Blueberries**（蓝莓） | lpc | "Blueberry bush" 行 | 一颗颗写入 → write 反复追加 |
| PY43-KC-051 | 文件系统基础 | **Watermelon**（西瓜） | lpc | "Melon vine" 西瓜子图 | 大型容器 → 目录 / 文件层级 |
| PY43-KC-052 | 文件读取与数据处理 | **Wheat**（小麦） | fc16 | 行 6 col 1（**Wheat**, Grapes） | 颗粒逐行收割 → readline / readlines |
| PY43-KC-053 | 词频统计 | **Rice**（水稻） | fc16 | 行 5 col 2（Pineapple, **Rice**） | 颗粒清点 → 词频统计 |

### 第七章 · 归纳与抽象（chapter_id=185，金黄色调 `#FACC15`）

| KC code | KC 中文名 | Crop sprite | Source | Cell hint | 生长隐喻 |
| --- | --- | --- | --- | --- | --- |
| PY43-KC-054 | lambda函数 | **Leeks**（韭葱） | lpc | "Onion/Garlic/leek" 韭葱子图 | 细长一行 → lambda x: x+1 单行函数 |
| PY43-KC-055 | pyinstaller打包 | **Gold potatoes**（金马铃薯） | lpc | "Potato root (DE)" 金色子图 | 包装精品 → 单一 exe 可分发 |
| PY43-KC-056 | Python包 | **Garlic**（大蒜） | lpc | "Onion/Garlic/Leek root" 大蒜子图 | 多瓣聚合 → package 内含 module |
| PY43-KC-057 | Python库与pip工具 | **Russet potatoes**（黄褐马铃薯） | lpc | "Potato root (DE)" 标准黄褐 | 基础主食 → 标准库 + pip 第三方库 |
| PY43-KC-058 | 主程序与__name__变量 | **Parsnips**（欧防风） | lpc | "Carrot root (DE)" 欧防风白根 | 深根主入口 → if __name__ == "__main__" |
| PY43-KC-059 | 函数参数高级用法 | **Fennel bulb**（茴香） | lpc | "Fennel plant" 行 | 多层鳞茎 → *args / **kwargs / default |
| PY43-KC-060 | 函数变量的作用域 | **Kohlrabi**（苤蓝） | lpc | "Kohlrabi" 行 | 球茎封闭 → LEGB 作用域链 |
| PY43-KC-061 | 函数递归 | **Yuca / Cassava**（木薯） | lpc | "Cassava" 行 | 深根分形 → fact(n) 自我调用 |
| PY43-KC-062 | 模块与导入 | **Kale**（羽衣甘蓝） | lpc | "Kale" 行 | 多片相连 → import / from-import |
| PY43-KC-063 | 模块化编程 | **Rutabaga**（大头菜） | lpc | "Beet/Turnip/Rutabaga root" 杂交块根 | 杂交融合 → 多模块协作 |
| PY43-KC-064 | 类和对象基础 | **Cauliflower**（花椰菜） | lpc | "Cabbage/Cauliflower/Broccoli" 白色子图 | 一母多花球 → class 与 instance |
| PY43-KC-065 | 面向对象编程的特征 | **Broccoli**（西兰花） | lpc | "Cabbage/Cauliflower/Broccoli" 绿色子图 | 分形树状 → 封装 / 继承 / 多态 |

---

## 3. 备用 sprite（farming-game-basics 高清版）

`farming-game-basics` 的 4 张作物（bean / carrot / paprika / potato）分辨率较大（最高 124×221），适合在以下场景作为同类 KC 的 **高清替代** 或 **图鉴大图**：

| 备用 sprite | 推荐替代 KC | 推荐用法 |
| --- | --- | --- |
| `fgb/bean_1~4.png` + `bean_icon.png` | PY43-KC-044 序列基本操作 / PY43-KC-045 序列类型 | 序列章节图鉴主图 |
| `fgb/carrot_1~4.png` + `carrot_icon.png` | PY43-KC-029 进位计数制与二进制 | 学习成果勋章 / 头像 |
| `fgb/paprika_1~4.png` + `paprika_icon.png` | PY43-KC-034 分支语句嵌套 | 分支结构关卡封面 |
| `fgb/potato_1~4.png` + `potato_icon.png` | PY43-KC-057 Python库与pip工具 | 基础设施关卡封面 |
| `fgb/bush.png` / `bush_big.png` | 公共 | 田边丛林背景 |
| `fgb/flower_tile_1~3.png` | 公共 | 装饰瓦片 |
| `fgb/soil_tile.png` / `path_tile.png` / `grass_tile.png` / `stone_tile.png` | 公共 | 农场地表 |

---

## 4. 场景瓦片（lpc-farming-tilesets）建议用途

| Tileset | 推荐用途 | 注意事项 |
| --- | --- | --- |
| `youngwheat.png` | 幼苗期通用田垄背景（chapter 1 / 2 / 7） | 与 LPC crops 风格一致 |
| `wheat.png` | 成熟期田垄背景（chapter 6 / 7 收获意象） | 配合 KC-052 / KC-053 麦穗主题 |
| `plowed_soil.png` | 翻耕地（KC 未掌握状态） | 与 fgb 的 `soil_tile.png` 互补 |
| `plants.png` | 通用作物 + 番茄 / 玉米 备份 | 风格与 LPC 100% 兼容 |
| `tallgrass.png` / `reed.png` / `fence.png` / `fence_alt.png` | 农场围栏 / 草丛装饰 | 围墙作为班级"领地"边界 |
| `farming_fishing.png` / `sand.png` / `sandwater.png` | 水域 / 沙地 | 池塘 / 装饰，非作物使用 |

---

## 5. 阶段命名约定（前端契约）

| 阶段 ID | 帧索引（LPC / fc16） | farming-game-basics 对应 | 含义 | 出现条件 |
| --- | --- | --- | --- | --- |
| `seed` | 0 | `*_1.png` | 种子 / 翻耕 | 进入 KC，尚未学习 |
| `sprout` | 1 | `*_2.png` | 幼苗 | 看过课件页 ≥ 1 |
| `mature` | 2 | `*_3.png` | 茁壮 | 资源 / 练习首次完成 |
| `harvest` | 3 | `*_4.png` | 成熟 / 可收获 | 掌握度 ≥ 0.8 |
| `withered` | 4 | 由 `*_4.png` 灰度 + 60% alpha 合成 | 枯萎 / 遗忘 | 30 天未复习且掌握度衰减 < 0.4 |

> farming-game-basics 仅 4 阶段（缺 withered），由前端 `cropStateMachine.ts` 在运行时合成灰阶；不修改原始素材，保持 license 合规。

---

## 6. 验证清单（前端集成时勾选）

- [ ] 65 KC 一一对应 65 种独立作物 sprite，无重复
- [ ] 每个章节至少 1 种代表色调验证（在 `useKcCropTheme.spec.ts` 中固定快照）
- [ ] LPC spritesheet 切片 atlas 已生成（推荐用 `texture-atlas` 工具按 5 帧 × 50 列布局生成 JSON）
- [ ] CC-BY-SA / CC-BY / CC0 三种 license 均在 UI 角落「素材致谢」页面正确展示
- [ ] 灰阶 withered 合成在 `ImageProcessor.toGrayscale` 单测里验证
- [ ] KC → crop 映射读取自 `frontend/src/data/kc_crop_mapping.ts`（从本 Markdown 生成）
