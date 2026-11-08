<template>
  <div ref="layout" class="learning-layout" :class="{ 'reading-only': !hasSupport, 'is-sidebar-collapsed': sidebarCollapsed, 'is-compact': compact, 'is-resizing': dragging }" :style="layoutStyle">
    <slot />
    <div v-if="hasSupport && !compact && !sidebarCollapsed" class="learning-divider">
      <div
        class="learning-resize-handle"
        role="separator"
        tabindex="0"
        aria-label="调整内容与助教宽度"
        aria-orientation="vertical"
        :aria-valuemin="22"
        :aria-valuemax="42"
        :aria-valuenow="supportWidth"
        :aria-valuetext="`助教区域宽度 ${supportWidth}%`"
        @pointerdown="startDrag"
        @pointermove="moveDrag"
        @pointerup="endDrag"
        @pointercancel="endDrag"
        @lostpointercapture="endDrag"
        @keydown="resizeWithKeys"
        @dblclick="reset"
      ><span aria-hidden="true"></span></div>
    </div>
  </div>
</template>
<script setup lang="ts">
import { computed, ref } from "vue";
import { useWorkspaceSidebar } from "../shared/workspaceSidebar";
withDefaults(defineProps<{ hasSupport?: boolean; compact?: boolean }>(), { hasSupport: true, compact: false });
const storageKey = "pythontopia-learning-layout";
const layout = ref<HTMLElement>(), supportWidth = ref(28), dragging = ref(false);
const sidebar = useWorkspaceSidebar(), sidebarCollapsed = sidebar.collapsed;
try {
  const saved = JSON.parse(localStorage.getItem(storageKey) ?? "null");
  if (Number.isFinite(saved?.width)) supportWidth.value = Math.round(Math.min(42, Math.max(22, saved.width)));
} catch { /* Display preferences are optional. */ }
const layoutStyle = computed(() => ({ "--support-width": `${supportWidth.value}%` }));
function save() {
  try { localStorage.setItem(storageKey, JSON.stringify({ width: supportWidth.value })); } catch { /* Reading remains available when storage is full. */ }
}
function startDrag(event: PointerEvent) {
  if (event.button !== 0) return;
  dragging.value = true;
  (event.currentTarget as HTMLElement).setPointerCapture?.(event.pointerId);
  event.preventDefault();
}
function moveDrag(event: PointerEvent) {
  if (!dragging.value || !layout.value) return;
  const rect = layout.value.getBoundingClientRect();
  if (rect.width <= 0) return;
  const nextWidth = (rect.right - event.clientX - 11) / rect.width * 100;
  if (nextWidth < 12) { endDrag(); sidebar.collapse(); return; }
  supportWidth.value = Math.round(Math.min(42, Math.max(22, nextWidth)));
}
function endDrag() { if (dragging.value) { dragging.value = false; save(); } }
function reset() { supportWidth.value = 28; save(); }
function resizeWithKeys(event: KeyboardEvent) {
  if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key)) return;
  event.preventDefault();
  event.stopPropagation();
  if (event.key === "End") sidebar.collapse();
  else if (event.key === "Home") reset();
  else { supportWidth.value = Math.min(42, Math.max(22, supportWidth.value + (event.key === "ArrowLeft" ? 2 : -2))); save(); }
}
</script>
<style>
.game-workspace .learning-divider { grid-column: 2; grid-row: 1; min-width: 0; position: relative; }
.game-workspace .learning-resize-handle { position: absolute; inset: 0 auto 0 50%; transform: translateX(-50%); width: 44px; display: grid; place-items: center; cursor: col-resize; touch-action: none; z-index: 2; border-radius: 8px; }
.game-workspace .learning-resize-handle > span { width: 4px; height: 50px; border-radius: 8px; background: var(--game-gold); opacity: .7; box-shadow: 0 0 0 3px #203b3266; }
.game-workspace .learning-resize-handle:is(:hover, :focus-visible) > span { opacity: 1; }
.game-workspace .learning-resize-handle:focus-visible { outline: 2px solid var(--game-gold); outline-offset: -5px; }
.game-workspace .learning-layout.is-resizing { user-select: none; cursor: col-resize; }
.game-workspace .learning-layout.is-sidebar-collapsed > .learning-support { display: none !important; }
.game-workspace .learning-layout.is-sidebar-collapsed { grid-template-columns: minmax(0, 1fr); }
.game-workspace .learning-layout.is-sidebar-collapsed > :is(.lesson-content, .practice-content) { grid-column: 1; }
</style>
