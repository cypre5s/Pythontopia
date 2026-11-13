import { useEffect, useRef, useState } from "react";
import { Bot, Plus, Trash2 } from "lucide-react";
import { AuthoringWorkspace } from "@/components/authoring-workspace";
import { Empty, ErrorNotice } from "@/components/common";
import { CharacterAssetPicker } from "./character-asset-picker";
import { CharacterPreview } from "@/components/character-preview";
import { SelectField } from "@/components/select-field";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Tabs, TabsList, TabsTrigger } from "./ui/tabs";
import { newId } from "@/lib/id";
import { api, errorMessage } from "@/lib/api";
import type { Authoring, Character, CharacterAsset } from "@/lib/authoring";

export function AssistantEditor({ data, onChange, classroomId, focusTarget, onUploadState }: {
  data: Authoring;
  onChange: (value: Authoring) => void;
  classroomId: number;
  focusTarget?: { roleId?: string };
  onUploadState?: (busy: boolean) => void;
}) {
  const [section, setSection] = useState("basic");
  const [selected, setSelected] = useState(focusTarget?.roleId ?? data.npcs[0]?.id ?? "");
  const [assets, setAssets] = useState<CharacterAsset[]>([]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [frameWidth, setFrameWidth] = useState(16);
  const [frameHeight, setFrameHeight] = useState(20);
  const [previewDirection, setPreviewDirection] = useState<"down" | "left" | "right" | "up">("down");
  const [animate, setAnimate] = useState(false);
  const role = data.npcs.find((item) => item.id === selected);
  const latest = useRef(data);
  const uploadGeneration = useRef(0);
  useEffect(() => { latest.current = data; }, [data]);
  useEffect(() => () => onUploadState?.(false), [onUploadState]);
  useEffect(() => {
    const controller = new AbortController();
    void api.get<CharacterAsset[]>(`/teacher/classrooms/${classroomId}/character-assets`, controller.signal)
      .then((result) => { if (!controller.signal.aborted) setAssets(result); })
      .catch((failure) => { if (!controller.signal.aborted) setError(errorMessage(failure)); });
    return () => { controller.abort(); uploadGeneration.current += 1; };
  }, [classroomId]);
  function updateRole(next: Character) {
    onChange({ ...data, npcs: data.npcs.map((item) => item.id === next.id ? next : item) });
  }
  function addRole() {
    const item: Character = { id: newId("npc"), name: "新的教学角色", portrait: "builtin:portrait:professor",
      sprite: "builtin:sprite:chara_2", persona: "", placement: null, appearance_condition: { always: true } };
    onChange({ ...data, npcs: [...data.npcs, item] });
    setSelected(item.id);
  }
  async function upload(file: File | undefined, kind: "portrait" | "sprite") {
    if (!file || !role) return;
    const roleId = role.id;
    const generation = ++uploadGeneration.current;
    const body = new FormData();
    body.set("file", file);
    body.set("kind", kind);
    body.set("frame_width", String(frameWidth));
    body.set("frame_height", String(frameHeight));
    body.set("owner_npc_id", roleId);
    body.set("expected_revision", String(data.revision));
    setBusy(true);
    onUploadState?.(true);
    setError("");
    try {
      const asset = await api.upload<CharacterAsset>(`/teacher/classrooms/${classroomId}/character-assets`, body);
      if (generation !== uploadGeneration.current) return;
      setAssets((items) => [...items, asset]);
      const current = latest.current;
      onChange({ ...current, npcs: current.npcs.map((item) => item.id === roleId ? { ...item, [kind]: asset.id } : item) });
    } catch (failure) { if (generation === uploadGeneration.current) setError(errorMessage(failure)); }
    finally { if (generation === uploadGeneration.current) { setBusy(false); onUploadState?.(false); } }
  }
  function removeRole() {
    if (!role || !window.confirm(`删除角色「${role.name}」？保存时将检查活动、流程和历史记录引用。`)) return;
    onChange({ ...data, npcs: data.npcs.filter((item) => item.id !== role.id) });
    setSelected("");
  }
  return <AuthoringWorkspace tools={<>
    <Button variant="ghost" className="justify-start" disabled={busy || data.npcs.length >= 100} onClick={addRole}><Plus />新增教学角色</Button>
    {data.npcs.map((item) => <Button key={item.id} variant={selected === item.id ? "secondary" : "ghost"}
      className="justify-start" disabled={busy} onClick={() => setSelected(item.id)}><Bot /><span className="truncate">{item.name}</span></Button>)}
  </>} toolbar={<Tabs value={section} onValueChange={value => setSection(String(value))}><TabsList aria-label="角色配置"><TabsTrigger value="basic">基础信息</TabsTrigger><TabsTrigger value="portrait">对话立绘</TabsTrigger><TabsTrigger value="sprite">像素角色</TabsTrigger></TabsList></Tabs>}>
    <div className="space-y-5 p-5">
      <ErrorNotice message={error} />
      {role ? <fieldset disabled={busy} aria-busy={busy} className="space-y-5">
        <div hidden={section !== "basic"} className="space-y-4">
          <div className="field"><Label htmlFor="character-name">角色名称</Label><Input id="character-name" value={role.name} maxLength={80}
            onChange={(event) => updateRole({ ...role, name: event.target.value })} /></div>
          <div className="field"><Label htmlFor="character-persona">AI 助教人设描述</Label><Textarea id="character-persona" value={role.persona} maxLength={4000} rows={8}
            placeholder="角色的身份、性格、说话习惯与教学风格" onChange={(event) => updateRole({ ...role, persona: event.target.value })} />
            <p className="hint">当前只保存人设，尚未用于 AI 回复。</p></div>
          <p className="text-sm text-muted-foreground">{role.placement
            ? `${data.catalog.maps.find((map) => map.key === role.placement?.map_key)?.label ?? role.placement.map_key}（${role.placement.tile_x}, ${role.placement.tile_y}）`
            : "未放置，可到地图交互与开放规则中安排位置。"}
          </p>
          <Button variant="ghost" onClick={removeRole}><Trash2 />删除角色</Button>
        </div>
        <div hidden={section === "basic"} className="space-y-5">
          {(["portrait", "sprite"] as const).map((kind) => <section key={kind} hidden={section !== kind} className="space-y-3 rounded-lg border p-4">
            <Label htmlFor={`character-${kind}`}>{kind === "portrait" ? "对话立绘" : "地图像素角色"}</Label>
            <CharacterAssetPicker id={`character-${kind}`} value={role[kind]}
              assets={assets.filter(asset => asset.kind === kind && (asset.id.startsWith("builtin:") || asset.id === role[kind]))}
              onChange={value => updateRole({ ...role, [kind]: value })} />
            <div className="flex min-h-36 items-center justify-center rounded-md bg-muted/40">
              <CharacterPreview asset={assets.find((asset) => asset.id === role[kind])} direction={previewDirection} animate={animate && kind === "sprite"} />
            </div>
            {kind === "sprite" && <>
              <div className="flex items-center gap-3">
                <SelectField aria-label="预览朝向" value={previewDirection} onValueChange={(value) => setPreviewDirection(value as typeof previewDirection)}>
                  <option value="down">向下</option><option value="left">向左</option><option value="right">向右</option><option value="up">向上</option>
                </SelectField>
                <Label><Checkbox checked={animate} onCheckedChange={setAnimate} />预览行走</Label>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div className="field"><Label htmlFor="frame-width">上传帧宽（像素）</Label><Input id="frame-width" type="number" min={8} max={256} value={frameWidth} onChange={(event) => setFrameWidth(Number(event.target.value))} /></div>
                <div className="field"><Label htmlFor="frame-height">上传帧高（像素）</Label><Input id="frame-height" type="number" min={8} max={256} value={frameHeight} onChange={(event) => setFrameHeight(Number(event.target.value))} /></div>
              </div>
              <p className="hint">PNG 三列四行，方向从上至下为下、左、右、上。每行中间一帧为站立姿势。</p>
            </>}
            <Label htmlFor={`upload-${kind}`}>{busy ? "正在上传…" : "上传自定义素材"}</Label>
            <Input id={`upload-${kind}`} type="file" accept={kind === "sprite" ? "image/png" : "image/png,image/jpeg,image/webp"}
              onChange={(event) => { void upload(event.target.files?.[0], kind); event.target.value = ""; }} />
          </section>)}
          <p className="hint">每个角色最多一张自定义立绘和一张自定义像素图。上传替换当前图片；保存成功后清理旧图，删除角色时一并清理。取消编辑保留原图。</p>
        </div>
      </fieldset> : <Empty title="选择或新增教学角色" />}
    </div>
  </AuthoringWorkspace>;
}
