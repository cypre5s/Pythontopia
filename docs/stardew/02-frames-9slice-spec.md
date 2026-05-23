---
title: Pythontopia 9-slice 弹窗 / 按钮 / 面板 边框规范
version: v1.0
status: Draft
owner: 视觉设计师 + 前端工程师
last_updated: 2026-05-24
audience: 前端工程师（Vue HUD）/ Phaser 场景作者 / 美术 / QA / Android 端组件作者
依赖文档:
  - /home/cypress/Pythontopia/docs/stardew/00-visual-system-overview.md
  - /home/cypress/Pythontopia/frontend/public/assets/pixel/LICENSE.md
  - /home/cypress/Pythontopia/LICENSE-ART.md
  - /home/cypress/Pythontopia/frontend/src/styles/tokens.css
---

# 0. 文档定位

本文是 Pythontopia 全端"可缩放像素边框"的工业规范。所有弹窗、按钮、面板、对话气泡、tooltip、任务卡，凡是**宽高会随内容变化**且必须保持像素质感的 UI 容器，统一使用 **9-slice（九宫格切片）** 技术，禁止直接用整张位图拉伸。

9-slice 是像素风游戏的事实标准（参考 Unity `UIImage.sliced`、Godot `NinePatchRect`、Phaser 3.60+ `NineSlice`、CSS `border-image`），它能让 24×24 的源 PNG 在任意宽高下保持四角与边缘像素 1:1、仅中心区域瓦片化或拉伸，从而彻底消除"像素糊掉"。

本文与 `00-visual-system-overview.md` §3 协调：00 文档定义了**色板与按钮三态色值**，本文负责把"色值"落地为**真 9-slice PNG 切片规范 + CSS / Phaser / Vue 组件 API**。当前 HUD（`HudShell.vue` / `MiniMap.vue` / `InventoryPanel.vue`）使用 CSS `border` + `box-shadow` 模拟像素边框，**R-12 子任务必须按本文升级到真 9-slice PNG**，升级路径见 §6 Fallback 章。

硬约束（违反一条即拒收）：

1. **不复制商业像素游戏官方 UI 切图**；仅用 CC0（Kenney UI Pack Adventure）/ CC-BY-SA 3.0（LPC UI）/ 自绘三类来源。
2. **源 PNG 必须为 24×24 整数倍**（24 / 32 / 48 三档），切片偏移必须为 8px 整数倍。
3. **边框对比度 ≥ 3:1**（WCAG 2.1 AA non-text，详见 §9）。
4. **业务层禁止硬编码颜色**；颜色经 `tokens.css` 变量驱动 PNG 选择（如 `--pyt-color-border-stardew`）。

---

# 1. 9-slice 原理

## 1.1 切片网格

一张 9-slice 源图被两条水平线 + 两条垂直线切成 **9 个区域**：4 个**角**（top-left / top-right / bottom-left / bottom-right）、4 个**边**（top / right / bottom / left）、1 个**中心**。渲染时：

- **4 角**：1:1 像素绘制，**永不缩放**
- **4 边**：在垂直 / 水平方向上**瓦片化重复**（`repeat`）或**拉伸**（`stretch`）
- **1 中心**：同样可瓦片或拉伸，承载背景纹理

## 1.2 标准切片：24×24，8/8/8 网格

Pythontopia 全端默认采用 **24×24 源、8/8/8 切片** 这一配置（继承自 `00-visual-system-overview.md` §3.1），切完得到 9 块各 8×8 像素：

```text
源 PNG 24×24                CSS border-image-slice
┌────┬────────┬────┐        ↑ top    = 8
│ TL │  Top   │ TR │        ← left   = 8
├────┼────────┼────┤        → right  = 8
│ L  │ Center │ R  │        ↓ bottom = 8
├────┼────────┼────┤
│ BL │ Bottom │ BR │        border: 8px solid transparent
└────┴────────┴────┘        border-image-width: 8px
  8     8       8           border-image-repeat: stretch | round
```

