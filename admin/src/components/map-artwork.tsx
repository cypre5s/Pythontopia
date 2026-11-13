import { useEffect, useRef, useState } from "react";
import { paintMapPreview } from "../../../shared/world/mapRender";
function mapAssetHref(path: string) {
  return import.meta.env.DEV ? `${import.meta.env.BASE_URL.replace(/\/$/, "")}${path}` : path;
}

export function MapArtwork({ mapKey, width, height }: { mapKey: string; width: number; height: number }) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const [status, setStatus] = useState<"loading" | "ready" | "failed">("loading");
  useEffect(() => {
    const element = canvas.current;
    if (!element) return;
    const controller = new AbortController();
    void paintMapPreview(element, mapKey, controller.signal, { x: 0, y: 0, width, height }, mapAssetHref)
      .then(() => { if (!controller.signal.aborted) setStatus("ready"); })
      .catch(() => { if (!controller.signal.aborted) setStatus("failed"); });
    return () => controller.abort();
  }, [mapKey, width, height]);
  return (
    <>
      <foreignObject width={width} height={height} pointerEvents="none" aria-hidden="true">
        <canvas
          ref={canvas}
          className="block size-full [image-rendering:pixelated]"
          width={width * 16}
          height={height * 16}
          data-map-art={status}
        />
      </foreignObject>
      {status !== "ready" && (
        <text x={width / 2} y={1.1} textAnchor="middle" fontSize={0.7} fill="#465849" role="status" pointerEvents="none">
          {status === "loading" ? "正在加载地图底图…" : "底图加载失败，仍可编辑地图标记。"}
        </text>
      )}
    </>
  );
}
