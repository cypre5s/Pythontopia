import { useEffect, useRef, useState } from 'react';
import { FilePenLine, Headphones, RefreshCw, RotateCcw } from 'lucide-react';
import { PageHeader, ErrorNotice, Loading, Empty, Pager } from '@/components/common';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { SelectField } from '@/components/select-field';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { NarrationReview } from '@/features/materials/narration/review';
import { NarrationPlayer } from '@/features/materials/narration/player';
import { api, errorMessage } from '@/lib/api';
import { useDraft } from '@/lib/drafts';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Activity, History } from 'lucide-react';

type Task = {
  id: number; material_id: number; title: string; material_kind: string; kind: string; status: string;
  requested_by: string; scope: string; material_available: boolean;
  done: number; total: number; message: string; started_at: string | null; finished_at: string | null;
  failed: { page_no: number; error?: string }[]; retryable: boolean; retry_note: string;
};
type Overview = { items: Task[]; total: number; counts: Record<string, number> };
const statuses: Record<string, string> = { running: '处理中', done: '已完成', failed: '失败', interrupted: '待核查' };
const kinds: Record<string, string> = { generate: '讲稿生成', calibrate: '讲稿校准', audio: '语音合成' };
const tones: Record<string, string> = { running: 'text-blue-700', done: 'text-emerald-700', failed: 'text-red-700', interrupted: 'text-amber-700' };

