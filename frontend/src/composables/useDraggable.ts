import { computed, onMounted, ref, type CSSProperties, type ComputedRef, type Ref } from 'vue';

export interface DraggableDefaultPosition {
  top: number;
  right?: number;
  left?: number;
}

export interface UseDraggableReturn {
  style: ComputedRef<CSSProperties>;
  onPointerDown: (e: PointerEvent) => void;
}

interface SavedPosition {
  top: number;
  left: number;
}

function readSavedPosition(key: string): SavedPosition | null {
  try {
    const raw = localStorage.getItem(key);
    if (!raw) return null;
    const obj = JSON.parse(raw) as Partial<SavedPosition>;
    if (typeof obj.top === 'number' && typeof obj.left === 'number') {
      return { top: obj.top, left: obj.left };
    }
  } catch { /* 数据损坏，忽略 */ }
  return null;
}

function writeSavedPosition(key: string, pos: SavedPosition): void {
  try {
    localStorage.setItem(key, JSON.stringify(pos));
  } catch { /* 配额超限，忽略 */ }
}

function clampToViewport(top: number, left: number, elWidth: number, elHeight: number): SavedPosition {
  const vw = typeof window !== 'undefined' ? window.innerWidth : 1920;
  const vh = typeof window !== 'undefined' ? window.innerHeight : 1080;
  return {
    top: Math.max(0, Math.min(top, vh - Math.min(elHeight, vh))),
    left: Math.max(0, Math.min(left, vw - Math.min(elWidth, vw))),
  };
}

/**
 * 让 `elementRef` 元素可拖拽：位置按 `storageKey` 持久化到 localStorage 并在下次挂载恢复，
 * 拖拽过程钳制在视口内；点击按钮/链接/表单/canvas 不触发拖拽。
 */
export function useDraggable(
  elementRef: Ref<HTMLElement | null>,
  storageKey: string,
  defaultPosition: DraggableDefaultPosition,
): UseDraggableReturn {
  const posTop = ref<number | null>(null);
  const posLeft = ref<number | null>(null);
  const dragging = ref(false);

  onMounted(() => {
    const saved = readSavedPosition(storageKey);
    if (saved) {
      posTop.value = saved.top;
      posLeft.value = saved.left;
    }
  });

  const style = computed<CSSProperties>(() => {
    if (posTop.value !== null && posLeft.value !== null) {
      return {
        position: 'fixed',
        top: `${posTop.value}px`,
        left: `${posLeft.value}px`,
        right: 'auto',
        cursor: dragging.value ? 'grabbing' : undefined,
      };
    }
    const base: CSSProperties = {
      position: 'fixed',
      top: `${defaultPosition.top}px`,
    };
    if (defaultPosition.left !== undefined) {
      base.left = `${defaultPosition.left}px`;
    }
    if (defaultPosition.right !== undefined) {
      base.right = `${defaultPosition.right}px`;
    }
    return base;
  });

  function onPointerDown(e: PointerEvent): void {
    const el = elementRef.value;
    if (!el) return;
    if ((e.target as HTMLElement)?.closest?.('button, a, input, select, textarea, canvas')) return;

    e.preventDefault();
    dragging.value = true;

    const rect = el.getBoundingClientRect();
    const offsetX = e.clientX - rect.left;
    const offsetY = e.clientY - rect.top;
    const elWidth = rect.width;
    const elHeight = rect.height;

    const onMove = (me: PointerEvent): void => {
      const raw = clampToViewport(
        me.clientY - offsetY,
        me.clientX - offsetX,
        elWidth,
        elHeight,
      );
      posTop.value = raw.top;
      posLeft.value = raw.left;
    };

    const onUp = (): void => {
      dragging.value = false;
      window.removeEventListener('pointermove', onMove);
      window.removeEventListener('pointerup', onUp);
      if (posTop.value !== null && posLeft.value !== null) {
        writeSavedPosition(storageKey, { top: posTop.value, left: posLeft.value });
      }
    };

    window.addEventListener('pointermove', onMove);
    window.addEventListener('pointerup', onUp);
  }

  return { style, onPointerDown };
}
