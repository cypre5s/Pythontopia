import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { narrationApi } from "@/features/materials/narration/api";
import { errorMessage, fileBlob } from "@/lib/api";
import {
  AlertTriangle,
  Check,
  Play,
  Save,
  Sparkles,
  Trash2,
  Volume2,
  X,
} from "lucide-react";
import { StatusBadge } from "./status-badge";
import type { usePageMedia } from "./use-page-media";
import type { useNarrationReview } from "./use-review";
export function NarrationPageEditor({
  review,
  media,
  classroomId,
  materialId,
}: {
  review: ReturnType<typeof useNarrationReview>;
  media: ReturnType<typeof usePageMedia>;
  classroomId: number;
  materialId: number;
}) {
  const {
    data,
    quota,
    audioState,
    draft,
    setDraft,
    busy,
    setBusy,
    setError,
    page,
    dirty,
    run,
    busyNow,
    audioPage,
  } = review;
  const { clip, setClip, audioGeneration } = media;
  if (!page || !data) return null;
  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <StatusBadge status={page.narration?.status} />
        <span className="hint">
          {page.narration?.image_used ? "生成时使用了页图" : "生成时使用文本层"}
        </span>
      </div>
      {page.narration?.stale_reason && (
        <p className="flex items-center gap-1 text-sm text-amber-700">
          <AlertTriangle size={14} /> {page.narration.stale_reason}
        </p>
      )}
      <Label htmlFor="narration-text">讲解稿</Label>
      <textarea
        id="narration-text"
        className="min-h-44 w-full rounded border p-2 text-sm"
        value={draft}
        onChange={(event) => setDraft(event.target.value)}
        placeholder="还没有草稿，可以先点“生成本页”，也可以自己写。"
      />
      <p className="hint">
        {draft.trim().length} 字（约 {Math.round(draft.trim().length / 4.2)}{" "}
        秒）
        {audioPage?.seconds ? ` · 实际音频 ${audioPage.seconds} 秒` : ""}
      </p>
      {page.narration?.anchors?.length ? (
        <p className="hint">anchors：{page.narration.anchors.join(" / ")}</p>
      ) : null}
      <div className="flex flex-wrap gap-2">
        <Button
          size="sm"
          disabled={!quota?.available || busyNow}
          onClick={() =>
            void run(
              "generate",
              () =>
                narrationApi.generatePage(
                  classroomId,
                  materialId,
                  page.page_no,
                ),
              "本页草稿已生成",
            )
          }
        >
          <Sparkles /> {busy === "generate" ? "生成中…" : "生成本页"}
        </Button>
        <Button
          size="sm"
          variant="outline"
          disabled={Boolean(busy) || !dirty}
          onClick={() =>
            void run(
              "save",
              () =>
                narrationApi.save(classroomId, materialId, page.page_no, draft),
              "已保存修改",
            )
          }
        >
          <Save /> 保存修改
        </Button>
        <Button
          size="sm"
          disabled={
            Boolean(busy) ||
            dirty ||
            !draft.trim() ||
            page.narration?.status === "accepted"
          }
          title={dirty ? "先保存修改再接受" : undefined}
          onClick={() =>
            void run(
              "accept",
              () => narrationApi.accept(classroomId, materialId, page.page_no),
              "本页讲解已接受",
            )
          }
        >
          <Check /> 接受
        </Button>
        <Button
          size="sm"
          variant="outline"
          disabled={Boolean(busy) || dirty || !page.narration}
          onClick={() =>
            void run(
              "reject",
              () => narrationApi.reject(classroomId, materialId, page.page_no),
              "本页讲解已拒绝",
            )
          }
        >
          <X /> 拒绝
        </Button>
        <Button
          size="sm"
          variant="ghost"
          disabled={Boolean(busy) || !page.narration}
          onClick={() =>
            void run(
              "remove",
              () => narrationApi.remove(classroomId, materialId, page.page_no),
              "本页讲解已删除",
            )
          }
        >
          <Trash2 /> 删除
        </Button>
      </div>
      <section className="space-y-2 rounded border p-3">
        <div className="flex flex-wrap items-center gap-2">
          <Label className="m-0">讲解音频</Label>
          <span className="hint">
            {audioState?.available === false
              ? "未配置语音合成服务"
              : audioState
                ? `音色 ${audioState.voice} · ${audioState.model}`
                : ""}
          </span>
          {audioPage?.ready ? (
            <Badge variant="secondary">
              {audioPage.seconds ? `约 ${audioPage.seconds} 秒` : "已就绪"}
            </Badge>
          ) : (
            <Badge variant="secondary">未生成</Badge>
          )}
          <Button
            size="sm"
            variant="outline"
            disabled={
              Boolean(busy) ||
              audioState?.available === false ||
              page.narration?.status !== "accepted"
            }
            title={
              page.narration?.status !== "accepted"
                ? "先接受本页讲解再生成音频"
                : undefined
            }
            onClick={() =>
              void run(
                "audio",
                async () => {
                  await narrationApi.synthesizeAudio(
                    classroomId,
                    materialId,
                    page.page_no,
                  );
                  // 旧录音已经作废，播放器必须清掉，否则听到的是上一版。
                  ++audioGeneration.current;
                  setClip("");
                },
                "本页音频已生成",
              )
            }
          >
            <Volume2 />{" "}
            {busy === "audio"
              ? "合成中…"
              : audioPage?.ready
                ? "重新合成"
                : "生成音频"}
          </Button>
          <Button
            size="sm"
            variant="ghost"
            disabled={Boolean(busy) || !audioPage?.ready}
            onClick={() =>
              void (async () => {
                const ticket = ++audioGeneration.current;
                setBusy("listen");
                setError("");
                try {
                  const blob = await fileBlob(
                    narrationApi.audioFile(
                      classroomId,
                      materialId,
                      page.page_no,
                    ),
                  );
                  if (ticket === audioGeneration.current)
                    setClip(URL.createObjectURL(blob));
                } catch (e) {
                  if (ticket === audioGeneration.current)
                    setError(errorMessage(e));
                } finally {
                  setBusy("");
                }
              })()
            }
          >
            <Play /> 试听
          </Button>
        </div>
        {clip && (
          <audio
            controls
            autoPlay
            src={clip}
            className="w-full"
            aria-label="讲解音频试听"
          />
        )}
        <p className="hint">
          音频只对已接受的讲解生成；改稿后旧录音会失效，需要重新合成。
        </p>
      </section>
    </div>
  );
}
