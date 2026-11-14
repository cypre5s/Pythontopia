import { useCallback, useEffect, useRef, useState } from "react";
import { AlertTriangle, Check, ChevronLeft, ChevronRight, ListChecks, Play, RotateCcw, Save, Sparkles, Trash2, Volume2, X } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { ErrorNotice, Loading } from "@/components/common";
import { errorMessage, fileBlob } from "@/lib/api";
import {
  narrationApi,
  type CalibrationReport,
  type NarrationAudioStatus,
  type NarrationList,
  type NarrationPage,
  type NarrationQuota,
} from "@/lib/narration";

const STATUS: Record<string, { label: string; tone: string }> = {
  draft: { label: "待审", tone: "bg-amber-100 text-amber-900" },
  accepted: { label: "已接受", tone: "bg-emerald-100 text-emerald-900" },
  rejected: { label: "已拒绝", tone: "bg-rose-100 text-rose-900" },
  stale: { label: "已失效", tone: "bg-slate-200 text-slate-700" },
  unavailable: { label: "无法生成", tone: "bg-slate-200 text-slate-700" },
};

function StatusBadge({ status }: { status: string | undefined }) {
  if (!status) return <span className="text-xs text-muted-foreground">未生成</span>;
  const item = STATUS[status] ?? { label: status, tone: "bg-slate-100 text-slate-800" };
  return <span className={`rounded px-2 py-0.5 text-xs ${item.tone}`}>{item.label}</span>;
}

