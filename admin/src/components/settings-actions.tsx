import type { ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { LoaderCircle, Save, Undo2 } from 'lucide-react';
import { Button } from '@/components/ui/button';

export type SettingsActionPlacement = { actionsTarget?: HTMLElement | null; active?: boolean };

export function SettingsActions({ actionsTarget, active = true, dirty, busy, onSave, onCancel, children }: SettingsActionPlacement & {
  dirty: boolean; busy: boolean; onSave: () => void; onCancel: () => void; children?: ReactNode;
}) {
  if (!active) return null;
  const actions = <div className="flex flex-wrap items-center justify-end gap-2" aria-label="当前标签设置操作">
    {dirty && <span className="text-xs text-amber-700">未保存</span>}
    {children}
    <Button variant="ghost" disabled={busy || !dirty} onClick={onCancel}><Undo2 />取消编辑</Button>
    <Button disabled={busy || !dirty} onClick={onSave}>{busy ? <LoaderCircle className="animate-spin" /> : <Save />}{busy ? '正在保存…' : '保存设置'}</Button>
  </div>;
  return actionsTarget ? createPortal(actions, actionsTarget) : actions;
}
