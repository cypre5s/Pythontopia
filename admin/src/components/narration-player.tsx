import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ChevronLeft, ChevronRight, Loader2, Pause, Play, X } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { fileBlob } from "@/lib/api";
import { narrationApi, type NarrationAudioStatus, type NarrationList } from "@/lib/narration";

const SPEEDS = ["0.7", "0.8", "0.9", "1", "1.1", "1.2", "1.4", "1.6", "1.8", "2"];

/**
 * 教师试听：像学生一样从第一页连着听。
 * 整页画面 + 整段字幕 + 自动连播；只播放已接受且已合成音频的页。
 */
export function NarrationPlayer({
  classroomId,
  materialId,
  title,
  onClose,
}: {
  classroomId: number;
  materialId: number;
  title: string;
  onClose: () => void;
}) {
  const [data, setData] = useState<NarrationList | null>(null);
  const [audioState, setAudioState] = useState<NarrationAudioStatus | null>(null);
  const [pageNo, setPageNo] = useState<number | null>(null);
  const [image, setImage] = useState("");
  const [imageError, setImageError] = useState("");
  const [clip, setClip] = useState("");
  const [playing, setPlaying] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [speed, setSpeed] = useState(() => localStorage.getItem("narration-speed") ?? "1");
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const autoNext = useRef(true);

  const playable = useMemo(() => {
    const ready = new Set((audioState?.pages ?? []).filter((item) => item.ready).map((item) => item.page_no));
    return (data?.pages ?? [])
      .filter((item) => item.narration?.status === "accepted" && ready.has(item.page_no))
      .map((item) => item.page_no);
  }, [audioState, data]);

  useEffect(() => {
    let dropped = false;
    void (async () => {
      try {
        const [list, sound] = await Promise.all([
          narrationApi.list(classroomId, materialId),
          narrationApi.audioStatus(classroomId, materialId),
        ]);
        if (dropped) return;
        setData(list);
        setAudioState(sound);
        const ready = new Set(sound.pages.filter((item) => item.ready).map((item) => item.page_no));
        const first = list.pages.find((item) => item.narration?.status === "accepted" && ready.has(item.page_no));
        setPageNo(first?.page_no ?? list.pages[0]?.page_no ?? null);
      } catch (e) {
        if (!dropped) setError(e instanceof Error ? e.message : "无法加载试听数据");
      } finally {
        if (!dropped) setLoading(false);
      }
    })();
    return () => {
      dropped = true;
    };
  }, [classroomId, materialId]);

  const page = data?.pages.find((item) => item.page_no === pageNo) ?? null;
  const hasAudio = page ? playable.includes(page.page_no) : false;

  // 页面图像走 blob：<img> 带不了鉴权头。渲染偶尔会比请求慢，失败重试一次。
  useEffect(() => {
    if (pageNo === null) return;
    let dropped = false;
    setImage((previous) => {
      if (previous) URL.revokeObjectURL(previous);
      return "";
    });
    setImageError("");
    void (async () => {
      for (let attempt = 0; attempt < 2; attempt += 1) {
        try {
          const blob = await fileBlob(narrationApi.pageImage(classroomId, materialId, pageNo));
          if (!dropped) setImage(URL.createObjectURL(blob));
          return;
        } catch (e) {
          if (attempt === 1 && !dropped) {
            setImageError(e instanceof Error ? e.message : "页面图像加载失败");
          } else {
            await new Promise((resolve) => setTimeout(resolve, 600));
          }
        }
      }
    })();
    return () => {
      dropped = true;
    };
  }, [classroomId, materialId, pageNo]);

  // 音频：换页即换流，播完自动下一页
  useEffect(() => {
    if (pageNo === null || !hasAudio) {
      setClip("");
      return;
    }
    let dropped = false;
    void (async () => {
      try {
        const blob = await fileBlob(narrationApi.audioFile(classroomId, materialId, pageNo));
        if (dropped) return;
        setClip(URL.createObjectURL(blob));
      } catch (e) {
        if (!dropped) setError(e instanceof Error ? e.message : "音频加载失败");
      }
    })();
    return () => {
      dropped = true;
      setClip((previous) => {
        if (previous) URL.revokeObjectURL(previous);
        return "";
      });
    };
  }, [classroomId, materialId, pageNo, hasAudio]);

  useEffect(() => {
    if (!audioRef.current) return;
    audioRef.current.playbackRate = Number(speed);
    localStorage.setItem("narration-speed", speed);
  }, [speed, clip]);

  const go = useCallback(
    (direction: 1 | -1) => {
      if (!pageNo) return;
      const index = playable.indexOf(pageNo);
      const nextIndex = index === -1 ? 0 : index + direction;
      if (nextIndex < 0 || nextIndex >= playable.length) return;
      setPageNo(playable[nextIndex]);
    },
    [pageNo, playable],
  );

  const toggle = useCallback(async () => {
    const element = audioRef.current;
    if (!element) return;
    if (element.paused) {
      await element.play().catch(() => setPlaying(false));
    } else {
      element.pause();
    }
  }, []);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === " ") {
        event.preventDefault();
        void toggle();
      } else if (event.key === "ArrowRight") {
        go(1);
      } else if (event.key === "ArrowLeft") {
        go(-1);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [go, toggle]);

  const progress = playable.length ? Math.max(0, playable.indexOf(pageNo ?? 0) + 1) : 0;

  return (
    <div role="dialog" aria-modal="true" aria-label={`试听 ${title}`} className="fixed inset-0 z-50 flex flex-col bg-neutral-950 p-4 text-white">
      <header className="mx-auto flex w-full max-w-6xl flex-wrap items-center gap-3 pb-2">
        <span className="truncate text-sm font-medium">{title} · 试听</span>
        <span className="text-xs text-white/70">
          {progress}/{playable.length} 页可听{pageNo ? ` · 第 ${pageNo} 页` : ""}
        </span>
        <div className="ml-auto flex items-center gap-2">
          <Label htmlFor="speed" className="m-0 text-xs text-white/80">
            语速
          </Label>
          <select
            id="speed"
            className="h-8 rounded border border-white/20 bg-black/40 px-2 text-sm text-white"
            value={speed}
            onChange={(event) => setSpeed(event.target.value)}
          >
            {SPEEDS.map((value) => (
              <option key={value} value={value}>
                {value}×
              </option>
            ))}
          </select>
          <Button variant="ghost" size="icon" className="text-white hover:bg-white/10" aria-label="退出试听" onClick={onClose}>
            <X />
          </Button>
        </div>
      </header>

      <div className="mx-auto flex min-h-0 w-full max-w-6xl flex-1 flex-col items-center justify-center gap-3">
        {loading ? (
          <Loader2 className="animate-spin text-white" aria-label="正在准备试听" />
        ) : (
          <>
            <div className="flex min-h-0 flex-1 items-center justify-center">
              {image ? (
                <img src={image} alt={`第 ${pageNo} 页`} className="max-h-full max-w-full rounded object-contain" />
              ) : (
                <p className="text-sm text-white/70">{imageError || "这一页没有可预览的页面图像"}</p>
              )}
            </div>
            <p className="max-h-[22vh] w-full max-w-4xl overflow-y-auto rounded bg-black/50 p-3 text-sm leading-7 text-white/90">
              {page?.narration?.status === "accepted"
                ? page.narration.text
                : "这一页还没有已接受的讲解，试听会跳过它。"}
            </p>
          </>
        )}
      </div>

      {error && <p className="pt-2 text-center text-sm text-amber-300">{error}</p>}

      <footer className="mx-auto flex w-full max-w-6xl items-center justify-center gap-3 pt-3">
        <Button variant="secondary" size="sm" disabled={!playable.length} onClick={() => go(-1)} aria-label="上一页">
          <ChevronLeft /> 上一页
        </Button>
        <Button
          size="sm"
          disabled={!hasAudio}
          onClick={() => void toggle()}
          aria-label={playing ? "暂停" : "播放"}
        >
          {playing ? <Pause /> : <Play />} {playing ? "暂停" : "播放"}
        </Button>
        <Button variant="secondary" size="sm" disabled={!playable.length} onClick={() => go(1)} aria-label="下一页">
          下一页 <ChevronRight />
        </Button>
      </footer>

      {clip && (
        <audio
          ref={audioRef}
          src={clip}
          autoPlay
          className="hidden"
          onPlay={() => setPlaying(true)}
          onPause={() => setPlaying(false)}
          onEnded={() => {
            setPlaying(false);
            if (autoNext.current) go(1);
          }}
        />
      )}
    </div>
  );
}