export function NarrationReview({
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
  const [quota, setQuota] = useState<NarrationQuota | null>(null);
  const [report, setReport] = useState<CalibrationReport | null>(null);
  const [audioState, setAudioState] = useState<NarrationAudioStatus | null>(null);
  const [clip, setClip] = useState("");
  const [pageNo, setPageNo] = useState<number | null>(null);
  const [draft, setDraft] = useState("");
  const [busy, setBusy] = useState("");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [imageFailed, setImageFailed] = useState(false);
  const [job, setJob] = useState<{ running: boolean; kind: string; done: number; total: number; message: string } | null>(null);
  const [pageImage, setPageImage] = useState("");
  const audioGeneration = useRef(0);

  const load = useCallback(async () => {
    setBusy("load");
    setError("");
    try {
      const [list, limits, sound] = await Promise.all([
        narrationApi.list(classroomId, materialId),
        narrationApi.quota(classroomId, materialId),
        narrationApi.audioStatus(classroomId, materialId),
      ]);
      setData(list);
      setQuota(limits);
      setAudioState(sound);
      setPageNo((current) => current ?? list.pages[0]?.page_no ?? null);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy("");
    }
  }, [classroomId, materialId]);

  useEffect(() => {
    void load();
  }, [load]);

  const page: NarrationPage | undefined = data?.pages.find((item) => item.page_no === pageNo);
  const saved = page?.narration?.text ?? "";
  const dirty = pageNo !== null && draft !== saved;

  useEffect(() => {
    setDraft(saved);
    setImageFailed(false);
  }, [pageNo, saved]);

  useEffect(() => {
    let stopped = false;
    const tick = async () => {
      try {
        const state = await narrationApi.job(classroomId, materialId);
        if (!stopped) setJob(state);
      } catch {
        // 状态查询失败不影响审校本身
      }
    };
    void tick();
    const timer = window.setInterval(() => void tick(), 3000);
    return () => {
      stopped = true;
      window.clearInterval(timer);
    };
  }, [classroomId, materialId]);

  useEffect(() => {
    // 切页必须停掉上一页：否则点过试听后翻页，旧音频会继续播放，看起来像"这一页在放"。
    ++audioGeneration.current;
    setClip("");
    return () => { ++audioGeneration.current; };
  }, [pageNo]);

  useEffect(() => () => { if (clip) URL.revokeObjectURL(clip); }, [clip]);

  useEffect(() => {
    if (pageNo === null) return;
    let dropped = false;
    setPageImage((previous) => {
      if (previous) URL.revokeObjectURL(previous);
      return "";
    });
    void (async () => {
      try {
        const blob = await fileBlob(narrationApi.pageImage(classroomId, materialId, pageNo));
        if (dropped) return;
        setPageImage(URL.createObjectURL(blob));
      } catch {
        if (!dropped) setImageFailed(true);
      }
    })();
    return () => {
      dropped = true;
    };
  }, [classroomId, materialId, pageNo]);

  useEffect(
    () => () => {
      setPageImage((previous) => {
        if (previous) URL.revokeObjectURL(previous);
        return "";
      });
    },
    [],
  );

  async function run(label: string, action: () => Promise<unknown>, done: string) {
    setBusy(label);
    setError("");
    setMessage("");
    try {
      await action();
      setMessage(done);
      await load();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy("");
    }
  }

  // 一页要 3–15 秒，整册串行会超过网关的读超时（90s）。按小批提交：能显示进度，
  // 中途失败也能直接重试（服务端按输入哈希跳过了已生成的页，不会重复计费）。
  async function generateWholeDeck() {
    if (!data) return;
    const total = data.coverage.pages;
    const chunk = 4;
    const failures: string[] = [];
    setBusy("all");
    setError("");
    setMessage("");
    try {
      for (let first = 1; first <= total; first += chunk) {
        const last = Math.min(first + chunk - 1, total);
        setMessage(`正在生成第 ${first}–${last} 页（共 ${total} 页）…`);
        const { results } = await narrationApi.generateRange(classroomId, materialId, first, last);
        for (const item of results) {
          if (item.status === "failed") failures.push(`第 ${item.page_no} 页：${item.error ?? "未知错误"}`);
        }
        await load();
      }
      setMessage(failures.length ? `整份生成结束，${failures.length} 页失败，可重试` : "整份生成已结束");
      if (failures.length) setError(failures.slice(0, 5).join("；"));
    } catch (e) {
      setError(errorMessage(e));
      setMessage("已生成的页已保存，可直接重试（不会重复计费）");
    } finally {
      setBusy("");
      await load().catch(() => {});
    }
  }

  async function acceptAllDrafts() {
    if (!data) return;
    const drafts = data.pages.filter((item) => item.narration?.status === "draft").length;
    if (!drafts) {
      setMessage("没有待审的草稿");
      return;
    }
    if (!window.confirm(`一次接受全部 ${drafts} 页待审草稿？之后仍可在右侧单独驳回。`)) return;
    await run("accept-all", () => narrationApi.acceptBatch(classroomId, materialId), `已接受 ${drafts} 页`);
  }

  async function synthesizeAllAudio() {
    if (!data) return;
    const accepted = data.pages.filter((item) => item.narration?.status === "accepted").length;
    if (!accepted) {
      setMessage("还没有已接受的页，先接受再合成音频");
      return;
    }
    if (!window.confirm(`为 ${accepted} 页已接受的讲解合成音频？已合成的页会复用，不重复计费。`)) return;
    const total = data.coverage.pages;
    const chunk = 4;
    setBusy("audio-all");
    setError("");
    try {
      for (let first = 1; first <= total; first += chunk) {
        const last = Math.min(first + chunk - 1, total);
        setMessage(`正在合成第 ${first}–${last} 页音频（共 ${total} 页）…`);
        const result = await narrationApi.audioBatch(classroomId, materialId, first, last);
        if (result.failed?.length) {
          setError(result.failed.map((item) => `第 ${item.page_no} 页：${item.error}`).slice(0, 5).join("；"));
        }
        await load();
      }
      setMessage("音频批量合成结束");
    } catch (e) {
      setError(errorMessage(e));
      setMessage("已合成的音频已保存，可直接重试");
    } finally {
      setBusy("");
      await load().catch(() => {});
    }
  }

  const running = Boolean(job?.running);
  const busyNow = Boolean(busy) || running;
  const audioPage = audioState?.pages.find((item) => item.page_no === pageNo);
  const navigation = () => {
    const pages = data?.pages ?? [];
    const index = pages.findIndex((item) => item.page_no === pageNo);
    return {
      previous: index > 0 ? pages[index - 1].page_no : null,
      next: index >= 0 && index < pages.length - 1 ? pages[index + 1].page_no : null,
    };
  };
  const { previous, next } = navigation();

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-6xl max-h-[92vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>逐页讲解 · {title}</DialogTitle>
          <DialogDescription>
            AI 草稿需要老师逐页接受才会出现在学生端；页面原文变化后旧审校会自动失效。
          </DialogDescription>
        </DialogHeader>
        <ErrorNotice message={error} />
        {message && !dirty && <p role="status" className="hint">{message}</p>}
        {!data || busy === "load" ? (
          <Loading />
        ) : (
          <>
            <div className="flex flex-wrap items-center gap-2 text-sm">
              <Badge variant="secondary">共 {data.coverage.pages} 页</Badge>
              <Badge variant="secondary">已生成 {data.coverage.covered}</Badge>
              <Badge variant="secondary">待审 {data.coverage.draft}</Badge>
              <Badge variant="secondary">已接受 {data.coverage.accepted}</Badge>
              {data.coverage.stale > 0 && <Badge variant="secondary">已失效 {data.coverage.stale}</Badge>}
              {data.coverage.missing > 0 && <Badge variant="secondary">未生成 {data.coverage.missing}</Badge>}
              {running && (
                <Badge variant="secondary" className="animate-pulse">
                  正在处理：{job?.kind === "audio" ? "音频" : job?.kind === "calibrate" ? "校准" : "生成"}
                  {job && job.total ? ` ${job.done}/${job.total}` : ""}
                </Badge>
              )}
              {quota && (
                <span className="hint">
                  今日已生成 {quota.used_today} / {quota.daily_limit}
                  {!quota.available && " · 作者期模型未配置，暂不可生成"}
                </span>
              )}
            </div>
            <div className="flex flex-wrap items-end gap-2">
              <Button
                disabled={!quota?.available || busyNow}
                onClick={() => {
                  if (!window.confirm(`将为全部 ${data.coverage.pages} 页生成讲解，已生成的页不会重复计费。继续？`)) return;
                  void generateWholeDeck();
                }}
              >
                <Sparkles /> {busy === "all" ? "正在生成…" : "整份生成"}
              </Button>
              <Button
                variant="outline"
                disabled={busyNow || data.coverage.draft === 0}
                onClick={() => void acceptAllDrafts()}
              >
                <Check /> {busy === "accept-all" ? "接受中…" : `接受全部草稿（${data.coverage.draft}）`}
              </Button>
              <Button
                variant="outline"
                disabled={busyNow || audioState?.available === false || data.coverage.accepted === 0}
                onClick={() => void synthesizeAllAudio()}
              >
                <Volume2 /> {busy === "audio-all" ? "合成中…" : "批量合成音频"}
              </Button>
              <Button
                variant="outline"
                disabled={!quota?.available || busyNow || data.coverage.covered === 0}
                onClick={() => void run("calibrate", async () => setReport(await narrationApi.calibrate(classroomId, materialId)), "校准报告已生成")}
              >
                <ListChecks /> {busy === "calibrate" ? "正在校准…" : "全篇校准"}
              </Button>
              {report && (
                <Button
                  variant="outline"
                  disabled={Boolean(busy) || !(report.issues ?? []).length}
                  onClick={() =>
                    void run(
                      "apply",
                      async () => {
                        const applied = await narrationApi.applyCalibration(classroomId, materialId);
                        setReport((current) => (current ? { ...current, applied_pages: applied ?? null } : current));
                      },
                      "已按报告重生成草稿页",
                    )
                  }
                >
                  <RotateCcw /> 应用校准（只改草稿）
                </Button>
              )}
            </div>
            {report && (
              <section className="surface space-y-2 p-4 text-sm">
                <p className="font-medium">校准报告 · {report.windows} 个窗口 · {report.pages} 页</p>
                <p className="whitespace-pre-wrap">{report.summary || "（无摘要）"}</p>
                {(report.terms ?? []).length > 0 && <p className="hint">术语：{report.terms.join("、")}</p>}
                {(report.issues ?? []).length === 0 ? (
                  <p className="hint">没有发现问题。</p>
                ) : (
                  <ul className="space-y-1">
                    {(report.issues ?? []).map((issue) => (
                      <li key={`${issue.page_no}-${issue.kind}`} className="flex items-start gap-2">
                        <button type="button" className="underline" onClick={() => setPageNo(issue.page_no)}>
                          第 {issue.page_no} 页
                        </button>
                        <span className="hint">
                          {issue.kind === "mismatch" ? "与页面不符" : issue.kind === "term" ? "术语不统一" : "连贯性"} ·{" "}
                          {issue.status === "accepted" ? "已接受，需老师确认" : "草稿页，可自动重生成"}
                        </span>
                        <span>{issue.detail}</span>
                      </li>
                    ))}
                  </ul>
                )}
              </section>
            )}
            <div className="grid gap-4 lg:grid-cols-[140px_minmax(0,1fr)_420px]">
              <div className="max-h-[66vh] space-y-1 overflow-y-auto pr-1">
                {data.pages.map((item) => (
                  <button
                    key={item.page_no}
                    type="button"
                    aria-current={item.page_no === pageNo}
                    className={`flex w-full items-center justify-between rounded border px-2 py-1 text-left text-sm ${
                      item.page_no === pageNo ? "border-primary bg-primary/5" : "border-transparent hover:bg-muted"
                    }`}
                    onClick={() => setPageNo(item.page_no)}
                  >
                    <span>第 {item.page_no} 页</span>
                    <StatusBadge status={item.narration?.status} />
                  </button>
                ))}
              </div>

              {page ? (
                <>
                  <div className="space-y-2">
                    <div className="flex flex-wrap items-center gap-2 text-sm">
                      <Button size="sm" variant="ghost" disabled={!previous} onClick={() => previous && setPageNo(previous)}>
                        <ChevronLeft /> 上一页
                      </Button>
                      <Button size="sm" variant="ghost" disabled={!next} onClick={() => next && setPageNo(next)}>
                        下一页 <ChevronRight />
                      </Button>
                      <span className="hint">第 {page.page_no} / {data.coverage.pages} 页</span>
                      <Input
                        type="number"
                        aria-label="跳到页码"
                        className="ml-auto w-20"
                        min={1}
                        max={data.coverage.pages}
                        placeholder="跳页"
                        onKeyDown={(event) => {
                          if (event.key !== "Enter") return;
                          const target = Number((event.target as HTMLInputElement).value);
                          if (data.pages.some((item) => item.page_no === target)) setPageNo(target);
                        }}
                      />
                    </div>
                    <div className="flex max-h-[62vh] min-h-[280px] items-center justify-center overflow-auto rounded border bg-muted/30 p-2">
                      {imageFailed ? (
                        <p className="hint px-4 text-center">
                          这一页没有可渲染的页面图像（文本资料或转换缺少预览）。
                          <br />
                          右侧仍可查看原文片段与讲解。
                        </p>
                      ) : (
                        <img
                          src={pageImage}
                          alt={`第 ${page.page_no} 页`}
                          className="max-h-[60vh] w-auto max-w-full"
                          onError={() => setImageFailed(true)}
                        />
                      )}
                    </div>
                    <details className="text-sm">
                      <summary className="cursor-pointer">页面原文（{page.source_chars} 字）</summary>
                      <pre className="mt-1 max-h-32 overflow-y-auto whitespace-pre-wrap rounded border p-2 text-xs">
                        {page.source_text || "（无文本层，内容在页面图像里）"}
                      </pre>
                    </details>
                  </div>

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
                      {draft.trim().length} 字（约 {Math.round(draft.trim().length / 4.2)} 秒）
                      {audioPage?.seconds ? ` · 实际音频 ${audioPage.seconds} 秒` : ""}
                    </p>
                    {page.narration?.anchors?.length ? (
                      <p className="hint">anchors：{page.narration.anchors.join(" / ")}</p>
                    ) : null}
                    <div className="flex flex-wrap gap-2">
                      <Button
                        size="sm"
                        disabled={!quota?.available || busyNow}
                        onClick={() => void run("generate", () => narrationApi.generatePage(classroomId, materialId, page.page_no), "本页草稿已生成")}
                      >
                        <Sparkles /> {busy === "generate" ? "生成中…" : "生成本页"}
                      </Button>
                      <Button
                        size="sm"
                        variant="outline"
                        disabled={Boolean(busy) || !dirty}
                        onClick={() => void run("save", () => narrationApi.save(classroomId, materialId, page.page_no, draft), "已保存修改")}
                      >
                        <Save /> 保存修改
                      </Button>
                      <Button
                        size="sm"
                        disabled={Boolean(busy) || dirty || !draft.trim() || page.narration?.status === "accepted"}
                        title={dirty ? "先保存修改再接受" : undefined}
                        onClick={() => void run("accept", () => narrationApi.accept(classroomId, materialId, page.page_no), "本页讲解已接受")}
                      >
                        <Check /> 接受
                      </Button>
                      <Button
                        size="sm"
                        variant="outline"
                        disabled={Boolean(busy) || dirty || !page.narration}
                        onClick={() => void run("reject", () => narrationApi.reject(classroomId, materialId, page.page_no), "本页讲解已拒绝")}
                      >
                        <X /> 拒绝
                      </Button>
                      <Button
                        size="sm"
                        variant="ghost"
                        disabled={Boolean(busy) || !page.narration}
                        onClick={() => void run("remove", () => narrationApi.remove(classroomId, materialId, page.page_no), "本页讲解已删除")}
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
                          <Badge variant="secondary">{audioPage.seconds ? `约 ${audioPage.seconds} 秒` : "已就绪"}</Badge>
                        ) : (
                          <Badge variant="secondary">未生成</Badge>
                        )}
                        <Button
                          size="sm"
                          variant="outline"
                          disabled={Boolean(busy) || audioState?.available === false || page.narration?.status !== "accepted"}
                          title={page.narration?.status !== "accepted" ? "先接受本页讲解再生成音频" : undefined}
                          onClick={() =>
                            void run(
                              "audio",
                              async () => {
                                await narrationApi.synthesizeAudio(classroomId, materialId, page.page_no);
                                // 旧录音已经作废，播放器必须清掉，否则听到的是上一版。
                                ++audioGeneration.current;
                                setClip("");
                              },
                              "本页音频已生成",
                            )
                          }
                        >
                          <Volume2 /> {busy === "audio" ? "合成中…" : audioPage?.ready ? "重新合成" : "生成音频"}
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
                                const blob = await fileBlob(narrationApi.audioFile(classroomId, materialId, page.page_no));
                                if (ticket === audioGeneration.current) setClip(URL.createObjectURL(blob));
                              } catch (e) {
                                if (ticket === audioGeneration.current) setError(errorMessage(e));
                              } finally {
                                setBusy("");
                              }
                            })()
                          }
                        >
                          <Play /> 试听
                        </Button>
                      </div>
                      {clip && <audio controls autoPlay src={clip} className="w-full" aria-label="讲解音频试听" />}
                      <p className="hint">音频只对已接受的讲解生成；改稿后旧录音会失效，需要重新合成。</p>
                    </section>
                  </div>
                </>
              ) : (
                <p className="hint lg:col-span-2">这份资料没有可讲解的页。</p>
              )}
            </div>
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}