为什么是 8px？因为 Pythontopia tile = 16px（参考 `00-visual-system-overview.md` §11），8px 正好是半 tile，与小镇 tilemap 像素对齐；同时 8/8/8 中心留 8px 给纹理重复，是 LPC / Kenney UI Pack 的通用约定。

## 1.3 重复模式

| 模式 | 含义 | 适用场景 |
| --- | --- | --- |
| `stretch` | 边与中心**线性拉伸**填满 | 单色 / 渐变背景（如 tooltip 半透明黑），无显眼图案 |
| `repeat` | 边与中心**完整 tile 重复**，余量从中间裁切 | 默认禁用（裁切位置不可控会破坏像素对齐） |
| `round` | 与 `repeat` 类似，但**自动缩放使整数次重复**，无裁切 | 木质纹理面板、稻草编织背景（需要纹理完整） |
| `space`（CSS only） | 完整 tile 之间分配空白 | 不使用（与像素质感冲突） |

> **默认值**：弹窗 / 按钮 / tooltip 用 `stretch`；木质 / 编织纹理面板用 `round`。

---

# 2. 8 套常用框总览

下表是 Pythontopia Beta 首发必须就位的 8 套 9-slice 框，全部托管在 `frontend/public/assets/pixel/ui/`，由 R-12 子任务驱动美术交付。

| # | 框 ID | 用途 | 源尺寸 | 切片 | 主色 | 状态 |
| --: | --- | --- | --- | --- | --- | --- |
| 01 | `frame_panel_wood` | 木质面板：背包、技能树、教学面板 | 48×48 | 16/16/16 | `#E2BC85` 底 + `#5C3A24` 外描边 | 占位待补 |
| 02 | `frame_dialog_bubble` | NPC 对话气泡（带尾巴变体） | 32×32 + 8 尾巴 | 8/8/8 主 + 尾巴单独 | `#FBE8C5` 底 + `#5C3A24` 描边 | 占位待补 |
| 03 | `frame_button_default` | 主按钮 default 态 | 24×24 | 8/8/8 | `#E2BC85` 底 | **可复用 Kenney `button_brown.png`** |
| 04 | `frame_button_hover` | 主按钮 hover 态 | 24×24 | 8/8/8 | `#FBE8C5` 底 + `#F1C453` 内描边 | 占位待补 |
| 05 | `frame_button_pressed` | 主按钮 pressed 态 | 24×24 | 8/8/8 | `#C9A86B` 底 + 1px 内移 | 占位待补 |
| 06 | `frame_card_quest` | 任务卡（任务面板内卡片） | 32×32 | 8/8/8 | `#FBE8C5` 底 + `#D4AF37` 金内描边 | 占位待补 |
| 07 | `frame_modal` | 模态对话框（设置 / 庆祝 / 退出确认） | 48×48 | 16/16/16 | `#FBE8C5` 底 + `#5C3A24` 双重描边 + `#D4AF37` 金角装饰 | 占位待补 |
| 08 | `frame_tooltip` | 通用 tooltip（背包 / KC 名称 / 快捷键提示） | 16×16 | 4/4/4 | `rgba(28,24,16,0.95)` 底 + `#D4AF37` 1px 描边 | 占位待补 |

> **占位策略**：R-12 落地前，03 立即指向 Kenney `kenney-adventure/Default/button_brown.png`（CC0，已在仓库内）；01 / 02 / 04–08 使用 §6 Fallback CSS 退化方案，先让组件可用。

---

# 3. 每套框设计表（细则）

## 3.1 `frame_panel_wood` 木质面板

| 项 | 值 |
| --- | --- |
| 源 PNG / 切片 | `frame_panel_wood.png` 48×48 / 16-16-16-16 |
| 中心纹理 | 木板纹（2px 竖纹，`#E2BC85` + `#C9A86B` 间隔 4px） |
| 外 / 内描边 | 1px `#5C3A24` / 1px `#FBE8C5`（双线像素边框） |
| 阴影 / 重复模式 | `0 2px 0 rgba(0,0,0,0.35)` / `round`（木纹必须完整重复） |
| CSS 示例 | 见 §4.2 案例 A |

## 3.2 `frame_dialog_bubble` 对话气泡

