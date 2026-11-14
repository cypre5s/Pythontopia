import { useCallback, useEffect, useRef, useState } from "react";
import { Activity, Pause, Play, RefreshCw, Search, ShieldCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from "@/components/ui/dialog";
import { Table, TableHeader, TableHead, TableBody, TableRow, TableCell } from "@/components/ui/table";
import { SelectField } from "@/components/select-field";
import { PageHeader, ErrorNotice, Empty, Loading, Pager } from "@/components/common";
import { api, errorMessage, request, token } from "@/lib/api";
import { formatTimestamp } from "@/lib/time";
import { judgeDateQuery, recentJudgeDates, type JudgeDates } from "@/lib/judge-dates";
import { useResource } from "@/lib/use-resource";

const statuses: Record<string, string> = {
  prepared: "等待评测", dispatching: "正在评测", accepted: "通过", wrong_answer: "解答错误",
  runtime_error: "运行错误", compile_error: "语法错误", time_limit: "运行超时", memory_limit: "内存超限",
  infrastructure_error: "服务异常", submission_unknown: "结果待核查", queued: "历史排队", running: "历史执行中",
};
interface Service {
  paused: boolean;
  health: { status?: string; detail?: string; latency_ms?: number };
  checked_at: string | null;
  worker_seen_at: string | null;
  worker_alive: boolean;
  counts: Record<string, number>;
  global_counts: Record<string, number> | null;
  events: { at: string; message: string }[];
}
interface Submission {
  id: string; student_name: string; student_id: string | null; question_title: string; activity_title: string;
  status: string; created_at: string; updated_at: string; passed_count: number | null; total_count: number | null;
}
interface Detail extends Submission {
  code: string;
  feedback: { detail?: string };
  diagnostic: { started_at: string; finished_at: string | null; detail: string } | null;
  cases: { index: number; public: boolean; input: string; expected_output: string;
    result: { status: string; stdout: string; stderr: string; time_ns: number | null; memory_bytes: number | null; output_truncated: boolean; source?: "public_receipt" } | null }[];
}
const timestamp = (value: string | null) => value ? formatTimestamp(value) : "暂无记录";

function useJudgePolling<T>(path: string) {
  const [data, setData] = useState<T | null>(null), [error, setError] = useState(""), [revision, setRevision] = useState(0);
  const reload = useCallback(() => setRevision(v => v + 1), []);
  useEffect(() => {
    const controller = new AbortController();
    const captured = token();
    let active = true, timer: ReturnType<typeof setTimeout>;
    setData(null);
    setError("");
    async function poll() {
      try {
        const value = await api.get<T>(path, controller.signal);
        if (active && captured === token()) { setData(value); setError(""); }
      } catch (e) { if (active) setError(errorMessage(e)); }
      finally { if (active) timer = setTimeout(() => void poll(), 10_000); }
    }
    void poll();
    return () => { active = false; controller.abort(); clearTimeout(timer); };
  }, [path, revision]);
  return { data, error, reload };
}

function Status({ value }: { value: string }) {
  return <span className={`judge-status ${value === "accepted" ? "judge-pass" : ["prepared", "dispatching"].includes(value) ? "" : "judge-fail"}`}>{statuses[value] ?? value}</span>;
}

export function Judge({ classroomId, canManage }: { classroomId: number; canManage: boolean }) {
  // A different class unmounts the entire page, including open private evidence.
  return <JudgePage key={classroomId} classroomId={classroomId} canManage={canManage} />;
}

function JudgePage({ classroomId, canManage }: { classroomId: number; canManage: boolean }) {
  const [draftDates, setDraftDates] = useState<JudgeDates>(() => recentJudgeDates(7));
  const [dateQuery, setDateQuery] = useState(() => judgeDateQuery(recentJudgeDates(7)));
  const [preset, setPreset] = useState<number | "all" | "custom">(7), [dateError, setDateError] = useState("");
  const [search, setSearch] = useState(""), [query, setQuery] = useState(""), [status, setStatus] = useState("");
  const [page, setPage] = useState(1), [size, setSize] = useState(25), [selected, setSelected] = useState<string | null>(null);
  const [control, setControl] = useState<boolean | null>(null), [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false), [error, setError] = useState(""), [notice, setNotice] = useState("");
  const action = useRef<AbortController | null>(null);
  useEffect(() => () => action.current?.abort(), []);
  const service = useJudgePolling<Service>(`/teacher/judge/service?classroom_id=${classroomId}${dateQuery}`);
  const submissions = useJudgePolling<{ items: Submission[]; total: number }>(`/teacher/judge/submissions?classroom_id=${classroomId}&q=${encodeURIComponent(query)}&status=${status}&page=${page}&size=${size}${dateQuery}`);
  const state = service.data;
  function applyDates(next: JudgeDates | null, choice: typeof preset) {
    try {
      const nextQuery = judgeDateQuery(next);
      if (next) setDraftDates(next);
      setDateQuery(nextQuery); setPreset(choice); setDateError(""); setPage(1); setSelected(null);
    } catch (e) { setDateError(errorMessage(e)); }
  }
  async function operate(kind: "probe" | "control") {
    const controller = new AbortController(), captured = token();
    action.current = controller;
    setBusy(true); setError(""); setNotice("");
    try {
      await request(`/teacher/judge/service${kind === "probe" ? "/probe" : ""}`, {
        method: kind === "probe" ? "POST" : "PUT", signal: controller.signal,
        ...(kind === "control" ? { body: JSON.stringify({ paused: control, reason }) } : {}),
      });
      if (controller.signal.aborted || captured !== token()) return;
      setControl(null); setReason(""); service.reload(); submissions.reload();
      setNotice(kind === "probe" ? "检测已完成，请查看最新服务状态。" : "派发设置已保存，正在执行的提交会继续完成。");
    } catch (e) { if (!controller.signal.aborted) setError(errorMessage(e)); }
    finally { if (!controller.signal.aborted) setBusy(false); }
  }
  return <>
    <PageHeader title="评测管理" description="查看服务状态与本班 Python 提交，定位学生遇到的评测问题。">
      <Button variant="outline" onClick={() => { service.reload(); submissions.reload(); }}><RefreshCw />刷新</Button>
    </PageHeader>
    <section className="surface mb-4 judge-date-filter" aria-label="评测日期筛选">
      <div className="flex flex-wrap items-center gap-2"><span className="mr-1 text-sm font-medium">统计日期</span>
        {[1, 7, 30].map(days => <Button key={days} size="sm" variant={preset === days ? "default" : "outline"} aria-pressed={preset === days} onClick={() => applyDates(recentJudgeDates(days), days)}>近{days === 1 ? "一天" : days === 7 ? "七天" : "三十天"}</Button>)}
        <Button size="sm" variant={preset === "all" ? "default" : "outline"} aria-pressed={preset === "all"} onClick={() => applyDates(null, "all")}>全部时间</Button>
      </div>
      <form className="flex flex-wrap items-center gap-2" onSubmit={e => { e.preventDefault(); applyDates(draftDates, "custom"); }}>
        <Input className="w-auto" aria-label="开始日期" id="judge-date-start" type="date" required value={preset === "all" ? "" : draftDates.start} onChange={e => { setPreset("custom"); setDraftDates(v => ({ ...v, start: e.target.value })); }} />
        <span className="text-muted-foreground" aria-hidden="true">至</span>
        <Input className="w-auto" aria-label="结束日期" id="judge-date-end" type="date" required value={preset === "all" ? "" : draftDates.end} onChange={e => { setPreset("custom"); setDraftDates(v => ({ ...v, end: e.target.value })); }} />
        <Button type="submit" size="sm" variant="outline">应用日期</Button>
      </form>
      <ErrorNotice message={dateError} />
    </section>
    <ErrorNotice message={service.error || submissions.error || (control === null ? error : "")} />
    {notice ? <p role="status" className="text-sm text-muted-foreground">{notice}</p> : null}
    <section className="surface judge-service" aria-label="评测服务状态">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div><h2 className="flex items-center gap-2 font-semibold"><ShieldCheck size={18} />评测服务</h2>
          {state ? <><p className="mt-3 font-medium">{state.health.status === "healthy" ? "连接正常" : "需要检查"} · {state.paused ? "派发已暂停" : "自动派发"}</p>
            {state.health.status !== "healthy" ? <p className="mt-1 text-sm judge-fail">{state.health.detail ?? "等待首次检测"}</p> : null}
            {!state.worker_alive ? <p className="mt-1 text-sm judge-fail">队列处理器未收到近期心跳</p> : null}
          </> : <Loading />}
        </div>
        {canManage ? <div className="flex flex-wrap gap-2">
          <Button variant="outline" disabled={busy} onClick={() => void operate("probe")}><Activity />立即检测</Button>
          <Button variant="outline" disabled={busy || !state} onClick={() => { setReason(""); setError(""); setControl(!state?.paused); }}>
            {state?.paused ? <Play /> : <Pause />}{state?.paused ? "恢复派发" : "暂停派发"}
          </Button>
        </div> : null}
      </div>
      {state ? <div className="judge-metrics">
        {[['等待评测', state.counts.prepared ?? 0], ['正在评测', state.counts.dispatching ?? 0], ['结果待核查', state.counts.submission_unknown ?? 0], ['服务异常', state.counts.infrastructure_error ?? 0]].map(([label, value]) => <div key={label}><span>{label}</span><strong>{value}</strong></div>)}
      </div> : null}
      <div className="flex flex-wrap items-start justify-between gap-3 text-xs text-muted-foreground"><span>本班 · 所选日期</span>
      {state ? <details><summary className="cursor-pointer">运行详情</summary>
        <p className="mt-2">检测：{timestamp(state.checked_at)}{state.health.latency_ms != null ? ` · ${state.health.latency_ms} ms` : ""} · 心跳{state.worker_alive ? "正常" : "异常"}</p>
        <p className="mt-2">每 10 秒刷新</p>
        {canManage && state.global_counts ? <p className="mt-2">全站实时队列（全部日期）：等待 {state.global_counts.prepared ?? 0} · 评测中 {state.global_counts.dispatching ?? 0} · 待核查 {state.global_counts.submission_unknown ?? 0}</p> : null}
      </details> : null}</div>
      {canManage && state ? <details className="mt-3 text-sm"><summary className="cursor-pointer">服务事件（{state.events.length}）</summary>
        <p className="mt-2 text-xs text-muted-foreground">按事件发生时间筛选，仅保留最近 50 条服务事件。</p>
        {!state.events.length ? <p className="mt-2 text-sm">所选范围内没有保留的服务事件。</p> : null}
        <ol className="judge-events">{[...state.events].reverse().map((e, i) => <li key={`${e.at}-${i}`}><time>{timestamp(e.at)}</time><span>{e.message}</span></li>)}</ol>
      </details> : null}
    </section>
    <section className="surface judge-submissions" aria-label="本班提交记录">
      <h2 className="font-semibold">提交记录</h2>
      <form className="my-4 flex flex-wrap gap-2" onSubmit={e => { e.preventDefault(); setPage(1); setQuery(search.trim()); }}>
        <Input className="max-w-sm" aria-label="搜索提交" placeholder="学生姓名、学号、题目或提交编号" value={search} maxLength={100} onChange={e => setSearch(e.target.value)} />
        <SelectField aria-label="评测状态" value={status || "all"} onValueChange={v => { setStatus(v === "all" ? "" : v); setPage(1); }}>
          <option value="all">全部状态</option>{Object.entries(statuses).map(([key, name]) => <option key={key} value={key}>{name}</option>)}
        </SelectField>
        <Button variant="outline" type="submit"><Search />搜索</Button>
      </form>
      {!submissions.data ? submissions.error ? null : <Loading /> : submissions.data.items.length ? <>
        <Table aria-label="Python 提交记录"><TableHeader><TableRow>{["学生", "题目 / 活动", "评测结果", "提交时间", "操作"].map(title => <TableHead key={title}>{title}</TableHead>)}</TableRow></TableHeader>
          <TableBody>{submissions.data.items.map(row => <TableRow key={row.id}>
            <TableCell><span className="font-medium">{row.student_name}</span><p className="text-xs text-muted-foreground">{row.student_id ?? "无学号"}</p></TableCell>
            <TableCell className="max-w-xs whitespace-normal"><span>{row.question_title}</span><p className="text-xs text-muted-foreground">{row.activity_title}</p></TableCell>
            <TableCell><Status value={row.status} />{row.total_count != null ? <p className="text-xs text-muted-foreground">{row.passed_count} / {row.total_count} 通过</p> : null}</TableCell>
            <TableCell>{timestamp(row.created_at)}</TableCell>
            <TableCell><Button variant="outline" size="sm" onClick={() => setSelected(row.id)} aria-label={`查看 ${row.student_name} 的提交 ${row.id}`}>查看提交</Button></TableCell>
          </TableRow>)}</TableBody></Table>
      </> : <Empty title="没有匹配的提交" description="这里只记录正式 Python 提交；学生浏览器中的公开样例运行不会上传。" />}
      {submissions.data ? <Pager page={page} size={size} total={submissions.data.total} onPage={setPage} onSize={v => { setSize(v); setPage(1); }} /> : null}
    </section>
    <Dialog open={control !== null} onOpenChange={open => { if (!open && !busy) setControl(null); }}>
      <DialogContent><DialogHeader><DialogTitle>{control ? "暂停全站评测派发" : "恢复全站评测派发"}</DialogTitle><DialogDescription>此操作影响所有班级。已在执行的提交继续完成；成绩和待核查结果保持不变。</DialogDescription></DialogHeader>
        <form onSubmit={e => { e.preventDefault(); void operate("control"); }}>
          <ErrorNotice message={error} /><Label htmlFor="judge-reason">操作原因</Label><Input id="judge-reason" className="mt-2" value={reason} maxLength={200} required disabled={busy} onChange={e => setReason(e.target.value)} />
          <DialogFooter className="mt-4"><Button type="button" variant="outline" disabled={busy} onClick={() => setControl(null)}>取消</Button><Button type="submit" disabled={busy || !reason.trim()}>{busy ? "保存中…" : "确认"}</Button></DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
    {selected ? <SubmissionDialog key={selected} classroomId={classroomId} id={selected} onClose={() => setSelected(null)} /> : null}
  </>;
}

function SubmissionDialog({ classroomId, id, onClose }: { classroomId: number; id: string; onClose: () => void }) {
  const detail = useResource<Detail>(`/teacher/judge/submissions/${encodeURIComponent(id)}?classroom_id=${classroomId}`);
  const data = detail.data;
  return <Dialog open onOpenChange={open => { if (!open) onClose(); }}><DialogContent className="judge-detail sm:max-w-4xl">
    <DialogHeader><DialogTitle>提交详情</DialogTitle><DialogDescription>仅本班教师与管理员可见。用例来自提交时快照，题目名称为当前名称；隐藏用例与诊断不可转发给学生。</DialogDescription></DialogHeader>
    <ErrorNotice message={detail.error} />{detail.error ? <Button variant="outline" onClick={detail.reload}>重试</Button> : null}
    {detail.loading ? <Loading /> : data ? <div className="judge-detail-body">
      <div className="flex flex-wrap justify-between gap-2"><div><h3 className="font-semibold">{data.question_title}</h3><p className="text-sm">{data.student_name} · {data.student_id ?? "无学号"} · {timestamp(data.created_at)}</p></div><Status value={data.status} /></div>
      <p className="mt-2 break-all text-xs text-muted-foreground">提交编号：{data.id}</p>
      <div className="judge-advice"><strong>排查提示</strong><p>{advice(data)}</p>{data.feedback.detail ? <p>{data.feedback.detail}</p> : null}</div>
      <h3 className="my-2 font-semibold">提交代码</h3><pre className="judge-code"><code>{data.code}</code></pre>
      <h3 className="my-3 font-semibold">评测过程</h3>
      {data.diagnostic ? <p className="text-sm text-muted-foreground">开始：{timestamp(data.diagnostic.started_at)} · 结束：{timestamp(data.diagnostic.finished_at)}{data.diagnostic.detail ? ` · ${data.diagnostic.detail}` : ""}</p> : <p className="text-sm text-muted-foreground">当时未记录执行诊断；历史提交不会自动重跑。</p>}
      {!data.cases.length ? <p className="my-4 text-sm">没有保存用例快照。</p> : data.cases.map(c => <details className="judge-case" key={c.index} open={c.result?.status !== "accepted"}>
        <summary><span>{c.public ? "公开" : "隐藏"}用例 {c.index + 1}</span> · {c.result ? <Status value={c.result.status} /> : "没有执行记录"}{c.result?.source === "public_receipt" ? " · 历史公开回执" : ""}</summary>
        <div className="mt-3"><Output title="输入" value={c.input} />
          <div className="judge-output-grid"><Output title="实际输出" value={c.result?.stdout} tone={c.result?.status === "wrong_answer" ? "actual" : ""} /><Output title="预期输出" value={c.expected_output} tone={c.result?.status === "wrong_answer" ? "expected" : ""} /></div>
          {c.result ? <><p className="text-xs text-muted-foreground">CPU 耗时：{c.result.time_ns == null ? "未记录" : `${(c.result.time_ns / 1e6).toFixed(2)} ms`} · 内存：{c.result.memory_bytes == null ? "未记录" : `${(c.result.memory_bytes / 1048576).toFixed(2)} MiB`}</p>
            {c.result.stderr ? <Output title="错误输出" value={c.result.stderr} tone="actual" /> : null}{c.result.output_truncated ? <p>输出过长，仅展示已保存部分。</p> : null}</> : null}
        </div>
      </details>)}
    </div> : null}
  </DialogContent></Dialog>;
}

function Output({ title, value, tone = "" }: { title: string; value?: string; tone?: string }) {
  return <div className="judge-output"><h4>{title}</h4><pre className={tone ? `judge-${tone}` : ""}>{value === undefined ? "未记录" : value === "" ? "（空输出）" : value}</pre></div>;
}

function advice(data: Detail): string {
  if (data.status === "accepted") return "本次正式提交已通过全部用例。";
  if (data.status === "submission_unknown") return "请求可能已经执行，但完整结果未能确认。请保留提交编号，核对服务事件及执行记录；不要直接重跑或凭推测改成绩。";
  if (["infrastructure_error", "prepared", "dispatching"].includes(data.status)) return "当前不是代码正确性的最终结论。请先查看服务连接、队列状态和时间记录。";
  if (data.status === "time_limit") return "可能存在无限循环或算法在较大输入下耗时过长。公开样例较小，不能覆盖正式用例的全部规模。";
  if (data.status === "memory_limit") return "检查是否创建了过大的列表、字符串，或数据随循环持续累积。正式运行有内存限制。";
  if (["runtime_error", "compile_error"].includes(data.status)) return "结合错误输出检查语法、缩进、输入解析和越界；也需考虑 Python 运行环境与第三方包差异。";
  const publicCases = data.cases.filter(c => c.public);
  const publicPassed = publicCases.length > 0 && publicCases.every(c => c.result?.status === "accepted");
  return `${publicPassed ? "本次提交的公开用例通过，隐藏用例未全部通过。" : "请对照本次提交代码及实际输出，浏览器试运行与正式提交可能不是同一份代码。"}公开样例不覆盖全部输入；请检查边界条件、空格换行、中文/英文标点与额外提示文字。这些是排查方向，不是自动归因。`;
}
