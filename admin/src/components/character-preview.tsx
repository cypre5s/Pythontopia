import { useEffect, useState } from "react";
import { fileBlob } from "@/lib/api";
import type { CharacterAsset } from "@/lib/authoring";

export function CharacterPreview({ asset, direction = "down", animate = false, compact = false }: {
  asset?: CharacterAsset;
  direction?: "down" | "left" | "right" | "up";
  animate?: boolean;
  compact?: boolean;
}) {
  const [loaded, setLoaded] = useState<{ source: string; url: string; error?: string }>();
  const [frame, setFrame] = useState(1);
  const source = asset?.url;
  useEffect(() => {
    if (!source?.startsWith("/api/")) return;
    let active = true;
    let objectUrl: string | undefined;
    void fileBlob(source.slice(4)).then((blob) => {
      if (!active) return;
      objectUrl = URL.createObjectURL(blob);
      setLoaded({ source, url: objectUrl });
    }).catch(() => { if (active) setLoaded({ source, url: "", error: "素材加载失败" }); });
    return () => { active = false; if (objectUrl) URL.revokeObjectURL(objectUrl); };
  }, [source]);
  useEffect(() => {
    if (!animate) return;
    const timer = window.setInterval(() => setFrame((value) => (value + 1) % 3), 180);
    return () => window.clearInterval(timer);
  }, [animate]);
  if (!asset || !source) return <span className="text-sm text-muted-foreground">选择素材</span>;
  const current = loaded?.source === source ? loaded : undefined;
  const url = source.startsWith("/api/") ? current?.url : import.meta.env.DEV
    ? import.meta.env.BASE_URL.replace(/\/$/, "") + source : source;
  if (!url) return <span role="status">{current?.error ?? "加载素材…"}</span>;
  if (asset.kind === "portrait") return <img src={url} alt={asset.name} className={compact ? "h-12 w-10 shrink-0 object-contain" : "h-40 w-full object-contain"} />;
  const width = asset.frame_width ?? 16;
  const height = asset.frame_height ?? 20;
  const scale = Math.min(compact ? 2 : 4, (compact ? 48 : 128) / Math.max(width, height));
  const row = ["down", "left", "right", "up"].indexOf(direction);
  return <span role="img" aria-label={asset.name} className="block shrink-0 [image-rendering:pixelated]"
    style={{ width: width * scale, height: height * scale, backgroundImage: `url("${url}")`,
      backgroundSize: `${width * 3 * scale}px ${height * 4 * scale}px`,
      backgroundPosition: `${-(animate ? frame : 1) * width * scale}px ${-row * height * scale}px` }} />;
}
