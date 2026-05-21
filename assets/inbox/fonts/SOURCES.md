# Pythontopia 中文像素字体 - 素材来源

> 子代理 #7 (像素中文字体) 下载报告 · 2026-05-21
> 任务: Pythontopia R-100 · 阶段: `assets/inbox/fonts/` 素材入库

本目录收录可商用、可嵌入 Web、可用于 Phaser BitmapFont 生成的中文像素字体。所有字体均通过 magic bytes 校验与 cmap 一致性校验。

---

## 字体 1: Fusion Pixel Font (缝合像素字体)

| 项目 | 值 |
|---|---|
| 上游仓库 | https://github.com/TakWolf/fusion-pixel-font |
| 项目主页 | https://takwolf.com / https://github.com/TakWolf |
| 作者 | TakWolf (像素字体工房) |
| Release 版本 | `2026.05.07` |
| License (字体) | **SIL Open Font License v1.1 (OFL-1.1)** — 可商用、可嵌入、可分发 |
| License (构建脚本) | MIT |
| 字符总数 | 36,511 (12px proportional 模式) |
| 上游字符覆盖文档 | https://github.com/TakWolf/fusion-pixel-font/blob/master/docs/info-12px-proportional.md |

### 设计说明

> 「该项目为方舟像素字体的临时性过渡方案。使用多个像素字体合并而成，因此以『缝合』命名。」—— 上游 README

Fusion Pixel 通过合并 Ark Pixel、Cubic 11、Galmuri、Boutique Bitmap 9x9 等开源像素字体补齐 Ark Pixel 还未完成的字形，因此 CJK 字符覆盖率显著高于 Ark Pixel。`LICENSE/` 子目录保留了所有被缝合源字体的原始 OFL 协议文本，用于法务归属。

### 已入库变体 (proportional 比例模式)

| 字号 | 变体 | 格式 | 文件大小 |
|---|---|---|---|
| 10px | latin | woff2 | 424K |
| 10px | zh_hans | woff2 | 424K |
| 10px | zh_hant | woff2 | 424K |
| 12px | latin | woff2 | 692K |
| 12px | zh_hans | woff2 | 704K |
| 12px | zh_hant | woff2 | 704K |
| 12px | zh_hans | ttf | 6.7M |

### 字符覆盖测试 (12px proportional zh_hans)

| Unicode 区块 | 覆盖率 | 数量 |
|---|---|---|
| Basic Latin (0000-007F) | 100% | 95/95 |
| Latin-1 Supplement | 100% | 94/94 |
| Latin Extended-A | 100% | 128/128 |
| CJK 符号与标点 (3000-303F) | 100% | 64/64 |
| 平假名 / 片假名 | 100% | 93/93, 96/96 |
| 注音字母 | 100% | 43/43 |
| 全/半角形式 (FF00-FFEF) | 72% | 173/240 |
| **CJK 统一表意 (4E00-9FFF)** | **91.5%** | 19,211/20,992 |
| CJK 扩展 A (3400-4DBF) | 22.5% | 1,480/6,592 |
| 谚文音节 (Hangul) | 100% | 11,172/11,172 |
| 盲文图案 | 100% | 256/256 |
| 制表符/方块/几何形状 | 100% | 256/256 |

### Pythontopia 教学词汇覆盖测试

- 课程标题 "Python语言基础"：**100%**
- 章节关键词 (变量/循环/列表/函数/类/异常/模块 ...)：**100%**
- 对话气泡常用词 (你好/欢迎/像素小镇/执行代码/输出结果 ...)：**100%**
- UI 标签 (学习地图/任务/背包/技能/存档 ...)：**100%**
- 进阶概念 (嵌套/迭代/抽象/继承/多态/装饰器/推导式/递归/协程 ...)：**100%**
- 算法/调试/网络/数据库/Web/测试/部署高频词：**100%**

### GB / Unicode 标准对照