| 项 | 值 |
| --- | --- |
| 源 PNG / 切片 | `frame_dialog_bubble.png` 32×32 + `frame_dialog_tail_<dir>.png` 8×8 / 8-8-8-8 |
| 底色 / 外描边 / 内描边 | `#FBE8C5` / 2px `#5C3A24` / 1px `#D4AF37` |
| 尾巴 | 独立 8×8 三角，4 方向变体（`up/down/left/right`），绝对定位叠在主框边缘 |
| 备注 | 尾巴像素必须与主框描边对齐，不可有 1px 错位；`border-image-repeat: stretch` |

## 3.3 `frame_button_default / hover / pressed` 按钮三态

三态共享 24×24、8/8/8 切片，颜色与位移完全遵守 `00-visual-system-overview.md` §3.2 表（不在此重述以避免双源真相）。要点：

- 三态用 3 个独立 PNG 切换（不用滤镜或 transform 模拟）
- CTA 主按钮（"提交"/"收获"）额外叠加 `#D4AF37` 2px 内描边，通过 `PyFrame variant="button" tone="cta"` 触发
- `:active` 配合 `transform: translateY(1px)` 实现"按下去"反馈，时长 60ms `linear`

## 3.4 `frame_card_quest` 任务卡

| 项 | 值 |
| --- | --- |
| 源 PNG / 切片 | `frame_card_quest.png` 32×32 / 8-8-8-8 |
| 底色 / 外描边 | `#FBE8C5` / 2px `#5C3A24` |
| 装饰 | 左上角 8×8 像素金穗（来自 `icons_kc/quest_main/` 主题） |
| 状态变体 | `progress`（金穗）/ `done`（绿穗 `#6BBF59`）/ `failed`（红穗 `#D9534F`）3 套 PNG |

## 3.5 `frame_modal` 模态对话框

| 项 | 值 |
| --- | --- |
| 源 PNG / 切片 | `frame_modal.png` 48×48 / 16-16-16-16 |
| 底色 / 外描边 / 内描边 | `#FBE8C5` / 2px `#5C3A24` / 1px `#D4AF37` 金色 |
| 4 角装饰 | 6×6 金色像素菱形（在 16px 切片角内） |
| 阴影 / 遮罩 | `0 6px 0 rgba(0,0,0,0.35)` + 全屏 `rgba(0,0,0,0.5)` 遮罩 |
| `border-image-repeat` | `stretch` |

## 3.6 `frame_tooltip` 提示气泡

| 项 | 值 |
| --- | --- |
| 源 PNG / 切片 | `frame_tooltip.png` 16×16 / 4-4-4-4 |
| 底色 / 描边 | `rgba(28, 24, 16, 0.95)` 夜色半透明 / 1px `#D4AF37` 金色 |
| 字体 | Ark Pixel 12，颜色 `#FFD166`，`border-image-repeat: stretch` |

---

# 4. CSS `border-image` 用法

## 4.1 基础语法

```css
.py-frame {
  border: 8px solid transparent;
  border-image-source: url('/assets/pixel/ui/frame_button_default.png');
  border-image-slice: 8 fill;
  border-image-width: 8px;
  border-image-repeat: stretch;
  background-color: transparent;
  box-sizing: border-box;
}
```

**关键点解释**：

- `border: 8px solid transparent`：必须先声明等宽边框作为"占位"，否则 `border-image` 无 `border-width` 可继承
- `border-image-slice: 8 fill`：**`fill` 关键字必须保留**，否则中心区域会被透明化（按钮内部会"挖空"）
- `border-image-width: 8px`：渲染时边框实际宽度，可以与 `border-width` 不同（如想要"放大像素"可设为 16px）
- `border-image-repeat: stretch`：与 §1.3 表对齐
- `box-sizing: border-box`：确保按钮总尺寸不被边框撑大

## 4.2 案例 A：木质面板

