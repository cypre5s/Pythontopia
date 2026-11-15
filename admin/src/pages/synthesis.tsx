import { useEffect, useRef, useState } from 'react';
import { FilePenLine, Headphones, RefreshCw, RotateCcw } from 'lucide-react';
import { PageHeader, ErrorNotice, Loading, Empty, Pager } from '@/components/common';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { SelectField } from '@/components/select-field';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { NarrationReview } from '@/components/narration-review';
import { NarrationPlayer } from '@/components/narration-player';
import { api, errorMessage } from '@/lib/api';
import { useDraft } from '@/lib/drafts';

type Task = {
  material_id: number; title: string; material_kind: string; kind: string; status: string;
  done: number; total: number; message: string; started_at: string | null; finished_at: string | null;
  failed: { page_no: number; error?: string }[]; retryable: boolean; retry_note: string;
};
type Overview = { items: Task[]; total: number; counts: Record<string, number> };
const statuses: Record<string, string> = { idle: '未开始', running: '处理中', done: '已完成', failed: '失败', interrupted: '待核查' };
const kinds: Record<string, string> = { generate: '讲稿生成', calibrate: '讲稿校准', audio: '语音合成' };
const tones: Record<string, string> = { running: 'text-blue-700', done: 'text-emerald-700', failed: 'text-red-700', interrupted: 'text-amber-700' };

export function Synthesis({ classroomId }: { classroomId: number }) {
  const [query, setQuery] = useState(''), [status, setStatus] = useState('all');
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
        const params = new URLSearchParams({ query, status, page: String(page), size: String(size) });
        const result = await api.get<Overview>(`/teacher/classrooms/${classroomId}/synthesis?${params}`, controller.signal);
        if (!controller.signal.aborted) { setData(result); setError(''); }
      } catch (e) { if (!controller.signal.aborted) setError(errorMessage(e)); }
      finally { if (!controller.signal.aborted) timer = setTimeout(() => void load(), 5000); }
    }
    void load();
    return () => { controller.abort(); clearTimeout(timer); };
  }, [classroomId, query, status, page, size, refresh]);
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
    <PageHeader title="资料合成管理" description="查看本班 PDF 与演示文稿的最近一次任务，集中审校讲稿与试听音频。">
      <Button variant="outline" onClick={() => setRefresh(n => n + 1)}><RefreshCw />刷新</Button>
    </PageHeader>
    <ErrorNotice message={error} />{notice && <p role="status" className="hint mb-3">{notice}</p>}
    <div className="mb-4 flex flex-wrap items-center gap-3">
      <Input className="max-w-sm" aria-label="搜索资料名称" placeholder="搜索资料名称" value={query} onChange={e => { setQuery(e.target.value); setPage(1); }} />
      <SelectField aria-label="任务状态" value={status} onValueChange={v => { setStatus(v); setPage(1); }}>
        <option value="all">全部状态</option>{Object.entries(statuses).map(([key, label]) => <option value={key} key={key}>{label}</option>)}
      </SelectField>
      {data && <span className="hint">处理中 {data.counts.running} · 失败 {data.counts.failed} · 待核查 {data.counts.interrupted}</span>}
    </div>
    {!data ? !error && <Loading /> : <div className="surface overflow-hidden">
      <Table><TableHeader><TableRow><TableHead>资料名称</TableHead><TableHead>最近任务</TableHead><TableHead>状态 / 进度</TableHead><TableHead>详情</TableHead><TableHead>开始时间</TableHead><TableHead className="text-right">操作</TableHead></TableRow></TableHeader>
        <TableBody>{data.items.map(item => <TableRow key={item.material_id}>
          <TableCell><span className="font-medium">{item.title}</span><div className="hint">{item.material_kind === 'pdf' ? 'PDF' : '演示文稿'}</div></TableCell>
          <TableCell>{kinds[item.kind] || '—'}</TableCell>
          <TableCell><span className={tones[item.status]}>{statuses[item.status] || item.status}</span>{item.total > 0 && <div className="hint">已处理 {item.done} / {item.total} 页</div>}</TableCell>
          <TableCell className="max-w-sm whitespace-normal"><span>{item.message || '尚无任务记录'}</span>
            {item.failed.length > 0 && <details className="mt-1 text-xs"><summary>查看失败页（{item.failed.length}）</summary><ul className="mt-2 space-y-1">{item.failed.map(f => <li key={f.page_no}>第 {f.page_no} 页：{f.error || '生成失败'}</li>)}</ul></details>}
            {item.retry_note && <p className="hint">{item.retry_note}</p>}</TableCell>
          <TableCell className="text-xs text-muted-foreground">{item.started_at ? new Date(item.started_at).toLocaleString('zh-CN') : '—'}</TableCell>
          <TableCell><div className="flex justify-end gap-1">
            <Button variant="ghost" size="sm" onClick={() => setReview(item)}><FilePenLine />审校</Button>
            <Button variant="ghost" size="sm" onClick={() => setListen(item)}><Headphones />试听</Button>
            {item.retryable && <Button variant="outline" size="sm" disabled={busy} onClick={() => { setActionError(''); setRetry(item); }}><RotateCcw />重试</Button>}
          </div></TableCell>
        </TableRow>)}</TableBody>
      </Table>
      {!data.items.length && <Empty title="没有符合条件的资料" description="仅显示当前班级的 PDF 和演示文稿。" />}
      <Pager total={data.total} page={page} size={size} onPage={setPage} onSize={v => { setSize(v); setPage(1); }} />
    </div>}
    <p className="hint mt-3">每份资料保留最近一次任务状态，每 5 秒刷新。任务中断或旧记录缺少重试参数时，请先打开资料核查；重试需要手动确认。</p>
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
