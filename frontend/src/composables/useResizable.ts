import type { Ref } from 'vue';

export interface ResizableOptions {
  /** 最小尺寸（px） */
  min: number;
  /** 最大尺寸（px） */
  max: number;
  /** 'x' 横向拖拽改宽度；'y' 纵向拖拽改高度 */
  axis: 'x' | 'y';
  /** 手柄在被控面板的右/下侧时设 true：拖拽方向取反（往里拖变小） */
  invert?: boolean;
}

export interface UseResizableReturn {
  onPointerDown: (event: PointerEvent) => void;
}

/**
 * 把一个数值 ref（面板的像素尺寸）接到拖拽手柄上：在手柄上按下拖动即实时改尺寸，
 * 自动夹在 [min, max]。横向用 col-resize、纵向用 row-resize，拖拽期间禁选中文本。
 *
 * 用 CSS 变量驱动 grid-template，而非内联 grid-template-columns，确保窄屏 media query 仍能覆盖。
 */
export function useResizable(size: Ref<number>, opts: ResizableOptions): UseResizableReturn {
  function clamp(value: number): number {
    return Math.min(opts.max, Math.max(opts.min, value));
  }

  function onPointerDown(event: PointerEvent): void {
    // 只响应主键拖拽；忽略右键/中键。
    if (event.button !== 0) return;
    event.preventDefault();
    const startPos = opts.axis === 'x' ? event.clientX : event.clientY;
    const startSize = size.value;
    const handle = event.currentTarget as HTMLElement | null;
    handle?.setPointerCapture?.(event.pointerId);

    function onMove(moveEvent: PointerEvent): void {
      const cur = opts.axis === 'x' ? moveEvent.clientX : moveEvent.clientY;
      let delta = cur - startPos;
      if (opts.invert) delta = -delta;
      size.value = clamp(startSize + delta);
    }

    function onUp(upEvent: PointerEvent): void {
      handle?.releasePointerCapture?.(upEvent.pointerId);
      window.removeEventListener('pointermove', onMove);
      window.removeEventListener('pointerup', onUp);
      if (typeof document !== 'undefined') {
        document.body.style.removeProperty('user-select');
        document.body.style.removeProperty('cursor');
      }
    }

    if (typeof document !== 'undefined') {
      document.body.style.userSelect = 'none';
      document.body.style.cursor = opts.axis === 'x' ? 'col-resize' : 'row-resize';
    }
    window.addEventListener('pointermove', onMove);
    window.addEventListener('pointerup', onUp);
  }

  return { onPointerDown };
}