| 标准 | 覆盖率 | 备注 |
|---|---|---|
| CJK 前 3500 常用字 (国家通用规范) | **94.80%** (3318/3500) | 缺极少数生僻字 |
| CJK 前 6763 字 (≈GB2312) | **93.92%** (6352/6763) | 几乎全覆盖 |
| GB18030 主区 (CJK 4E00-9FFF) | **91.5%** | 满足教育场景 |
| Unicode CJK 扩展 A | 22.5% | 仅生僻字缺失 |

---

## 字体 2: Ark Pixel Font (方舟像素字体)

| 项目 | 值 |
|---|---|
| 上游仓库 | https://github.com/TakWolf/ark-pixel-font |
| 项目主页 | https://takwolf.itch.io/ark-pixel-font |
| 作者 | TakWolf (像素字体工房) |
| Release 版本 | `2026.05.07` |
| License (字体) | **SIL Open Font License v1.1 (OFL-1.1)** — 可商用、可嵌入、可分发 |
| License (构建脚本) | MIT |
| 字符总数 | 24,427 (12px proportional 模式) |
| 上游字符覆盖文档 | https://github.com/TakWolf/ark-pixel-font/blob/master/docs/info-12px-proportional.md |

### 设计说明

> 「开源的泛中日韩像素字体，黑体风格。该字体目前处于积极开发阶段，仍然缺少大量汉字。」—— 上游 README

Ark Pixel 是 TakWolf 完全原创设计的像素字体，字形规整、风格一致。但因仍在开发中，CJK 覆盖率低于 Fusion Pixel，部分常用字仍缺失。**上游官方建议**：8/10/12px 临时使用 Fusion Pixel 替代，16px 临时使用 Unifont 替代。

### 已入库变体 (proportional 比例模式)

| 字号 | 变体 | 格式 | 文件大小 |
|---|---|---|---|
| 10px | latin | woff2 | 80K |
| 10px | zh_cn | woff2 | 76K |
| 10px | zh_tw | woff2 | 76K |
| 12px | latin | woff2 | 552K |
| 12px | zh_cn | woff2 | 564K |
| 12px | zh_tw | woff2 | 580K |
| 16px | latin | woff2 | 60K |
| 16px | zh_cn | woff2 | 60K |
| 16px | zh_tw | woff2 | 60K |
| 12px | zh_cn | ttf | 4.8M |

### 字符覆盖测试 (12px proportional zh_cn)

| Unicode 区块 | 覆盖率 | 数量 |
|---|---|---|
| Basic Latin (0000-007F) | 100% | 95/95 |
| Latin-1 Supplement / Extended-A | 100% | — |
| CJK 符号与标点 (3000-303F) | 100% | 64/64 |
| 平假名 / 片假名 | 100% | 93/93, 96/96 |
| **CJK 统一表意 (4E00-9FFF)** | **87.2%** | 18,299/20,992 |
| CJK 扩展 A (3400-4DBF) | 22.5% | 1,480/6,592 |
| 竖排形式 (FE10-FE1F) | 100% | 10/10 |

### Pythontopia 教学词汇覆盖测试

- 课程标题/章节/UI 标签：**100%**
- 对话气泡 (含"执行代码")：**26/27** (缺 `执`，影响"执行"等关键词)
- 进阶概念 (嵌套/迭代 ...)：缺 `嵌` (影响"嵌套")
- 算法：缺 `溯` (影响"回溯")
- 调试/数据库：缺 `滚` (影响"回滚")
- 部署：缺 `警` (影响"告警")
- 测试：缺 `雾` (影响"烟雾测试")

**结论**: Ark Pixel 在 Pythontopia 高频词汇中有 5+ 个缺字，**不能作为主力字体**。

### GB / Unicode 标准对照

| 标准 | 覆盖率 | 备注 |
|---|---|---|
| CJK 前 3500 常用字 | 92.00% (3220/3500) | 略低于 Fusion |
| CJK 前 6763 字 (≈GB2312) | 90.31% (6108/6763) | 略低于 Fusion |
| GB18030 主区 | 87.2% | 满足通用场景 |
| **Ark 16px CJK** | **0.5%** (95/20992) | **几乎无中文，仅拉丁/假名/符号** |
| **Ark 10px CJK** | **5.1%** (1076/20992) | **不可用于中文** |

