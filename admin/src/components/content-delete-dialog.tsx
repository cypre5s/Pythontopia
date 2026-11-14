import { useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Checkbox } from "@/components/ui/checkbox";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { ErrorNotice, Loading } from "@/components/common";
import { errorMessage, request, token } from "@/lib/api";

export type DeletionKind = "chapter" | "material" | "question" | "activity";
type Impact = { kind: string; id?: string; title: string; reason?: string; count?: number };
type Preview = {
  id: string; confirmation: string; remove: Impact[]; detach: Impact[]; preserve: Impact[];
  blockers: { message: string; path?: string }[]; affected_students: number; ending_sessions: number; history_policy: string;
};
const names: Record<string, string> = { chapter: "章节", material: "资料", question: "题目", activity: "活动", knowledge_point: "知识点", quest: "任务", story: "教学流程" };

export function ContentDeleteDialog({ classroomId, targets, onClose, onDone }: {
  classroomId: number; targets: { kind: DeletionKind; id: string }[]; onClose: () => void; onDone: () => void;
}) {
  const [preview, setPreview] = useState<Preview | null>(null);
  const [step, setStep] = useState<"preview" | "confirm">("preview");
  const [revision, setRevision] = useState(0), [error, setError] = useState("");
  const [confirmation, setConfirmation] = useState(""), [acknowledged, setAcknowledged] = useState(false);
  const [busy, setBusy] = useState(false), [stale, setStale] = useState(false);
  const action = useRef<AbortController | null>(null);
  const encoded = JSON.stringify(targets);
  useEffect(() => {
    const controller = new AbortController(), captured = token();
    setPreview(null); setError(""); setStep("preview"); setConfirmation(""); setAcknowledged(false); setStale(false);
    void request<Preview>("/teacher/content-deletions/preview", {
      method: "POST", signal: controller.signal, body: JSON.stringify({ classroom_id: classroomId, targets: JSON.parse(encoded) }),
    }).then(value => { if (!controller.signal.aborted && token() === captured) setPreview(value); })
      .catch(e => { if (!controller.signal.aborted) setError(errorMessage(e)); });
    return () => controller.abort();
  }, [classroomId, encoded, revision]);
  useEffect(() => () => action.current?.abort(), []);
  async function confirm() {
    if (!preview || busy || stale || !acknowledged || confirmation !== preview.confirmation) return;
    const controller = new AbortController(), captured = token();
    action.current = controller;
    setBusy(true); setError("");
    try {
      await request(`/teacher/content-deletions/${encodeURIComponent(preview.id)}/confirm`, {
        method: "POST", signal: controller.signal,
        body: JSON.stringify({ classroom_id: classroomId, confirmation, confirmed: true }),
      });
      if (!controller.signal.aborted && captured === token()) onDone();
    } catch (e) {
      if (!controller.signal.aborted) { setError(errorMessage(e)); setStale(true); }
    } finally { if (!controller.signal.aborted) setBusy(false); }
  }
  return <Dialog open onOpenChange={open => { if (!open && !busy) onClose(); }}>
    <DialogContent className="sm:max-w-2xl" showCloseButton={!busy}>
      <DialogHeader><DialogTitle>{step === "preview" ? "删除影响范围" : "最终确认删除"}</DialogTitle>
        <DialogDescription>{step === "preview" ? "请核对一起删除和解除关联的项目，再继续确认。" : "执行后内容会退出当前教学，学生历史记录仍保留。"}</DialogDescription>
      </DialogHeader>
      <ErrorNotice message={error} />
      {!preview && !error ? <Loading /> : null}
      {preview ? <>
        {step === "preview" ? <div className="max-h-[55dvh] space-y-4 overflow-auto pr-2">
          <ImpactList title="一起删除" rows={preview.remove} />
          <ImpactList title="解除关联" rows={preview.detach} />
          <ImpactList title="保留" rows={preview.preserve} />
          <p className="text-sm">{preview.history_policy}</p>
          {preview.affected_students > 0 ? <p className="text-sm">关联 {preview.affected_students} 名学生（含体验账号）。</p> : null}
          {preview.ending_sessions > 0 ? <p className="text-sm">将结束 {preview.ending_sessions} 个相关进行中互动，互动记录与进度保留。</p> : null}
          {preview.blockers.length > 0 ? <div className="rounded-md border border-destructive/30 p-3"><h3 className="font-semibold">需要先处理</h3>
            {preview.blockers.map((b, i) => <p className="mt-2 text-sm" key={i}>{b.message}{b.path ? <span className="mt-1 block break-all text-xs text-muted-foreground">位置：{b.path}</span> : null}</p>)}
            <p className="mt-2 text-xs text-muted-foreground">关闭弹窗，到教学流程或对应内容页修正后重新预览。</p>
          </div> : null}
        </div> : <div className="space-y-4">
          <p className="text-sm">将删除 {preview.remove.length} 项内容、解除 {preview.detach.length} 处关联。学生提交、试卷、成绩和进度保留。</p>
          <div><Label htmlFor="content-delete-confirmation">输入“{preview.confirmation}”确认</Label>
            <Input id="content-delete-confirmation" className="mt-2" autoComplete="off" value={confirmation} disabled={busy} onChange={e => setConfirmation(e.target.value)} /></div>
          <label className="flex items-start gap-2 text-sm"><Checkbox checked={acknowledged} disabled={busy} onCheckedChange={setAcknowledged} />我已核对删除范围，确认保留学生历史记录</label>
        </div>}
      </> : null}
      <DialogFooter>
        <Button variant="outline" disabled={busy} onClick={onClose}>取消</Button>
        {step === "confirm" ? <Button variant="outline" disabled={busy} onClick={() => setStep("preview")}>返回查看范围</Button> : null}
        {error || stale ? <Button variant="outline" disabled={busy} onClick={() => setRevision(v => v + 1)}>重新预览</Button> : null}
        {step === "preview" ? <Button disabled={!preview || !!preview.blockers.length || !!error || stale} onClick={() => setStep("confirm")}>继续确认</Button>
          : <Button variant="destructive" disabled={busy || stale || !acknowledged || confirmation !== preview?.confirmation} onClick={() => void confirm()}>{busy ? "正在删除…" : "确认级联删除"}</Button>}
      </DialogFooter>
    </DialogContent>
  </Dialog>;
}

function ImpactList({ title, rows }: { title: string; rows: Impact[] }) {
  return <section><h3 className="mb-2 text-sm font-semibold">{title}（{rows.length}）</h3>
    {rows.length ? <ul className="divide-y rounded-md border">{rows.map((row, i) => <li className="px-3 py-2 text-sm" key={`${row.kind}:${row.id ?? i}:${i}`}>
      <span className="mr-2 text-xs text-muted-foreground">{names[row.kind] ?? "历史"}</span>{row.title}{row.count ? ` · ${row.count} 条` : ""}
      {row.reason ? <p className="mt-1 text-xs text-muted-foreground">{row.reason}</p> : null}
    </li>)}</ul> : <p className="text-xs text-muted-foreground">无</p>}
  </section>;
}
