import { useEffect, useRef, useState } from "react";
import { Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { ErrorNotice } from "@/components/common";
import { api, errorMessage, token } from "@/lib/api";
import { ContentDeleteDialog, type DeletionKind } from "@/components/content-delete-dialog";

type Row = { id: number | string; title: string };

export function useTableDelete<T extends Row>(url: (row: T) => string, reload: () => void,
  cascade?: { classroomId: number; kind: (row: T) => DeletionKind | null }) {
  const [selected, setSelected] = useState<T[]>([]);
  const [targets, setTargets] = useState<T[]>([]);
  const [busy, setBusy] = useState(false);
  const [errors, setErrors] = useState<string[]>([]);
  const [completed, setCompleted] = useState(0);
  const mounted = useRef(false);
  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  function isSelected(row: T) {
    return selected.some(({ id }) => id === row.id);
  }
  function toggle(row: T, checked: boolean) {
    setSelected((current) => checked ? [...current.filter(({ id }) => id !== row.id), row] : current.filter(({ id }) => id !== row.id));
  }
  function togglePage(rows: T[], checked: boolean) {
    const ids = new Set(rows.map(({ id }) => id));
    setSelected((current) => [...current.filter(({ id }) => !ids.has(id)), ...(checked ? rows : [])]);
  }
  function ask(rows: T[]) {
    setErrors([]);
    setCompleted(0);
    setTargets(rows);
  }
  async function confirm() {
    if (busy || !targets.length) return;
    if (cascade && targets.every(row => cascade.kind(row))) return;
    const currentToken = token();
    const removed = new Set<Row["id"]>();
    const failed: T[] = [];
    const errors: string[] = [];
    setBusy(true);
    try {
      for (const row of targets) {
        if (!mounted.current || currentToken !== token()) return;
        try {
          await api.delete(url(row));
          removed.add(row.id);
        } catch (error) {
          failed.push(row);
          errors.push(`${row.title}：${errorMessage(error)}`);
        }
      }
      if (!mounted.current || currentToken !== token()) return;
      setSelected((current) => current.filter(({ id }) => !removed.has(id)));
      setCompleted((current) => current + removed.size);
      setTargets(failed);
      setErrors(errors);
      if (removed.size) reload();
    } finally {
      if (mounted.current) setBusy(false);
    }
  }

  function cascadeDone() {
    const ids = new Set(targets.map(row => row.id));
    setSelected(current => current.filter(row => !ids.has(row.id)));
    setCompleted(targets.length); setTargets([]); reload();
  }
  return { selected, isSelected, toggle, togglePage, ask, confirm, targets, busy, errors, completed, cascade, cascadeDone, clear: () => setSelected([]) };
}

export function DeleteSelection<T extends Row>({ actions }: { actions: ReturnType<typeof useTableDelete<T>> }) {
  const { selected, targets, busy, errors, completed, clear, ask, confirm } = actions;
  return (
    <>
      {selected.length > 0 && (
        <div className="selection-bar">
          <span>已选择 {selected.length} 条</span>
          <Button variant="ghost" size="sm" disabled={busy} onClick={clear}>取消选择</Button>
          <Button className="ml-auto" variant="destructive" size="sm" disabled={busy} onClick={() => ask(selected)}>
            <Trash2 />批量删除
          </Button>
        </div>
      )}
      {completed > 0 && targets.length === 0 && <p role="status" className="notice-success">已删除 {completed} 条。</p>}
      {actions.cascade && targets.length > 0 && targets.every(row => actions.cascade!.kind(row)) ?
        <ContentDeleteDialog classroomId={actions.cascade.classroomId}
          targets={targets.map(row => ({ kind: actions.cascade!.kind(row)!, id: String(row.id) }))}
          onClose={() => ask([])} onDone={actions.cascadeDone} /> : <Dialog open={targets.length > 0} onOpenChange={(open) => { if (!open && !busy) ask([]); }}>
        <DialogContent showCloseButton={false}>
          <DialogHeader>
            <DialogTitle>{targets.length === 1 ? `删除「${targets[0].title}」` : `删除选中的 ${targets.length} 条内容`}</DialogTitle>
            <DialogDescription>服务端会保留仍被引用或关联学习记录的内容。</DialogDescription>
          </DialogHeader>
          {completed > 0 && <p role="status">已删除 {completed} 条，以下内容保留。</p>}
          {errors.length > 0 && (
            <div className="max-h-52 overflow-auto">
              {errors.map((error, index) => <ErrorNotice key={index} message={error} />)}
            </div>
          )}
          <DialogFooter>
            <Button variant="outline" disabled={busy} onClick={() => ask([])}>{errors.length ? "关闭" : "取消"}</Button>
            <Button variant="destructive" disabled={busy} onClick={() => void confirm()}>
              {busy ? "正在删除…" : errors.length ? "重试未删除项" : "确认删除"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>}
    </>
  );
}

export function SelectTablePage<T extends Row>({ actions, rows }: { actions: ReturnType<typeof useTableDelete<T>>; rows: T[] }) {
  const count = rows.filter(actions.isSelected).length;
  return <Checkbox aria-label="选择本页内容" checked={count === rows.length && rows.length > 0} indeterminate={count > 0 && count < rows.length} disabled={actions.busy} onCheckedChange={(checked) => actions.togglePage(rows, checked)} />;
}

export function SelectTableRow<T extends Row>({ actions, row }: { actions: ReturnType<typeof useTableDelete<T>>; row: T }) {
  return <Checkbox aria-label={`选择${row.title}`} checked={actions.isSelected(row)} disabled={actions.busy} onCheckedChange={(checked) => actions.toggle(row, checked)} />;
}
