import { useEffect, useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Checkbox } from '@/components/ui/checkbox';
import { SelectField } from '@/components/select-field';
import { ErrorNotice } from '@/components/common';
import { api, errorMessage } from '@/lib/api';
import { useDraft } from '@/lib/drafts';

type ModelSettings = {
  revision: number; base_url: string; model: string; reasoning_effort: string;
  context_window: number; compression_threshold: number; output_tokens: number;
  model_calls: number; tool_calls: number; timeout_seconds: number; request_tokens: number;
  key_configured: boolean; key_mask: string; source: string; token_counting: string;
};
const numbers = [
  ['context_window', '最大上下文 tokens', 8192, 2000000],
  ['compression_threshold', '触发压缩 tokens', 4096, 1900000],
  ['output_tokens', '每次回复 tokens', 128, 8192],
  ['model_calls', '每次提问最多模型调用', 1, 20],
  ['tool_calls', '每次提问最多工具调用', 0, 40],
  ['timeout_seconds', '每次提问超时（秒）', 10, 300],
  ['request_tokens', '每次提问累计 token 预算', 8192, 2000000],
] as const;

export function AssistantModelSettings({ onSaved }: { onSaved?: (limits: Record<string, number>) => void }) {
  const [saved, setSaved] = useState<ModelSettings>(), [draft, setDraft] = useState<ModelSettings>();
  const [key, setKey] = useState(''), [clearKey, setClearKey] = useState(false);
  const [error, setError] = useState(''), [notice, setNotice] = useState(''), [busy, setBusy] = useState(false);
  const dirty = !!saved && (JSON.stringify(saved) !== JSON.stringify(draft) || !!key || clearKey);
  useDraft(dirty);
  useEffect(() => {
    const controller = new AbortController();
    void api.get<ModelSettings>('/teacher/assistant/model', controller.signal).then(value => {
      if (!controller.signal.aborted) { setSaved(value); setDraft(value); }
    }).catch(e => { if (!controller.signal.aborted) setError(errorMessage(e)); });
    return () => controller.abort();
  }, []);
  async function save() {
    if (!draft || !saved || busy) return;
    setBusy(true); setError(''); setNotice('');
    try {
      const value = await api.put<ModelSettings>('/teacher/assistant/model', {
        expected_revision: saved.revision, base_url: draft.base_url, model: draft.model, reasoning_effort: draft.reasoning_effort,
        ...Object.fromEntries(numbers.map(([name]) => [name, draft[name]])), api_key: key || null, clear_key: clearKey,
      });
      setSaved(value); setDraft(value); setKey(''); setClearKey(false); setNotice('全站智能体设置已保存');
      onSaved?.(Object.fromEntries(numbers.map(([name]) => [name, value[name]])));
    } catch (e) { setError(errorMessage(e)); } finally { setBusy(false); }
  }
  async function test() {
    setBusy(true); setError(''); setNotice('');
    try {
      const result = await api.post<{ elapsed_ms: number; tokens: number }>('/teacher/assistant/model/test', {});
      setNotice(`连接正常 · ${result.elapsed_ms} ms · ${result.tokens} tokens`);
    } catch (e) { setError(errorMessage(e)); } finally { setBusy(false); }
  }
  return <section className="surface mt-4 space-y-4 p-6">
    <div className="flex flex-wrap items-center justify-between gap-2"><h2 className="font-semibold">全站模型与上下文</h2>
      <div className="flex gap-2"><Button variant="outline" disabled={busy || dirty || !saved?.key_configured} onClick={() => void test()}>测试已保存连接</Button>
        <Button disabled={busy || !dirty} onClick={() => void save()}>保存全站设置</Button>
        {dirty && <Button variant="ghost" disabled={busy} onClick={() => { if (window.confirm('放弃全站设置的未保存修改？')) { setDraft(saved); setKey(''); setClearKey(false); } }}>取消编辑</Button>}</div>
    </div>
    <p className="hint">学习与练习共用此连接。保存不会调用模型；测试连接会发送一次短请求。</p>
    <ErrorNotice message={error} />{notice && <p role="status" className="hint">{notice}</p>}
    {draft && <fieldset disabled={busy} className="space-y-4">
      <div className="grid gap-4 md:grid-cols-2">
        <div className="field"><Label htmlFor="model-url">API 地址</Label><Input id="model-url" value={draft.base_url} onChange={e => setDraft({ ...draft, base_url: e.target.value })} /></div>
        <div className="field"><Label htmlFor="model-name">模型名称</Label><Input id="model-name" value={draft.model} onChange={e => setDraft({ ...draft, model: e.target.value })} /></div>
        <div className="field"><Label htmlFor="model-key">API Key {saved?.key_configured ? '（已配置）' : '（未配置）'}</Label>
          <Input id="model-key" type="password" autoComplete="new-password" value={key} placeholder={saved?.key_mask || '填写密钥'} disabled={clearKey} onChange={e => setKey(e.target.value)} />
          <Label className="mt-2 gap-2"><Checkbox checked={clearKey} onCheckedChange={v => { setClearKey(!!v); setKey(''); }} />清除已保存密钥</Label>
        </div>
        <div className="field"><Label htmlFor="model-reasoning">思考模式</Label><SelectField id="model-reasoning" value={draft.reasoning_effort || 'default'} onValueChange={value => setDraft({ ...draft, reasoning_effort: value === 'default' ? '' : value })}>
          <option value="default">供应商默认</option><option value="none">关闭</option><option value="minimal">最少</option><option value="low">低</option><option value="medium">中</option><option value="high">高</option>
        </SelectField></div>
      </div>
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">{numbers.map(([name, label, min, max]) => <div className="field" key={name}>
        <Label htmlFor={`model-${name}`}>{label}</Label><Input id={`model-${name}`} type="number" min={min} max={max} value={draft[name]} onChange={e => setDraft({ ...draft, [name]: Number(e.target.value) })} />
      </div>)}</div>
      <p className="hint">上下文上限需与供应商模型匹配。达到阈值时自动整理历史，最多增加一次摘要调用；原聊天记录保留。{draft.token_counting}。</p>
    </fieldset>}
  </section>;
}
