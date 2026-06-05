import { onBeforeUnmount, onMounted, ref } from 'vue';

/** 低于此宽度时侧栏纵向堆叠，停止拖拽分栏（与 LibraryPage 等媒体查询一致）。 */
export const SPLIT_LAYOUT_STACK_BREAKPOINT_PX = 1100;

export interface PaneWidthBounds {
  default: number;
  min: number;
  max: number;
}

export interface ResizableSplitConfig {
  storageKey: string;
  left: PaneWidthBounds;
  /** 三栏模式：中间栏（书房列表）。不传则为图书馆式「左固定 + 中 flex + 右固定」。 */
  center?: PaneWidthBounds;
  right?: PaneWidthBounds;
  mainMin?: number;
}

const SPLITTER_TOTAL_TWO = 20;
const SPLITTER_TOTAL_THREE = 30;

export function useResizableSplitLayout(config: ResizableSplitConfig) {
  const bodyRef = ref<HTMLElement | null>(null);
  const leftWidth = ref(config.left.default);
  const centerWidth = ref(config.center?.default ?? 0);
  const rightWidth = ref(config.right?.default ?? 0);
  const triple = Boolean(config.center);
  const mainMin = config.mainMin ?? 280;

  let dragEdge: 'left' | 'center' | 'right' | null = null;
  let dragStartX = 0;
  let dragStartLeft = 0;
  let dragStartCenter = 0;
  let dragStartRight = 0;

  function clamp(n: number, min: number, max: number): number {
    return Math.min(max, Math.max(min, n));
  }

  function loadWidths(): void {
    if (typeof window === 'undefined') return;
    try {
      const raw = window.localStorage.getItem(config.storageKey);
      if (!raw) return;
      const parsed = JSON.parse(raw) as { left?: number; center?: number; right?: number };
      if (typeof parsed.left === 'number') {
        leftWidth.value = clamp(parsed.left, config.left.min, config.left.max);
      }
      if (triple && config.center && typeof parsed.center === 'number') {
        centerWidth.value = clamp(parsed.center, config.center.min, config.center.max);
      }
      if (!triple && config.right && typeof parsed.right === 'number') {
        rightWidth.value = clamp(parsed.right, config.right.min, config.right.max);
      }
    } catch {
      /* 隐私模式下 localStorage 不可用，忽略 */
    }
  }

  function persistWidths(): void {
    if (typeof window === 'undefined') return;
    try {
      const payload = triple
        ? { left: leftWidth.value, center: centerWidth.value }
        : { left: leftWidth.value, right: rightWidth.value };
      window.localStorage.setItem(config.storageKey, JSON.stringify(payload));
    } catch {
      /* 隐私模式下 localStorage 不可用，忽略 */
    }
  }

  function splitterTotal(): number {
    return triple ? SPLITTER_TOTAL_THREE : SPLITTER_TOTAL_TWO;
  }

  function isStackedLayout(): boolean {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
      return false;
    }
    return window.matchMedia(`(max-width: ${SPLIT_LAYOUT_STACK_BREAKPOINT_PX}px)`).matches;
  }

  /** 窗口变窄时收缩左右栏，保证中间阅读区不低于 mainMin。 */
  function fitWidthsToContainer(): void {
    if (!bodyRef.value || isStackedLayout()) return;
    const total = bodyRef.value.clientWidth;
    if (total <= 0) return;
    const splitTotal = splitterTotal();
    const maxSides = total - splitTotal - mainMin;
    if (maxSides <= 0) {
      leftWidth.value = config.left.min;
      if (config.right) rightWidth.value = config.right.min;
      return;
    }
    let left = leftWidth.value;
    let right = config.right ? rightWidth.value : 0;
    if (left + right <= maxSides) return;

    const sum = left + right;
    const ratio = sum > 0 ? left / sum : 0.5;
    left = Math.round(maxSides * ratio);
    right = maxSides - left;
    left = clamp(left, config.left.min, Math.min(config.left.max, maxSides - (config.right?.min ?? 0)));
    if (config.right) {
      right = clamp(right, config.right.min, Math.min(config.right.max, maxSides - left));
      if (left + right > maxSides) {
        right = Math.max(config.right.min, maxSides - left);
      }
      rightWidth.value = right;
    }
    leftWidth.value = left;
  }

  function onPointerMove(e: PointerEvent): void {
    if (!dragEdge || !bodyRef.value) return;
    const dx = e.clientX - dragStartX;
    const total = bodyRef.value.clientWidth;
    const splitTotal = splitterTotal();

    if (dragEdge === 'left') {
      let next = dragStartLeft + dx;
      if (triple) {
        const mainAvail = total - next - centerWidth.value - splitTotal;
        if (mainAvail < mainMin) {
          next = total - centerWidth.value - splitTotal - mainMin;
        }
        leftWidth.value = clamp(next, config.left.min, config.left.max);
      } else if (config.right) {
        const mainAvail = total - next - rightWidth.value - splitTotal;
        if (mainAvail < mainMin) {
          next = total - rightWidth.value - splitTotal - mainMin;
        }
        leftWidth.value = clamp(next, config.left.min, config.left.max);
      }
      return;
    }

    if (dragEdge === 'center' && triple && config.center) {
      let next = dragStartCenter + dx;
      const mainAvail = total - leftWidth.value - next - splitTotal;
      if (mainAvail < mainMin) {
        next = total - leftWidth.value - splitTotal - mainMin;
      }
      centerWidth.value = clamp(next, config.center.min, config.center.max);
      return;
    }

    if (dragEdge === 'right' && config.right) {
      let next = dragStartRight - dx;
      const mainAvail = total - leftWidth.value - next - splitTotal;
      if (mainAvail < mainMin) {
        next = total - leftWidth.value - splitTotal - mainMin;
      }
      rightWidth.value = clamp(next, config.right.min, config.right.max);
    }
  }

  function endDrag(): void {
    dragEdge = null;
    document.body.classList.remove('split-dragging');
    window.removeEventListener('pointermove', onPointerMove);
    window.removeEventListener('pointerup', endDrag);
    window.removeEventListener('pointercancel', endDrag);
    persistWidths();
  }

  function onSplitterPointerDown(edge: 'left' | 'center' | 'right', e: PointerEvent): void {
    if (isStackedLayout()) {
      return;
    }
    dragEdge = edge;
    dragStartX = e.clientX;
    dragStartLeft = leftWidth.value;
    dragStartCenter = centerWidth.value;
    dragStartRight = rightWidth.value;
    document.body.classList.add('split-dragging');
    window.addEventListener('pointermove', onPointerMove);
    window.addEventListener('pointerup', endDrag);
    window.addEventListener('pointercancel', endDrag);
  }

  loadWidths();

  let resizeObserver: ResizeObserver | null = null;
  let stackMq: MediaQueryList | null = null;
  let onStackMqChange: (() => void) | null = null;

  onMounted(() => {
    fitWidthsToContainer();
    const el = bodyRef.value;
    if (el && typeof ResizeObserver !== 'undefined') {
      resizeObserver = new ResizeObserver(() => {
        fitWidthsToContainer();
      });
      resizeObserver.observe(el);
    }
    if (typeof window.matchMedia === 'function') {
      stackMq = window.matchMedia(`(max-width: ${SPLIT_LAYOUT_STACK_BREAKPOINT_PX}px)`);
      onStackMqChange = () => fitWidthsToContainer();
      stackMq.addEventListener('change', onStackMqChange);
    }
  });

  onBeforeUnmount(() => {
    endDrag();
    resizeObserver?.disconnect();
    resizeObserver = null;
    if (stackMq && onStackMqChange) {
      stackMq.removeEventListener('change', onStackMqChange);
    }
    stackMq = null;
    onStackMqChange = null;
  });

  return {
    bodyRef,
    leftWidth,
    centerWidth,
    rightWidth,
    onSplitterPointerDown,
    paneStyle: {
      left: () => ({ width: `${leftWidth.value}px` }),
      center: () => ({ width: `${centerWidth.value}px` }),
      right: () => ({ width: `${rightWidth.value}px` }),
    },
  };
}