export function Synthesis({ classroomId }: { classroomId: number }) {
  const [query, setQuery] = useState(''), [status, setStatus] = useState('all');
  const [view, setView] = useState('running');
  const [page, setPage] = useState(1), [size, setSize] = useState(25), [refresh, setRefresh] = useState(0);
  const [data, setData] = useState<Overview>(), [error, setError] = useState('');
  const [actionError, setActionError] = useState('');
  const [review, setReview] = useState<Task>(), [listen, setListen] = useState<Task>(), [retry, setRetry] = useState<Task>();
  const [busy, setBusy] = useState(false), [notice, setNotice] = useState('');
  const alive = useRef(true);
  useDraft(busy);
  useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
  useEffect(() => {
    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout>;
    async function load() {
      try {
        const params = new URLSearchParams({ query, status, view, page: String(page), size: String(size) });
        const result = await api.get<Overview>(`/teacher/classrooms/${classroomId}/synthesis?${params}`, controller.signal);
        if (!controller.signal.aborted) { setData(result); setError(''); }
      } catch (e) { if (!controller.signal.aborted) setError(errorMessage(e)); }
      finally { if (!controller.signal.aborted) timer = setTimeout(() => void load(), 5000); }
    }
    void load();
    return () => { controller.abort(); clearTimeout(timer); };
  }, [classroomId, query, status, view, page, size, refresh]);
  async function retryTask() {
    if (!retry || busy) return;
    setBusy(true); setNotice(''); setActionError('');
    try {
      const result = await api.post<Task>(`/teacher/classrooms/${classroomId}/synthesis/${retry.material_id}/retry`, { expected_started_at: retry.started_at });
      if (alive.current) { setNotice(result.status === 'done' ? '重试已完成' : '重试仍有失败，请查看任务详情'); setRetry(undefined); }
    } catch (e) { if (alive.current) setActionError(errorMessage(e)); }
    finally { if (alive.current) { setBusy(false); setRefresh(n => n + 1); } }
  }
  return <>
    <PageHeader title="资料合成管理" description="跟踪本班讲稿生成、校准与配音任务，查看每次执行的结果和失败原因。">
      <Button variant="outline" onClick={() => setRefresh(n => n + 1)}><RefreshCw />刷新</Button>
    </PageHeader>
    <Tabs value={view} onValueChange={v => { setView(v as string); setStatus('all'); setPage(1); }} className="mb-4">
      <TabsList><TabsTrigger value="running"><Activity />正在执行{data ? `（${data.counts.running}）` : ''}</TabsTrigger><TabsTrigger value="history"><History />历史记录</TabsTrigger></TabsList>
    </Tabs>
    <ErrorNotice message={error} />{notice && <p role="status" className="hint mb-3">{notice}</p>}
    <div className="mb-4 flex flex-wrap items-center gap-3">
      <Input className="max-w-sm" aria-label="搜索资料名称" placeholder="搜索资料名称" value={query} onChange={e => { setQuery(e.target.value); setPage(1); }} />
      {view === 'history' && <SelectField aria-label="任务状态" value={status} onValueChange={v => { setStatus(v); setPage(1); }}>
        <option value="all">全部状态</option>{Object.entries(statuses).filter(([key]) => key !== 'running').map(([key, label]) => <option value={key} key={key}>{label}</option>)}
      </SelectField>}
      {data && <span className="hint">处理中 {data.counts.running} · 失败 {data.counts.failed} · 待核查 {data.counts.interrupted}</span>}
    </div>
    {!data ? !error && <Loading /> : <div className="surface overflow-hidden">
      <Table><TableHeader><TableRow><TableHead>任务 / 资料</TableHead><TableHead>类型 / 范围</TableHead><TableHead>状态 / 进度</TableHead><TableHead>执行详情</TableHead><TableHead>执行时间</TableHead><TableHead className="text-right">操作</TableHead></TableRow></TableHeader>
        <TableBody>{data.items.map(item => <TableRow key={item.id}>
          <TableCell><span className="font-medium">{item.title}</span><div className="hint">{item.material_kind === 'pdf' ? 'PDF' : '演示文稿'}</div></TableCell>
          <TableCell>{kinds[item.kind] || '—'}<div className="hint">{item.scope}</div><div className="hint">任务 #{item.id} · {item.requested_by}</div></TableCell>
          <TableCell><span className={tones[item.status]}>{statuses[item.status] || item.status}</span>{item.total > 0 && <div className="hint">已处理 {item.done} / {item.total} 页</div>}</TableCell>
          <TableCell className="max-w-sm whitespace-normal"><span>{item.message || '尚无任务记录'}</span>
            {item.failed.length > 0 && <details className="mt-1 text-xs"><summary>查看失败页（{item.failed.length}）</summary><ul className="mt-2 space-y-1">{item.failed.map(f => <li key={f.page_no}>第 {f.page_no} 页：{f.error || '生成失败'}</li>)}</ul></details>}
            {item.retry_note && <p className="hint">{item.retry_note}</p>}</TableCell>
          <TableCell className="text-xs text-muted-foreground"><div>开始：{item.started_at ? new Date(item.started_at).toLocaleString('zh-CN') : '—'}</div><div>结束：{item.finished_at ? new Date(item.finished_at).toLocaleString('zh-CN') : '—'}</div></TableCell>
          <TableCell><div className="flex justify-end gap-1">
            <Button variant="ghost" size="sm" disabled={item.material_available === false} onClick={() => setReview(item)}><FilePenLine />查看资料</Button>
            <Button variant="ghost" size="sm" disabled={item.material_available === false} onClick={() => setListen(item)}><Headphones />试听</Button>
            {item.retryable && <Button variant="outline" size="sm" disabled={busy} onClick={() => { setActionError(''); setRetry(item); }}><RotateCcw />重试</Button>}
          </div></TableCell>
        </TableRow>)}</TableBody>
      </Table>
      {!data.items.length && <Empty title={view === 'running' ? '当前没有正在执行的任务' : '暂无符合条件的执行记录'} description={view === 'running' ? '可在历史记录中查看之前的执行结果。' : '每次执行独立保留记录，重试不会覆盖上一次结果。'} />}
      <Pager total={data.total} page={page} size={size} onPage={setPage} onSize={v => { setSize(v); setPage(1); }} />
    </div>}
    <p className="hint mt-3">每 5 秒更新执行状态。升级前仅保留原有最后一次记录；之后每次执行和重试都单独留存。中断任务请先核查结果。</p>
    {review && <NarrationReview classroomId={classroomId} materialId={review.material_id} title={review.title} onClose={() => { setReview(undefined); setRefresh(n => n + 1); }} />}
    {listen && <NarrationPlayer classroomId={classroomId} materialId={listen.material_id} title={listen.title} onClose={() => setListen(undefined)} />}
    <Dialog open={!!retry} onOpenChange={open => { if (!open && !busy) setRetry(undefined); }}>
      <DialogContent showCloseButton={!busy}><DialogHeader><DialogTitle>重试{retry ? kinds[retry.kind] : '任务'}</DialogTitle>
        <DialogDescription>{retry?.title}。按原任务参数重试；批次已有明确失败页时只处理失败页。会调用已配置的服务并可能产生费用，资料已变更时会阻止重试。</DialogDescription></DialogHeader>
        <ErrorNotice message={actionError} /><DialogFooter><Button variant="outline" disabled={busy} onClick={() => setRetry(undefined)}>取消</Button>
          <Button disabled={busy} onClick={() => void retryTask()}><RotateCcw className={busy ? 'animate-spin' : ''} />{busy ? '正在重试…' : '确认重试'}</Button></DialogFooter>
      </DialogContent>
    </Dialog>
  </>;
}
