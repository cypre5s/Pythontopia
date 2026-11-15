import type { Point } from "@/features/authoring/model";
import { useRef, useState, type PointerEvent } from "react";
import { type Region } from "./geometry";
export function useMapDrawing(width: number, height: number) {
  const [draftRegion, setDraftRegion] = useState<Region | null>(null);
  const start = useRef<{ pointer: number; point: Point } | null>(null);
  function clearDraw() {
    start.current = null;
    setDraftRegion(null);
  }
  function point(
    event: PointerEvent<SVGSVGElement>,
    clampToMap = true,
  ): Point | null {
    const transform = event.currentTarget.getScreenCTM();
    if (!transform) return null;
    const position = new DOMPoint(event.clientX, event.clientY).matrixTransform(
      transform.inverse(),
    );
    if (
      !clampToMap &&
      (position.x < 0 ||
        position.x >= width ||
        position.y < 0 ||
        position.y >= height)
    ) {
      return null;
    }
    return {
      x: Math.min(width - 1, Math.max(0, Math.floor(position.x))),
      y: Math.min(height - 1, Math.max(0, Math.floor(position.y))),
    };
  }
  return {
    draftRegion,
    setDraftRegion,
    start,
    point,
    clearDraw,
    width,
    height,
  };
}
