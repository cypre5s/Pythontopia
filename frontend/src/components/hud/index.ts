// R-13 Phase 6 收纳：HUD 组件统一 barrel export，方便 WorldPage 等上层组件
// 用一行 import 拿全套：
//   import { HudShell, WorldMinimapHud, SettingsModal, ... } from '@/components/hud';
// 同时统一对外暴露的"HUD 公共契约"在这里登记，未来新增 HUD 组件先加到本文件。
export { default as HudShell } from './HudShell.vue';
export { default as MiniMap } from './MiniMap.vue';
export { default as WorldMinimapHud } from './WorldMinimapHud.vue';
export { default as InventoryPanel } from './InventoryPanel.vue';
export { default as QuickslotPanelHost } from './QuickslotPanelHost.vue';
export { default as SettingsModal } from './SettingsModal.vue';
export { default as FestivalRibbon } from './FestivalRibbon.vue';
export { default as FastTravelPanel } from './FastTravelPanel.vue';
// R-13 §5：主线 + Boss
export { default as MainQuestChip } from './MainQuestChip.vue';
export { default as MainQuestPanel } from './MainQuestPanel.vue';

export type { MinimapExitMarker, MinimapExitSide } from './MiniMap.vue';
