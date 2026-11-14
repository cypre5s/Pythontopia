import { useEffect, useState } from "react";
import { Save, Undo2 } from "lucide-react";
import { ErrorNotice, Loading, PageHeader } from "@/components/common";
import { SelectField } from "@/components/select-field";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { api, errorMessage } from "@/lib/api";
import { useDraft } from "@/lib/drafts";
import { AssistantModelSettings } from '@/components/assistant-model-settings';

type AssistantSettings = { enabled?: boolean; default_npc_id?: string | null; limits?: Record<string, number> };
type Settings = { revision: number; assistant: AssistantSettings; roles: { id: string; name: string }[]; system_limits?: Record<string, number> };
const budgets = [
  ["model_calls", "每次最多模型调用", 6, 1], ["tool_calls", "每次最多工具调用", 12, 0],
  ["output_tokens", "回复令牌上限", 2048, 1], ["request_tokens", "请求令牌预算", 262144, 1],
  ["timeout_seconds", "超时秒数", 120, 1],
] as const;

export function CourseSettings({ classroomId, isAdmin = false }: { classroomId: number; isAdmin?: boolean }) {
  const [saved, setSaved] = useState<Settings | null>(null);
  const [draft, setDraft] = useState<AssistantSettings>({});
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const dirty = Boolean(saved && JSON.stringify(saved.assistant) !== JSON.stringify(draft));
  useDraft(dirty);
  const path = `/teacher/classrooms/${classroomId}/course/settings`;
  useEffect(() => {
    const controller = new AbortController();
    void api.get<Settings>(path, controller.signal).then(value => {
      if (!controller.signal.aborted) { setSaved(value); setDraft(value.assistant); }
    }).catch(failure => { if (!controller.signal.aborted) setError(errorMessage(failure)); });
    return () => controller.abort();
  }, [path]);
  async function save() {
    if (!saved || busy) return;
    setBusy(true); setError(""); setMessage("");
    try {
      const result = await api.put<{ revision: number }>(path, { expected_revision: saved.revision, assistant: draft });
      setSaved({ ...saved, revision: result.revision, assistant: draft });
      setMessage("课程设置已保存");
    } catch (failure) { setError(errorMessage(failure)); }
    finally { setBusy(false); }
  }
  return <>
    <PageHeader title="智能体设置" description="学习与练习分别提供辅导，共用本班角色设置和调用预算。">
      {dirty && <Button variant="ghost" disabled={busy} onClick={() => {
        if (saved && window.confirm("放弃课程设置的未保存修改？")) { setDraft(saved.assistant); setError(""); }
      }}><Undo2 />取消编辑</Button>}
      <Button disabled={!dirty || busy} onClick={() => void save()}><Save />{busy ? "正在保存…" : "保存设置"}</Button>
    </PageHeader>
    <ErrorNotice message={error} />
    {message && !dirty && <p role="status" className="hint">{message}</p>}
    {!saved ? (!error && <Loading />) : <fieldset disabled={busy} aria-busy={busy} className="surface space-y-6 p-6">
      <h2 className="font-semibold">本班设置</h2>
      <Label className="gap-2"><Checkbox checked={draft.enabled ?? true} onCheckedChange={enabled => setDraft({ ...draft, enabled })} />开放本班课程 AI 答疑</Label>
      <div className="field max-w-lg">
        <Label htmlFor="default-npc">课程默认答疑角色</Label>
        <SelectField id="default-npc" placeholder="未配置" value={draft.default_npc_id ?? ""} onValueChange={value => setDraft({ ...draft, default_npc_id: value || null })}>
          <option value="">未配置</option>
          {saved.roles.map(role => <option key={role.id} value={role.id}>{role.name}</option>)}
        </SelectField>
        <p className="hint">图书馆阅读答疑和选择“使用课程默认角色”的活动使用此角色。角色资料在教学流程的助教教学角色中维护。</p>
        {!saved.roles.length && <p className="hint">请先在助教教学角色中新增角色。</p>}
      </div>
      <section className="space-y-4 border-t pt-5">
        <h3 className="font-medium">课程助教预算</h3>
        <p className="hint">这些限制适用于本班所有 AI 答疑，仍受系统额度上限约束。关闭课程答疑后，所有活动和图书馆均不可提问；测验始终不开放助教。</p>
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {budgets.map(([key, label, max, min]) => <div className="field" key={key}>
            <Label htmlFor={`budget-${key}`}>{label}</Label>
            <Input id={`budget-${key}`} type="number" min={min} max={saved.system_limits?.[key] ?? max} value={draft.limits?.[key] ?? saved.system_limits?.[key] ?? max}
              onChange={event => setDraft({ ...draft, limits: { ...draft.limits, [key]: Number(event.target.value) } })} />
          </div>)}
        </div>
      </section>
    </fieldset>}
    {isAdmin && <AssistantModelSettings onSaved={limits => setSaved(current => current ? { ...current, system_limits: limits } : current)} />}
  </>;
}