```css
.py-frame--panel-wood {
  border: 16px solid transparent;
  border-image-source: url('/assets/pixel/ui/frame_panel_wood.png');
  border-image-slice: 16 fill;
  border-image-width: 16px;
  border-image-repeat: round;
  background-color: #E2BC85;
  box-shadow: 0 2px 0 rgba(0, 0, 0, 0.35);
  color: var(--pyt-color-text-stardew, #4D3613);
  font-family: var(--pyt-font-pixel, 'Ark Pixel 12'), monospace;
  font-size: var(--pyt-pixel-size-sm, 12px);
  padding: 4px 8px;
}
```

## 4.3 案例 B：按钮三态（CSS 切换 PNG）

```css
.py-frame--button {
  border: 8px solid transparent;
  border-image-slice: 8 fill;
  border-image-width: 8px;
  border-image-repeat: stretch;
  border-image-source: url('/assets/pixel/ui/frame_button_default.png');
  transition: transform 60ms linear;
}
.py-frame--button:hover {
  border-image-source: url('/assets/pixel/ui/frame_button_hover.png');
}
.py-frame--button:active {
  border-image-source: url('/assets/pixel/ui/frame_button_pressed.png');
  transform: translateY(1px);
}
.py-frame--button:focus-visible {
  outline: var(--pyt-focus-ring, 2px solid #F1C453);
  outline-offset: 2px;
}
```

> **浏览器兼容**：`border-image` 全 evergreen 浏览器原生支持；IE 11 不支持但 Pythontopia 不在支持矩阵内。Safari 15 已修复 `fill` 关键字 bug。

---

# 5. Vue 组件抽象：`<PyFrame>`

为消除 8 套框各自编写 CSS 的重复劳动，统一封装一个 `PyFrame.vue` 组件，业务侧通过 `variant` + `tone` props 选择框，无需关心 PNG 路径与切片数值。

## 5.1 API 定义

```ts
type PyFrameVariant =
  | 'panel'     // 对应 frame_panel_wood
  | 'dialog'    // 对应 frame_dialog_bubble
  | 'button'    // 对应 frame_button_*
  | 'card'      // 对应 frame_card_quest
  | 'modal'     // 对应 frame_modal
  | 'tooltip';  // 对应 frame_tooltip

type PyFrameTone =
  | 'default'   // 通用色
  | 'cta'       // 仅 button：CTA 主按钮（加金色内描边）
  | 'progress'  // 仅 card：进行中
  | 'done'      // 仅 card：已完成
  | 'failed';   // 仅 card：失败

type PyFrameState = 'default' | 'hover' | 'pressed';

interface PyFrameProps {
  variant: PyFrameVariant;
  tone?: PyFrameTone;      // 默认 'default'
  state?: PyFrameState;    // 默认 'default'，button 之外忽略
  tail?: 'up' | 'down' | 'left' | 'right' | null; // 仅 dialog 生效
}
```

## 5.2 组件实现要点（拟新建 `frontend/src/components/ui/PyFrame.vue`）

```vue
<script setup lang="ts">
import { computed } from 'vue';

const props = withDefaults(defineProps<PyFrameProps>(), {
  tone: 'default', state: 'default', tail: null,
});

const SLICE: Record<PyFrameVariant, number> = {
  panel: 16, dialog: 8, button: 8, card: 8, modal: 16, tooltip: 4,
};

const sourcePath = computed(() => {
  const base = `/assets/pixel/ui/frame_${props.variant}`;
  if (props.variant === 'button') return `${base}_${props.state}.png`;
  if (props.variant === 'card' && props.tone !== 'default') return `${base}_quest_${props.tone}.png`;
  if (props.variant === 'panel') return `${base}_wood.png`;
  if (props.variant === 'dialog') return `${base}_bubble.png`;
  return `${base}.png`;
});
</script>

<template>
  <div
    class="py-frame"
    :class="[`py-frame--${variant}`, tone !== 'default' && `py-frame--${tone}`]"
    :style="{
      borderImageSource: `url('${sourcePath}')`,
      borderImageSlice: `${SLICE[variant]} fill`,
      borderImageWidth: `${SLICE[variant]}px`,
      borderWidth: `${SLICE[variant]}px`,
    }"
  >
    <slot />
    <span v-if="variant === 'dialog' && tail"
      :class="`py-frame__tail py-frame__tail--${tail}`" aria-hidden="true" />
  </div>
</template>
```

