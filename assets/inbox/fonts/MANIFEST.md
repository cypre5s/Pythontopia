# Pythontopia 字体清单 - 适用场景手册

> 子代理 #7 (像素中文字体) · 2026-05-21
> 配套文档: [`SOURCES.md`](./SOURCES.md) (来源/license/字符覆盖)

本清单按 Pythontopia 各 UI 场景给出明确的字体选型建议。所有结论基于 Unicode cmap 表实测，非估计值。

---

## 关键结论 (TL;DR)

| 场景 | 主用字体 (推荐) | 备用 | 字号 |
|---|---|---|---|
| 对话气泡 (NPC) | **Fusion 12px zh_hans** | Ark 12px zh_cn | 12px |
| 任务说明 / 课件正文 | **Fusion 12px zh_hans** | Ark 12px zh_cn | 12px |
| HUD / UI 标签 (中英混合) | **Fusion 12px zh_hans** | Ark 12px zh_cn | 12px |
| 小标签 / 物品数量 (纯英数) | Fusion 10px latin | Ark 10px latin | 10px |
| 标题 / Banner (放大显示) | **Fusion 12px zh_hans × CSS scale(2)** | — | 24px 实显 |
| 班级/任务公告 | **Fusion 12px zh_hans** | — | 12px |
| 代码片段 (像素风装饰) | Fusion 12px monospaced* | — | 12px |
| Phaser BitmapFont (画布) | **Fusion 12px zh_hans .ttf** | Ark 12px zh_cn .ttf | 12px |
| Web @font-face (主线) | **Fusion 12px zh_hans .woff2** | Ark 12px zh_cn .woff2 | 12px |

> \* 等宽 (monospaced) 变体本次未入库；若代码片段需等宽展示，建议后续单独下载 `fusion-pixel-font-12px-monospaced-otf.woff2`。代码编辑器 (Monaco) 仍用其默认等宽，不与此处冲突。

---

## 1. 对话气泡 (NPC 对话 / 旁白)

**首选**: `fusion-pixel-font/woff2/fusion-pixel-12px-proportional-zh_hans.woff2`

理由:
- 12px 是中文像素字体唯一稳定可读尺寸
- Fusion 12px zh_hans 实测覆盖 **94.80%** GB 常用 3500 字、**93.92%** GB2312 全集
- 测试 27 个 Pythontopia 高频对话词 100% 全覆盖（如"执行代码""欢迎来到像素小镇""请输入你的名字"）
- 比例排版 (proportional) 自然，符合中英混排阅读习惯

避免:
- ❌ **Ark Pixel 12px zh_cn**: 缺 `执` 字，"执行"会渲染为缺字方块
- ❌ 10px 任何变体: 覆盖率低于 60%，对话框易现缺字
- ❌ 16px 任何变体: Ark 16px 几乎无中文 (<1% CJK)

CSS 示例:
```css
@font-face {
  font-family: 'Fusion Pixel';
  src: url('/fonts/fusion-pixel-12px-proportional-zh_hans.woff2') format('woff2');
  font-display: swap;
  unicode-range: U+4E00-9FFF, U+3000-303F, U+FF00-FFEF, U+2000-206F;
}
.dialog-bubble {
  font-family: 'Fusion Pixel', sans-serif;
  font-size: 12px;
  line-height: 14px;
  letter-spacing: 0;
  -webkit-font-smoothing: none;
  font-smooth: never;
  image-rendering: pixelated;
}
```

---

## 2. 任务说明 / 学习卡片正文

**首选**: 同对话气泡 (Fusion 12px zh_hans)

理由: 教学场景需要完整覆盖 Python 课程的所有专业词汇。已实测 Fusion 12px 对以下场景 **100% 全覆盖**:

- 进阶概念: 嵌套、迭代、抽象、继承、多态、封装、重载、装饰器、推导式、生成器、闭包、递归、并发、线程、进程、协程
- 数据结构: 栈、队列、链表、树、堆、图、哈希、散列、指针、引用、深浅拷贝
- 算法: 排序、查找、冒泡、快速、归并、二分、动态规划、贪心、回溯、枚举、搜索
- 调试: 断点、单步、跟踪、日志、堆栈跟踪、异常捕获、抛出、回滚、撤销
- I/O / 网络 / 数据库 / Web / 测试 / 部署

