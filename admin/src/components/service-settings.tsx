import { useEffect, useState } from 'react';
import { Save, Undo2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Checkbox } from '@/components/ui/checkbox';
import { SelectField } from '@/components/select-field';
import { ErrorNotice, Loading } from '@/components/common';
import { api, errorMessage } from '@/lib/api';
import { useDraft } from '@/lib/drafts';

type Service = 'authoring' | 'speech';
type Settings = Record<string, string | number | boolean> & { revision: number; key_configured: boolean };
type Field = { key: string; label: string; min?: number; max?: number; step?: number; options?: string[] };
const shared: Field[] = [{ key: 'base_url', label: 'API 地址' }, { key: 'model', label: '模型名称' }];
const fields: Record<Service, Field[]> = {
  authoring: [...shared, { key: 'vision_model', label: '页面图像模型' },
    { key: 'effort', label: '思考模式', options: ['none', 'minimal', 'low', 'medium', 'high'] },
    { key: 'max_tokens', label: '每次回复 tokens', min: 128, max: 65536 },
    { key: 'daily_pages', label: '每门课程每日生成页数', min: 1, max: 10000 },
    { key: 'timeout', label: '请求超时（秒）', min: 10, max: 600 }],
  speech: [{ key: 'protocol', label: '语音接口协议', options: ['openai', 'deepseek'] }, ...shared,
    { key: 'voice', label: '音色名称 / ID' },
    { key: 'format', label: '返回音频格式', options: ['mp3', 'wav', 'pcm', 'opus', 'flac', 'aac'] },
    { key: 'speed', label: '合成语速', min: 0.25, max: 4, step: 0.05 },
    { key: 'timeout', label: '请求超时（秒）', min: 10, max: 600 }],
};
const names = { authoring: '讲稿生成', speech: '语音合成' };
const labels: Record<string, string> = { none: '关闭', minimal: '最少', low: '低', medium: '中', high: '高', openai: 'OpenAI 兼容语音接口', deepseek: 'DeepSeek 工具语音接口' };

export function ServiceSettings({ service }: { service: Service }) {
  const [saved, setSaved] = useState<Settings>(), [draft, setDraft] = useState<Settings>();
  const [key, setKey] = useState(''), [clearKey, setClearKey] = useState(false);
  const [error, setError] = useState(''), [notice, setNotice] = useState(''), [busy, setBusy] = useState(false);
  const dirty = !!saved && (JSON.stringify(saved) !== JSON.stringify(draft) || !!key || clearKey);
  useDraft(dirty);
  const path = `/teacher/services/${service}`;
  useEffect(() => {
    const controller = new AbortController();
    void api.get<Settings>(path, controller.signal).then(value => {
      if (!controller.signal.aborted) { setSaved(value); setDraft(value); }
    }).catch(e => { if (!controller.signal.aborted) setError(errorMessage(e)); });
    return () => controller.abort();
  }, [path]);
  async function save() {
    if (!draft || !saved || busy) return;
    setBusy(true); setError(''); setNotice('');
    try {
      const value = await api.put<Settings>(path, {
        ...Object.fromEntries(fields[service].map(({ key: name }) => [name, draft[name]])),
        expected_revision: saved.revision, api_key: key || null, clear_key: clearKey,
      });
      setSaved(value); setDraft(value); setKey(''); setClearKey(false); setNotice(`${names[service]}设置已保存`);
    } catch (e) { setError(errorMessage(e)); } finally { setBusy(false); }
  }
  return <section className="surface space-y-4 p-6">
    <div className="flex flex-wrap items-center justify-between gap-2"><h2 className="font-semibold">{names[service]}服务</h2>
      <div className="flex gap-2">
        {dirty && <Button variant="ghost" disabled={busy} onClick={() => { if (window.confirm('放弃未保存的服务设置？')) { setDraft(saved); setKey(''); setClearKey(false); } }}><Undo2 />取消编辑</Button>}
        <Button disabled={busy || !dirty} onClick={() => void save()}><Save />{busy ? '正在保存…' : '保存服务设置'}</Button>
      </div>
    </div>
    <p className="hint">全站共用，由管理员维护。{service === 'authoring' ? '用于 PDF、演示文稿的讲稿生成与校准，不使用学生答疑预算。' : '将教师已接受的讲稿合成为音频；留空 API 地址可停用合成。'}保存不会调用外部服务。</p>
    <ErrorNotice message={error} />{notice && !dirty && <p role="status" className="hint">{notice}</p>}
    {!draft ? (!error && <Loading />) : <fieldset disabled={busy} className="space-y-4">
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">{fields[service].map(field => <div className="field" key={field.key}>
        <Label htmlFor={`${service}-${field.key}`}>{field.label}</Label>
        {field.options ? <SelectField id={`${service}-${field.key}`} value={String(draft[field.key])} onValueChange={value => setDraft({ ...draft, [field.key]: value })}>
          {field.options.map(value => <option key={value} value={value}>{labels[value] ?? value}</option>)}
        </SelectField> : <Input id={`${service}-${field.key}`} type={field.min === undefined ? 'text' : 'number'} min={field.min} max={field.max} step={field.step}
          value={String(draft[field.key])} onChange={e => setDraft({ ...draft, [field.key]: field.min === undefined ? e.target.value : Number(e.target.value) })} />}
      </div>)}</div>
      <div className="field max-w-lg"><Label htmlFor={`${service}-key`}>API Key {saved?.key_configured ? '（已配置）' : '（未配置）'}</Label>
        <Input id={`${service}-key`} type="password" autoComplete="new-password" value={key} placeholder={saved?.key_configured ? '••••••••' : '填写服务密钥'} disabled={clearKey} onChange={e => setKey(e.target.value)} />
        <Label className="mt-2 gap-2"><Checkbox checked={clearKey} onCheckedChange={value => { setClearKey(!!value); setKey(''); }} />清除已保存密钥</Label>
      </div>
      {service === 'speech' && <p className="hint">接口协议、模型和音色请按实际服务填写。DeepSeek 工具语音接口仅支持 wav/pcm，合成语速为 1；学生播放时仍可调速。</p>}
      <p className="hint">当前来源：{saved?.source === 'database' ? '后台已保存设置' : '统一环境配置'}。密钥留空会保留现有值。</p>
    </fieldset>}
  </section>;
}
