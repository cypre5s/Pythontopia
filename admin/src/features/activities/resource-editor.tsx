import { useEffect, useState } from "react";
import { ArrowDown, ArrowUp, FileText, Plus, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { SelectField } from "@/components/select-field";
import { api, errorMessage, scoped } from "@/lib/api";
import type { ActivityResource, Material } from "@/lib/types";
import { HighlightEditor } from "./highlight-editor";

export function ResourceEditor({ resources, materials, chapterId, classroomId, onChange }: {
  resources: ActivityResource[]; materials: Material[]; chapterId: number; classroomId: number;
  onChange: (rows: ActivityResource[]) => void;
}) {
  const [selected, setSelected] = useState("");
  function update(index: number, row: ActivityResource) { onChange(resources.map((value, i) => i === index ? row : value)); }
  function move(index: number, delta: number) {
    const next = [...resources]; [next[index], next[index + delta]] = [next[index + delta], next[index]]; onChange(next);
  }
  return <section className="space-y-3 mb-4" aria-label="活动资料与阅读要求">
    <Label className="flex gap-2 items-center"><FileText size={16} />活动资料</Label>
    <p className="hint">按顺序配置每份资料的要求。必读资料全部完成后才能完成活动，可选资料保留进度但不阻挡完成。</p>
    {resources.map((row, index) => <section key={row.material_id} className="rounded-md border p-3 space-y-3">
      <div className="flex items-center gap-2">
        <strong className="min-w-0 flex-1 truncate" title={materials.find(m => m.id === row.material_id)?.title}>{index + 1}. {materials.find(m => m.id === row.material_id)?.title ?? "资料不可用，请移除"}</strong>
        <label className="flex items-center gap-1 text-sm"><Checkbox checked={row.required} onCheckedChange={value => update(index, { ...row, required: value === true })} />必读</label>
        <Button type="button" variant="ghost" size="icon" aria-label={`上移资料${index + 1}`} disabled={!index} onClick={() => move(index, -1)}><ArrowUp /></Button>
        <Button type="button" variant="ghost" size="icon" aria-label={`下移资料${index + 1}`} disabled={index === resources.length - 1} onClick={() => move(index, 1)}><ArrowDown /></Button>
        <Button type="button" variant="ghost" size="icon" aria-label={`移除资料${index + 1}`} onClick={() => onChange(resources.filter((_, i) => i !== index))}><Trash2 /></Button>
      </div>
      <ResourceRules classroomId={classroomId} resource={row} kind={materials.find(m => m.id === row.material_id)?.kind} onChange={value => update(index, value)} />
    </section>)}
    <div className="flex gap-2">
      <SelectField value={selected} onValueChange={setSelected} placeholder="选择要添加的资料" aria-label="添加活动资料">
        {materials.filter(m => m.chapter_ids.includes(chapterId) && m.status === "ready" && !resources.some(row => row.material_id === m.id)).map(m => <option key={m.id} value={m.id}>{m.title}</option>)}
      </SelectField>
      <Button type="button" variant="outline" disabled={!selected || resources.length >= 64} onClick={() => {
        const material = materials.find(m => m.id === Number(selected));
        if (!material || !material.chapter_ids.includes(chapterId) || material.status !== "ready" || resources.some(row => row.material_id === material.id)) return;
        onChange([...resources, { material_id: material.id, required: true, learning: material.kind === "video" ? { video: { minimum_seconds: 60 } } : { reading: { scope: "all" } } }]); setSelected("");
      }}><Plus />添加资料</Button>
    </div>
  </section>;
}

function ResourceRules({ resource, kind, classroomId, onChange }: { classroomId: number; resource: ActivityResource; kind?: string; onChange: (row: ActivityResource) => void }) {
  const [pages, setPages] = useState<{ id: number; page_no: number; title: string }[]>([]);
  const [error, setError] = useState("");
  const learning = resource.learning;
  const reading = learning.reading ?? { scope: "all" as const };
  useEffect(() => {
    let live = true;
    if (kind === "video" || reading.scope !== "pages") return;
    api.get<{ pages: typeof pages }>(scoped(`/teacher/materials/${resource.material_id}`, classroomId)).then(value => { if (live) { setPages(value.pages); setError(""); } }).catch(cause => { if (live) setError(errorMessage(cause)); });
    return () => { live = false; };
  }, [resource.material_id, kind, reading.scope, classroomId]);
  const update = (value: ActivityResource["learning"]) => onChange({ ...resource, learning: value });
  if (kind === "video") {
    const video = learning.video ?? {};
    return <>
      <label className="flex gap-2 items-center text-sm"><Checkbox checked={video.minimum_seconds !== undefined} onCheckedChange={value => update({ ...learning, video: { ...video, minimum_seconds: value === true ? 60 : undefined } })} />最低有效观看时长</label>
      {video.minimum_seconds !== undefined && <label className="flex items-center gap-2 text-sm"><Input type="number" min={1} max={86400} aria-label="最低有效观看秒数" value={video.minimum_seconds} onChange={event => update({ ...learning, video: { ...video, minimum_seconds: Number(event.target.value) } })} />秒</label>}
      <HighlightEditor title="必须观看的关键片段（秒）" video required rows={(video.segments ?? []).map(row => ({ start: row.start_seconds, end: row.end_seconds, label: row.label }))} onChange={rows => update({ ...learning, video: { ...video, segments: rows.map(row => ({ start_seconds: row.start, end_seconds: row.end, label: row.label })) } })} />
      <p className="hint">同时设置时长和片段时，两项都要达成。拖动跳过不计入有效观看。</p>
    </>;
  }
  return <>
    <SelectField aria-label="资料阅读要求" value={reading.scope} onValueChange={scope => update({ ...learning, reading: scope === "all" ? { scope } : scope === "range" ? { scope, start_page: 1, end_page: 1 } : { scope: "pages", page_ids: [] } })}>
      <option value="all">阅读每一页</option><option value="range">阅读指定页范围</option><option value="pages">阅读关键页</option>
    </SelectField>
    {reading.scope === "range" && <div className="grid grid-cols-2 gap-3">
      <label className="text-sm">起始页<Input type="number" min={1} value={reading.start_page} onChange={event => update({ ...learning, reading: { ...reading, start_page: Number(event.target.value) } })} /></label>
      <label className="text-sm">结束页<Input type="number" min={reading.start_page} value={reading.end_page} onChange={event => update({ ...learning, reading: { ...reading, end_page: Number(event.target.value) } })} /></label>
    </div>}
    {reading.scope === "pages" && <div className="max-h-48 overflow-auto space-y-1" aria-label="选择关键页">{error && <p role="alert">{error}</p>}{pages.map(page => <label key={page.id} className="flex items-center gap-2 text-sm"><Checkbox checked={reading.page_ids?.includes(page.id) ?? false} onCheckedChange={checked => update({ ...learning, reading: { ...reading, page_ids: checked === true ? [...(reading.page_ids ?? []), page.id] : reading.page_ids?.filter(id => id !== page.id) } })} />{page.page_no}. {page.title}</label>)}</div>}
  </>;
}