---

## 3. HUD / 状态栏 / UI 标签

**首选**: `fusion-pixel-font/woff2/fusion-pixel-12px-proportional-zh_hans.woff2`

理由: HUD 经常出现混合内容如"金币: 12""体力 80/100""第 3 章: 流程控制"，需要中英数符号同字体一致渲染。Fusion 12px 全部覆盖。

辅助:
- 纯英数 HUD (如积分/血条数字)：可单独绑定 `fusion-pixel-10px-proportional-latin.woff2` 节省空间，再通过 `unicode-range` 限制只匹配 ASCII。

CSS 分片加载策略:
```css
@font-face {
  font-family: 'Pythontopia Pixel';
  src: url('/fonts/fusion-pixel-10px-proportional-latin.woff2') format('woff2');
  unicode-range: U+0020-007E;
  font-display: swap;
}
@font-face {
  font-family: 'Pythontopia Pixel';
  src: url('/fonts/fusion-pixel-12px-proportional-zh_hans.woff2') format('woff2');
  unicode-range: U+4E00-9FFF, U+3000-303F, U+FF00-FFEF;
  font-display: swap;
}
```

---

## 4. 标题 / Banner / 大字 (≥16px 显示)

像素字体的核心约束: **不能任意放大模糊渲染**。两个字体都没有原生 16px 中文。

**推荐方案**: Fusion 12px zh_hans + CSS `transform: scale(2)` + `image-rendering: pixelated`

```css
.title-large {
  font-family: 'Fusion Pixel';
  font-size: 12px;
  transform: scale(2); /* 实际显示 24px，保留像素硬边 */
  transform-origin: top left;
  image-rendering: pixelated;
  image-rendering: -moz-crisp-edges;
}
```

不推荐:
- ❌ Ark 16px zh_cn: 仅有 95/20992 CJK 字符，中文几乎无渲染能力
- ❌ 直接放大 (browser smoothing): 会破坏像素硬边美感

---

## 5. Phaser 3 游戏画布内文字

**首选**: `fusion-pixel-font/ttf/fusion-pixel-12px-proportional-zh_hans.ttf` (6.7M)

理由:
- Phaser 3 `BitmapText` 性能远高于动态 `Text` (Canvas 2D 文本)
- BitmapFont 需要预先生成 `.fnt` + 雪碧图，工具吃 TTF/OTF
- Fusion 12px 单语言版 (6.7M) 在打包时只需提取实际用到的字符，最终 atlas 通常 < 1MB