---

## 二者对照与选型建议

| 维度 | Fusion Pixel | Ark Pixel |
|---|---|---|
| 设计理念 | 缝合多源补全 | 完全原创 |
| 字符总数 (12px) | **36,511** | 24,427 |
| GB2312 覆盖 (12px) | **93.92%** | 90.31% |
| Pythontopia 教学词汇 (12px) | **100% 全覆盖** | 缺 5+ 关键字 |
| 字形一致性 | 中等 (多源拼接) | **高** (一致风格) |
| 大字号 (16px) | 不提供 | 仅拉丁可用 |
| 适用 | 主力中文渲染 | 标题/英文/装饰 |

**Pythontopia 主选**: Fusion Pixel 12px proportional (zh_hans)

---

## 文件完整性校验

所有 woff2 文件 magic bytes 均为 `77 4F 46 32` (wOF2)，所有 ttf 文件 magic bytes 均为 `00 01 00 00`。

```
Fusion: 21 files (LICENSE×4 + woff2×6 + ttf×1), 11MB
Ark:    11 files (LICENSE×1 + woff2×9 + ttf×1), 7MB
```

woff2 与 TTF 的 cmap 表完全一致 (Fusion 36511/36511, Ark 24427/24427)。

---

## Web @font-face 兼容性

| 浏览器 | WOFF2 支持版本 | 备注 |
|---|---|---|
| Chrome | 36+ (2014) | 主流支持 |
| Firefox | 39+ (2015) | 主流支持 |
| Safari | 12+ (2018) | iOS 12+ 同步 |
| Edge | 14+ (2016) | Chromium 全支持 |
| Opera | 23+ (2014) | — |

所有目标用户浏览器均原生支持 WOFF2，无需 fallback。

TTF 文件仅用于：
- Phaser 3 `BitmapFont` 离线生成 (`.fnt` + texture atlas)
- 设计稿/Figma 嵌入
- 备用 src 链 (`url(*.ttf) format('truetype')`)

---

## 注意事项 (Compliance)

1. **OFL-1.1 要求**:
   - 不得单独售卖字体 (only as part of larger software package)
   - 字体衍生品仍须 OFL-1.1
   - 修改字形需改名 (Reserved Font Name)
   - **必须保留 OFL 协议全文在分发物中**

2. **Fusion Pixel 的多源协议**:
   - `LICENSE/ark-pixel.txt` — Ark Pixel OFL
   - `LICENSE/cubic-11.txt` — Cubic 11 OFL
   - `LICENSE/galmuri.txt` — Galmuri OFL
   - 因 Fusion 是缝合体，必须同时保留所有源字体的协议

3. **Pythontopia 引用建议** (在 `frontend/public/fonts/README.md` 或 `LICENSES` 文件):
   ```
   Fusion Pixel Font © 2022 TakWolf, licensed under SIL OFL 1.1
   https://github.com/TakWolf/fusion-pixel-font
   
   Ark Pixel Font © 2021 TakWolf, licensed under SIL OFL 1.1
   https://github.com/TakWolf/ark-pixel-font
   ```

4. **未下载**: 缝合宋 (Stitch Song) 与 Cubic 11 已被 Fusion Pixel 内含，不需单独引入。

---

## 后续工作建议

1. 将 `fusion-pixel-12px-proportional-zh_hans.woff2` 作为主力字体放入 `frontend/public/fonts/`。
2. 用 `@font-face` + `unicode-range` 分片加载 latin / zh_hans，节省带宽。
3. 用 TTF 通过 [BMFont](https://www.angelcode.com/products/bmfont/) 或 [bmfont-cli](https://github.com/Pencil-and-Cat/bmfont-cli) 离线生成 Phaser `BitmapFont` 资源。
4. 在 `docs/credits.md` 与游戏内"关于"页加入字体作者署名 (OFL 推荐做法)。
5. 缺字回退链: `Fusion 12px zh_hans → Fusion 12px zh_hant → 系统默认 sans-serif`。