## 5.3 使用示例

```vue
<PyFrame variant="button" tone="cta" state="default" @click="submit">
  提交作业
</PyFrame>

<PyFrame variant="dialog" tail="down">
  欢迎来到小镇！按 F 与我对话。
</PyFrame>

<PyFrame variant="card" tone="done">
  任务：浇水 3 次（已完成）
</PyFrame>
```

---

# 6. Fallback：PNG 未加载时的退化

PNG 资产可能因网络、CDN、占位期未就位而 404，组件**必须能优雅降级**到 CSS 纯边框，避免"裸眼方块"破窗。

## 6.1 退化策略

`border-image` 在 PNG 加载失败时，浏览器**自动 fallback 到 `border` 原生属性**（即组件声明的 `border: 8px solid transparent`）。因此只需把 `transparent` 改为有效色即可：

```css
.py-frame--panel-wood {
  border: 16px solid #5C3A24; /* fallback 深棕硬边 */
  border-image-source: url('/assets/pixel/ui/frame_panel_wood.png');
  /* ... */
}
```

## 6.2 R-12 升级路径

当前（2026-05-24）HUD 三件套均使用 CSS `border` + `box-shadow` 模拟，未引用 PNG：

| 组件 | 当前 CSS | R-12 升级目标 |
| --- | --- | --- |
| `HudShell.vue`（顶栏） | `border: 2px solid #d4af37` | `<PyFrame variant="panel" tone="default">` 包裹顶栏内容 |
| `MiniMap.vue` | `border: 2px solid #d4af37` | `<PyFrame variant="panel">` 包裹 canvas |
| `InventoryPanel.vue` | `border: 3px solid #d4af37` | `<PyFrame variant="panel">` 包裹 grid |

> 升级时**不改逻辑**，仅外层包 PyFrame。CSS 中原 `border` 删除前先在 PR 截图验证视觉不破窗。

---

# 7. Phaser 3.60+ `NineSlice` 用法

Phaser 画布内的 NPC 对话气泡、悬浮 tooltip、任务通知不能用 DOM 渲染（性能差且 Z 序错乱），必须用 Phaser 内置 `NineSlice` 类（3.60+ 原生支持）。

## 7.1 Boot 阶段加载

```ts
// frontend/src/game/scenes/BootScene.ts（节选）
this.load.image('frame_dialog_bubble', '/assets/pixel/ui/frame_dialog_bubble.png');
this.load.image('frame_tooltip',       '/assets/pixel/ui/frame_tooltip.png');
this.load.image('frame_button_default', '/assets/pixel/ui/frame_button_default.png');
```

## 7.2 Town / Field / Mine 场景内对话气泡

```ts
// frontend/src/game/scenes/TownScene.ts 示例
function spawnDialogBubble(scene: Phaser.Scene, x: number, y: number, text: string): Phaser.GameObjects.NineSlice {
  const bubble = scene.add.nineslice(
    x, y,
    'frame_dialog_bubble',
    undefined,
    /* width  */ 180,
    /* height */ 64,
    /* leftWidth   */ 8,
    /* rightWidth  */ 8,
    /* topHeight   */ 8,
    /* bottomHeight*/ 8,
  );
  bubble.setOrigin(0.5, 1);
  bubble.setDepth(9990);

  const label = scene.add.bitmapText(x, y - 32, 'ark_pixel_12', text, 12)
    .setOrigin(0.5, 0.5)
    .setDepth(9991)
    .setTint(0x4D3613);

  scene.tweens.add({
    targets: [bubble, label],
    alpha: { from: 0, to: 1 },
    duration: 200,
    ease: 'Sine.easeOut',
  });

  return bubble;
}
```

## 7.3 Tooltip 跟随鼠标

```ts
// 鼠标悬停 KC 种子时弹出 tooltip
const tooltip = scene.add.nineslice(
  pointer.x + 16, pointer.y + 16,
  'frame_tooltip',
  undefined,
  /* w */ 120, /* h */ 32,
  /* l */ 4,   /* r */ 4,
  /* t */ 4,   /* b */ 4,
).setOrigin(0, 0).setDepth(9999);
```