推荐工具链 (任选其一):
1. [BMFont (Windows)](https://www.angelcode.com/products/bmfont/)
2. [bmfont-cli (Node)](https://www.npmjs.com/package/msdf-bmfont-xml) — 可在 CI 自动跑
3. [bmFont (Python)](https://github.com/zicklag/bmfont) — 可在 Pythontopia 构建流程嵌入

预生成步骤:
```bash
# 在 frontend/scripts/build-pixel-bitmapfont.ts 中
# 1. 收集所有出现在课件/UI 中的中文字符 -> charset.txt
# 2. msdf-bmfont -o public/fonts/pixel-cn -i charset.txt fusion-pixel-12px-proportional-zh_hans.ttf
# 3. 输出: pixel-cn.png + pixel-cn.fnt -> Phaser this.load.bitmapFont(...)
```

---

## 6. Web @font-face 兼容性验证

| 文件 | 格式 | 浏览器要求 | 加载方式 |
|---|---|---|---|
| `*.woff2` | WOFF 2.0 | Chrome 36+, FF 39+, Safari 12+, Edge 14+ | 主链 `format('woff2')` |
| `*.ttf` | TrueType | 所有 | 备链 `format('truetype')` |

实测结论:
- ✅ 所有 woff2 magic bytes = `77 4F 46 32` (wOF2)，文件格式合法
- ✅ 所有 ttf magic bytes = `00 01 00 00`，TrueType 标准
- ✅ woff2 与 ttf 的 cmap 表完全一致 (Fusion 36511, Ark 24427)
- ✅ 直接通过 fontTools 解析无错误

完整 CSS 写法 (推荐):
```css
@font-face {
  font-family: 'Fusion Pixel CN';
  src: url('/fonts/fusion-pixel-12px-proportional-zh_hans.woff2') format('woff2');
  font-weight: 400;
  font-style: normal;
  font-display: swap;
}

/* Phaser canvas 文本 (DOM 模式) */
.phaser-text {
  font-family: 'Fusion Pixel CN', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-size: 12px;
  line-height: 1;
  -webkit-font-smoothing: none;
  font-smooth: never;
}
```

---

## 7. 缺字回退链 (Fallback Chain)

```
Fusion 12px zh_hans (94.8% GB3500)
  ↓ 缺字时
Fusion 12px zh_hant (繁体补充)
  ↓ 缺字时
Ark 12px zh_cn (备用风格)
  ↓ 缺字时
系统 sans-serif (浏览器默认 CJK 字体)
```

CSS:
```css
font-family:
  'Fusion Pixel CN Hans',
  'Fusion Pixel CN Hant',
  'Ark Pixel CN',
  'PingFang SC', 'Microsoft YaHei', sans-serif;
```

---

## 8. 部署清单 (从 inbox 流转到生产)

待审核通过后，建议:

| 源 (inbox) | 目标 (生产) | 用途 |
|---|---|---|
| `fusion-pixel-font/woff2/fusion-pixel-12px-proportional-zh_hans.woff2` | `frontend/public/fonts/` | Web @font-face 主字体 |
| `fusion-pixel-font/woff2/fusion-pixel-12px-proportional-latin.woff2` | `frontend/public/fonts/` | Latin subset (节省) |
| `fusion-pixel-font/ttf/fusion-pixel-12px-proportional-zh_hans.ttf` | `frontend/build-assets/` | Phaser BitmapFont 构建源 |
| `fusion-pixel-font/LICENSE-OFL.txt` | `frontend/public/fonts/LICENSES/` | 法务合规 |
| `fusion-pixel-font/LICENSE/*.txt` | `frontend/public/fonts/LICENSES/fusion/` | 缝合源协议 |
| `ark-pixel-font/woff2/ark-pixel-12px-proportional-zh_cn.woff2` | `frontend/public/fonts/` | 备用/风格替换字体 |
| `ark-pixel-font/LICENSE-OFL.txt` | `frontend/public/fonts/LICENSES/` | 法务合规 |

**未入库变体如需补充**:
- 等宽 (monospaced) 变体: 若代码示例需要像素等宽字体
- 8px 变体: 若极小 HUD 数字 (但中文不可用)
- ja/ko 变体: 若需国际化日韩

---

## 9. 不入库的字体 (任务清单备注)

| 字体 | 原因 |
|---|---|
| **Cubic 11** | 已被 Fusion Pixel 缝合包含，无需单独下载 |
| **缝合宋 (Stitch Song)** | 上游已停更，CJK 覆盖低于 Fusion Pixel；且 12px 风格冲突 |
| **Galmuri** | 韩文字体，仅含基本汉字，已被 Fusion 包含 |
| **Boutique Bitmap 9x9** | 已被 Fusion 包含 |
| **Unifont** | 上游推荐 16px 方案，但风格偏程序员字体，与小镇像素风格不匹配 |

---

## 10. 验证脚本快照

> 完整脚本: `/tmp/check_woff2_and_standards.py` (执行时间 2026-05-21)

```
=== Fusion 12px zh_hans (36511 字符) ===
  CJK 前 3500 常用字:    3318/3500 (94.80%)
  GB2312 (前 6763):      6352/6763 (93.92%)
  CJK 主区:              19211/20992 (91.5%)

=== Ark 12px zh_cn (24427 字符) ===
  CJK 前 3500 常用字:    3220/3500 (92.00%)
  GB2312 (前 6763):      6108/6763 (90.31%)
  CJK 主区:              18299/20992 (87.2%)
```

---

**最终建议**: Pythontopia Beta 仅引入 Fusion 12px zh_hans + Fusion 10px latin (HUD 英文) + 全部 LICENSE。Ark Pixel 作为可选风格切换字体保留在 inbox，需要时手动启用。
