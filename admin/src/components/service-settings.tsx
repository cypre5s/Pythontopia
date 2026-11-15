import { useEffect, useState } from 'react';
import { WandSparkles } from 'lucide-react';
import { SettingsActions, type SettingsActionPlacement } from './settings-actions';
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
type Field = { key: string; label: string; min?: number; max?: number; step?: number; options?: string[]; placeholder?: string };
const shared: Field[] = [{ key: 'base_url', label: 'API 地址', placeholder: '例如：https://api.example.com' }, { key: 'model', label: '模型名称' }];
const fields: Record<Service, Field[]> = {
  authoring: [...shared, { key: 'vision_model', label: '页面图像模型' },
    { key: 'effort', label: '思考模式', options: ['none', 'minimal', 'low', 'medium', 'high'] },
    { key: 'max_tokens', label: '每次回复上限（tokens）', min: 128, max: 65536 },
    { key: 'daily_pages', label: '每门课程每日生成页数', min: 1, max: 10000 },
    { key: 'timeout', label: '请求超时（秒）', min: 10, max: 600 }],
  speech: [{ key: 'protocol', label: '语音接口协议', options: ['openai', 'deepseek'] }, ...shared,
    { key: 'voice', label: '音色名称 / ID' },
    { key: 'format', label: '返回音频格式', options: ['mp3', 'wav', 'pcm', 'opus', 'flac', 'aac'] },
    { key: 'speed', label: '合成语速', min: 0.25, max: 4, step: 0.05 },
    { key: 'timeout', label: '请求超时（秒）', min: 10, max: 600 }],
};
const names = { authoring: '讲稿生成', speech: '语音合成' };
const labels: Record<string, string> = { none: '关闭', minimal: '最少', low: '低', medium: '中', high: '高', stella: 'Stella（女声）', tide: 'Tide（男声）', openai: 'OpenAI 兼容语音接口', deepseek: 'DeepSeek 工具语音接口' };

export function ServiceSettings({ service, actionsTarget, active }: SettingsActionPlacement & { service: Service }) {
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
      setSaved(value); setDraft(value); setKey(''); setClearKey(false); setNotice('设置已保存');
    } catch (e) { setError(errorMessage(e)); } finally { setBusy(false); }
  }
  const deepseek = service === 'speech' && draft?.protocol === 'deepseek';
  const visibleFields = fields[service].filter(field => !(deepseek && ['model', 'speed'].includes(field.key))).map(field => {
    if (deepseek && field.key === 'voice') return { ...field, options: ['stella', 'tide'] };
    if (deepseek && field.key === 'format') return { ...field, options: ['wav', 'pcm'] };
    return field;
  });
  function applyProtocol(protocol: string, replace = false) {
    if (!draft) return;
    const example = protocol === 'deepseek'
      ? { base_url: 'https://api.deepseek.com', model: '', voice: 'stella', format: 'wav', speed: 1 }
      : { base_url: 'https://api.openai.com', model: 'tts-1', voice: 'alloy', format: 'mp3', speed: 1 };
    // Custom endpoints remain intact unless the explicit example action is used.
    const known = ['', 'https://api.deepseek.com', 'https://api.openai.com'];
    const root = String(draft.base_url).replace(/\/+$/, '').replace(/\/v1(?:\/(?:tools\/tts|audio\/speech))?$/, '');
    setDraft({ ...draft, ...example, base_url: replace || known.includes(root) ? example.base_url : draft.base_url, protocol });
    setNotice('示例已填入，请核对后保存。');
  }
  return <section className="surface space-y-4 p-6">
    <SettingsActions actionsTarget={actionsTarget} active={active} dirty={dirty} busy={busy} onSave={() => void save()} onCancel={() => {
      if (window.confirm('放弃当前标签的未保存修改？')) { setDraft(saved); setKey(''); setClearKey(false); setError(''); setNotice(''); }
    }} />
    <h2 className="font-semibold">{names[service]}</h2>
    <p className="hint">适用范围：全站{service === 'authoring' ? '讲稿生成与校准' : '语音合成'}。由管理员维护，{service === 'authoring' ? '用于 PDF、演示文稿，不占用学生答疑预算。' : '仅合成教师已接受的讲稿。'}保存设置不会调用外部服务。</p>
    <ErrorNotice message={error} />{notice && !dirty && <p role="status" className="hint">{notice}</p>}
    {!draft ? (!error && <Loading />) : <fieldset disabled={busy} className="space-y-4">
      {service === 'speech' && <div className="flex flex-wrap items-center gap-2 rounded-md bg-muted/50 p-3">
        <p className="hint flex-1">{deepseek ? 'DeepSeek 示例：api.deepseek.com · Stella 女声 / Tide 男声 · WAV / PCM。此接口无需模型名称，合成语速固定为 1。' : 'OpenAI 兼容接口：填写服务基础地址、模型和音色。示例仅供配置参考，是否可用以你的供应商为准。'}</p>
        <Button type="button" variant="outline" onClick={() => {
          if (window.confirm('将地址、音色和格式等替换为接口示例？API Key 保持不变，保存后生效。')) applyProtocol(String(draft.protocol), true);
        }}><WandSparkles />填入示例</Button>
      </div>}
      <div className="grid items-start gap-4 md:grid-cols-2 xl:grid-cols-3">{visibleFields.map(field => <div className="field" key={field.key}>
        <Label htmlFor={`${service}-${field.key}`}>{field.label}</Label>
        {field.options ? <SelectField id={`${service}-${field.key}`} value={String(draft[field.key])} onValueChange={value => {
          if (field.key === 'protocol') applyProtocol(value); else setDraft({ ...draft, [field.key]: value });
        }}>
          {field.options.map(value => <option key={value} value={value}>{labels[value] ?? value}</option>)}
        </SelectField> : <Input id={`${service}-${field.key}`} type={field.min === undefined ? 'text' : 'number'} min={field.min} max={field.max} step={field.step}
          placeholder={field.key === 'base_url' && deepseek ? 'https://api.deepseek.com' : field.placeholder}
          value={String(draft[field.key])} onChange={e => setDraft({ ...draft, [field.key]: field.min === undefined ? e.target.value : Number(e.target.value) })} />}
        {field.key === 'base_url' && service === 'speech' && <p className="hint">填写基础地址即可；完整语音接口地址也可识别。留空可停用合成。</p>}
      </div>)}</div>
      <div className="field max-w-lg"><Label htmlFor={`${service}-key`}>API Key {saved?.key_configured ? '（已配置）' : '（未配置）'}</Label>
        <Input id={`${service}-key`} type="password" autoComplete="new-password" value={key} placeholder={saved?.key_configured ? '••••••••' : '填写服务密钥'} disabled={clearKey} onChange={e => setKey(e.target.value)} />
        <Label className="mt-2 gap-2"><Checkbox checked={clearKey} onCheckedChange={value => { setClearKey(!!value); setKey(''); }} />清除已保存密钥</Label>
      </div>
      {deepseek && <p className="hint">学生播放时仍可调速。<a className="underline" href="https://api-docs.deepseek.com/zh-cn/guides/tts/" target="_blank" rel="noreferrer">查看接口说明</a></p>}
      <p className="hint">当前来源：{saved?.source === 'database' ? '后台已保存设置' : '统一环境配置'}。密钥留空会保留现有值。</p>
    </fieldset>}
  </section>;
}