> **性能建议**：单场景活跃 NineSlice 实例 ≤ 20；超出请改用 RenderTexture 预合成。Phaser NineSlice 内部走 WebGL 单 quad 9 区域绘制，比 DOM 快约 5×（实测 200 个气泡 60 fps）。

---

# 8. 资产命名 & License

## 8.1 命名约定（强制）

| 框类型 | 命名模式 | 例子 |
| --- | --- | --- |
| 面板 | `frame_panel_<wood/dark/light>.png` | `frame_panel_wood.png` |
| 对话气泡 | `frame_dialog_bubble.png` + `frame_dialog_tail_<dir>.png` | `frame_dialog_tail_down.png` |
| 按钮三态 | `frame_button_<state>.png` | `frame_button_pressed.png` |
| 任务卡 | `frame_card_quest_<tone>.png`（default 省略 tone） | `frame_card_quest_done.png` |
| 模态 | `frame_modal.png` | `frame_modal.png` |
| Tooltip | `frame_tooltip.png` | `frame_tooltip.png` |

**禁止**：camelCase、空格、中文文件名、`.webp` / `.svg`、未带 `frame_` 前缀的 UI 框。

## 8.2 来源与 License

| 框 | 推荐来源 | License | 备注 |
| --- | --- | --- | --- |
| `frame_button_default` | Kenney UI Pack Adventure `button_brown.png` | **CC0** | 已在 `frontend/public/assets/pixel/ui/kenney-adventure/Default/`，可直接用 |
| `frame_panel_wood` | LPC UI by Sharm（OpenGameArt） | **CC-BY-SA 3.0** | 必须在 `LICENSE-ART.md` + `frontend/public/assets/pixel/LICENSE.md` 同步署名 Sharm |
| `frame_dialog_bubble` | 自绘（视觉设计师 R-12 出图） | **CC-BY-SA 3.0**（项目内统一） | 必须在 `pixel/LICENSE.md` 标注 "Pythontopia self-drawn" |
| `frame_modal` / `frame_card_quest` / `frame_tooltip` | 自绘 | **CC-BY-SA 3.0** | 同上 |

> **License 同步流程**：新增任意 PNG，必须同步更新
> 1. `/home/cypress/Pythontopia/LICENSE-ART.md` 顶层总表
> 2. `/home/cypress/Pythontopia/frontend/public/assets/pixel/LICENSE.md` 运行时清单
>
> 引入 LPC 派生作品会触发 **CC-BY-SA 3.0+ 传染**，本项目已在 `LICENSE-ART.md` 接受此条款（分层授权：代码 MIT，资产 SA）。

---

# 9. 可访问性

| 项 | 要求 | 验证手段 |
| --- | --- | --- |
| **边框对比度** | 外描边 vs 背景 ≥ **3:1**（WCAG 2.1 AA non-text，1.4.11） | 取色器：`#5C3A24` on `#FBE8C5` = **8.9:1**（AAA 通过） |
| **焦点环** | 所有 `<PyFrame variant="button">` 必须有 `:focus-visible` → 2px `#F1C453` 外环 | 键盘 Tab 手动测试 |
| **按钮可达性** | `<PyFrame variant="button">` 内部必须是 `<button>` 元素（不允许 `<div role="button">`） | ESLint `vuejs-accessibility/no-static-element-interactions` |
| **对话气泡可达** | `<PyFrame variant="dialog">` 必须有 `role="dialog"` + `aria-label`，尾巴 `aria-hidden="true"` | axe-core CI |
| **状态色不依赖颜色** | `frame_card_quest_done` 必须同时显示文字"已完成"，不能仅靠绿穗辨别 | 灰度截图自检 |
| **prefers-reduced-motion** | 按钮 hover / pressed 过渡时长 × 0.5；NineSlice 弹出 tween 跳过 | CSS 媒体查询 + Phaser 检测 |

