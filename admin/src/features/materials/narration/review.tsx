import { ErrorNotice, Loading } from "@/components/common";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { narrationApi } from "@/features/materials/narration/api";
import { Check, ListChecks, RotateCcw, Sparkles, Volume2 } from "lucide-react";
import { NarrationPageEditor } from "./page-editor";
import { NarrationPagePreview } from "./page-preview";
import { StatusBadge } from "./status-badge";
import { usePageMedia } from "./use-page-media";
import { useNarrationReview } from "./use-review";

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
  const review = useNarrationReview(classroomId, materialId);
  const {
    data,
    quota,
    report,
    setReport,
    audioState,
    pageNo,
    setPageNo,
    busy,
    error,
    message,
    job,
    page,
    saved,
    dirty,
    run,
    generateWholeDeck,
    acceptAllDrafts,
    synthesizeAllAudio,
    running,
    busyNow,
  } = review;
  const media = usePageMedia(classroomId, materialId, pageNo, saved);

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-6xl max-h-[92vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>逐页讲解 · {title}</DialogTitle>
          <DialogDescription>
            AI
            草稿需要老师逐页接受才会出现在学生端；页面原文变化后旧审校会自动失效。
          </DialogDescription>
        </DialogHeader>
        <ErrorNotice message={error} />
        {message && !dirty && (
          <p role="status" className="hint">
            {message}
          </p>
        )}
        {!data || busy === "load" ? (
          <Loading />
        ) : (
          <>
            <div className="flex flex-wrap items-center gap-2 text-sm">
              <Badge variant="secondary">共 {data.coverage.pages} 页</Badge>
              <Badge variant="secondary">已生成 {data.coverage.covered}</Badge>
              <Badge variant="secondary">待审 {data.coverage.draft}</Badge>
              <Badge variant="secondary">已接受 {data.coverage.accepted}</Badge>
              {data.coverage.stale > 0 && (
                <Badge variant="secondary">已失效 {data.coverage.stale}</Badge>
              )}
              {data.coverage.missing > 0 && (
                <Badge variant="secondary">
                  未生成 {data.coverage.missing}
                </Badge>
              )}
              {running && (
                <Badge variant="secondary" className="animate-pulse">
                  正在处理：
                  {job?.kind === "audio"
                    ? "音频"
                    : job?.kind === "calibrate"
                      ? "校准"
                      : "生成"}
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
                  if (
                    !window.confirm(
                      `将为全部 ${data.coverage.pages} 页生成讲解，已生成的页不会重复计费。继续？`,
                    )
                  )
                    return;
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
                <Check />{" "}
                {busy === "accept-all"
                  ? "接受中…"
                  : `接受全部草稿（${data.coverage.draft}）`}
              </Button>
              <Button
                variant="outline"
                disabled={
                  busyNow ||
                  audioState?.available === false ||
                  data.coverage.accepted === 0
                }
                onClick={() => void synthesizeAllAudio()}
              >
                <Volume2 /> {busy === "audio-all" ? "合成中…" : "批量合成音频"}
              </Button>
              <Button
                variant="outline"
                disabled={
                  !quota?.available || busyNow || data.coverage.covered === 0
                }
                onClick={() =>
                  void run(
                    "calibrate",
                    async () =>
                      setReport(
                        await narrationApi.calibrate(classroomId, materialId),
                      ),
                    "校准报告已生成",
                  )
                }
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
                        const applied = await narrationApi.applyCalibration(
                          classroomId,
                          materialId,
                        );
                        setReport((current) =>
                          current
                            ? { ...current, applied_pages: applied ?? null }
                            : current,
                        );
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
                <p className="font-medium">
                  校准报告 · {report.windows} 个窗口 · {report.pages} 页
                </p>
                <p className="whitespace-pre-wrap">
                  {report.summary || "（无摘要）"}
                </p>
                {(report.terms ?? []).length > 0 && (
                  <p className="hint">术语：{report.terms.join("、")}</p>
                )}
                {(report.issues ?? []).length === 0 ? (
                  <p className="hint">没有发现问题。</p>
                ) : (
                  <ul className="space-y-1">
                    {(report.issues ?? []).map((issue) => (
                      <li
                        key={`${issue.page_no}-${issue.kind}`}
                        className="flex items-start gap-2"
                      >
                        <button
                          type="button"
                          className="underline"
                          onClick={() => setPageNo(issue.page_no)}
                        >
                          第 {issue.page_no} 页
                        </button>
                        <span className="hint">
                          {issue.kind === "mismatch"
                            ? "与页面不符"
                            : issue.kind === "term"
                              ? "术语不统一"
                              : "连贯性"}{" "}
                          ·{" "}
                          {issue.status === "accepted"
                            ? "已接受，需老师确认"
                            : "草稿页，可自动重生成"}
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
                      item.page_no === pageNo
                        ? "border-primary bg-primary/5"
                        : "border-transparent hover:bg-muted"
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
                  <NarrationPagePreview
                    review={review}
                    media={media}
                    classroomId={classroomId}
                    materialId={materialId}
                  />

                  <NarrationPageEditor
                    review={review}
                    media={media}
                    classroomId={classroomId}
                    materialId={materialId}
                  />
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