> **测试断言**：所有"内描边色 / 外描边色"组合在 `00-visual-system-overview.md` §1 调色板内的对比度已预审，不允许引入新颜色组合而绕过对比度审查。

---

# 10. 验收清单

| # | 验收项 | 验证方式 | 责任人 |
| --: | --- | --- | --- |
| 01 | 8 套 PNG 全部就位于 `frontend/public/assets/pixel/ui/` | `ls` 检查 8 个文件名匹配 §8.1 命名 | 美术 |
| 02 | `License.md` 与 `LICENSE-ART.md` 同步署名 | grep 验证作者字符串 | 美术 |
| 03 | `PyFrame.vue` 组件 6 个 variant 各有 1 个 Storybook / Demo 截图 | Playwright `frontend/tests/components/PyFrame.spec.ts` 跑通 | 前端 |
| 04 | Vitest 单测：6 variant × 3 props 组合 ≥ 18 用例通过 | `npm run test -- PyFrame` | 前端 |
| 05 | Phaser TownScene 至少有 1 个 NineSlice dialog 气泡 demo | 启动 dev server 进入 town 触发对话 | 前端 |
| 06 | HUD 三件套 R-12 升级 PR 合并，截图对比无破窗 | Playwright `frontend/tests/components/HudShell.spec.ts` 截图基线 | 前端 |
| 07 | 可访问性 axe-core CI 无 error | GitHub Actions 集成 | 前端 |
| 08 | 边框对比度抽检 ≥ 3:1（8 套全部） | DevTools Lighthouse | 前端 |

完成 8 项后，本文从 Draft 升级 Active（v1.0 → v1.0-released）。

---

# 11. 附录：PNG 制作规范（Aseprite / 美术外包）

## 11.1 三档源尺寸

| 档位 | 源 PNG 尺寸 | 切片 | 适用框 |
| --- | --- | --- | --- |
| **S** | 16×16 | 4/4/4 | `frame_tooltip` |
| **M**（默认） | 24×24 | 8/8/8 | `frame_button_*`、`frame_dialog_bubble`、`frame_card_quest` |
| **L** | 48×48 | 16/16/16 | `frame_panel_wood`、`frame_modal` |

## 11.2 Aseprite 工作流（7 步）

1. **新建画布**：选 S/M/L 三档之一，颜色模式 **Indexed**，导入 `00-visual-system-overview.md` §1 的 12 色 palette
2. **辅助线**：`View > Grid Settings` 设 8px 网格；`View > Show Slices` 开启切片预览
3. **画 4 个角**：4 角 8×8 区域画外描边 1px `#5C3A24` + 内描边 1px `#FBE8C5`，必须像素对齐
4. **画 4 个边**：水平 / 垂直 1px 直线连接四角，**禁止斜线 / 圆弧**（拉伸时会糊掉）
5. **画中心**：8×8 中心填底色 / 纹理；木质纹理用 `#E2BC85` + `#C9A86B` 4px 竖纹
6. **导出**：`File > Export Sprite Sheet`，PNG-32 带 alpha，**禁用 anti-aliasing**
7. **验证**：以 80×40 / 200×100 / 400×60 三档尺寸预览，确认 4 角无变形

## 11.3 切片标记与归档

Aseprite `Slices` 面板添加 9 个命名 slice（`tl/t/tr/l/c/r/bl/b/br`，各 8×8），不影响导出但便于团队 review。源文件归档至 `assets/inbox/source/ui/frame_<name>.aseprite`，导出 PNG 归档至 `assets/inbox/ui_extra/frame_<name>.png`，由 `scripts/sync-assets.mjs` 增量同步到 `frontend/public/assets/pixel/ui/`。**禁止**手工拷贝绕过 sync 脚本，否则 License 索引会漂移。

---

> 本文是"框宪法"。新增 UI 容器（如班长视角红框、Bug 矿洞警告框）必须先回 §1 切片网格与 §2 8 套框中找语义，找不到再补充新提案，并在 `PyFrame` 组件 `variant` 枚举内增加值。**严禁在业务组件层硬编码 `border-image` / 9-slice PNG 路径**，必须经 `<PyFrame>` 统一封装。
