import { useEffect, useRef, useState, type PointerEvent } from "react";
import { MapArtwork } from "./map-artwork";
import { ConditionEditor } from "./condition-editor";
import { SelectField } from "./select-field";
import { Button } from "./ui/button";
import { Input } from "./ui/input";
import { Label } from "./ui/label";
import type { Authoring, Character, Flow } from "@/lib/authoring";

const emptyFlow: Flow = { id: "", title: "", chapter_id: null, quest_id: null,
  content: { schema_version: 1, mode: "public", bindings: {}, entries: {}, nodes: [] } };

export function CharacterPlacement({ data, onChange, focusId, onSelect }: {
  data: Authoring; onChange: (value: Authoring) => void; focusId?: string; onSelect?: (id: string) => void;
}) {
  const [selected, setSelected] = useState(focusId ?? data.npcs[0]?.id ?? "");
  const role = data.npcs.find((item) => item.id === selected);
  const [mapKey, setMapKey] = useState(role?.placement?.map_key ?? data.catalog.maps[0]?.key ?? "");
  const drag = useRef<{ id: string; pointer: number } | null>(null);
  const [pending, setPending] = useState<{ x: number; y: number } | null>(null);
  const map = data.catalog.maps.find((item) => item.key === mapKey);
  const focusedMap = data.npcs.find((item) => item.id === focusId)?.placement?.map_key;
  useEffect(() => {
    if (!focusId) return;
    const frame = requestAnimationFrame(() => { setSelected(focusId); if (focusedMap) setMapKey(focusedMap); });
    return () => cancelAnimationFrame(frame);
  }, [focusId, focusedMap]);
  function update(next: Character) {
    onChange({ ...data, npcs: data.npcs.map((item) => item.id === next.id ? next : item) });
  }
  function position(event: PointerEvent<SVGSVGElement>) {
    const bounds = event.currentTarget.getBoundingClientRect();
    return { x: Math.max(0, Math.min((map?.width ?? 1) - 1, Math.floor((event.clientX - bounds.left) / bounds.width * (map?.width ?? 1)))),
      y: Math.max(0, Math.min((map?.height ?? 1) - 1, Math.floor((event.clientY - bounds.top) / bounds.height * (map?.height ?? 1)))) };
  }
  function place(id: string, point: { x: number; y: number }) {
    const item = data.npcs.find((npc) => npc.id === id);
    if (item) update({ ...item, placement: { map_key: mapKey, tile_x: point.x, tile_y: point.y, direction: item.placement?.direction ?? "down" } });
  }
  return <div className="grid gap-5 p-4 xl:grid-cols-[minmax(0,1fr)_20rem]">
    <div className="space-y-3">
      <Label htmlFor="character-map">摆放地图</Label>
      <SelectField id="character-map" value={mapKey} onValueChange={setMapKey}>
        {data.catalog.maps.map((item) => <option key={item.key} value={item.key}>{item.label}</option>)}
      </SelectField>
      {map && <svg role="group" aria-label="教学角色摆放地图" viewBox={`0 0 ${map.width} ${map.height}`}
        className="w-full touch-none rounded-md border bg-muted" style={{ aspectRatio: `${map.width}/${map.height}` }}
        onPointerDown={(event) => {
          if (event.button !== 0) return;
          const target = (event.target as Element).closest("[data-character]")?.getAttribute("data-character");
          const id = target ?? selected;
          if (!id) return;
          setSelected(id);
          onSelect?.(id);
          drag.current = { id, pointer: event.pointerId };
          setPending(position(event));
          event.currentTarget.setPointerCapture(event.pointerId);
        }}
        onPointerMove={(event) => { if (drag.current?.pointer === event.pointerId) setPending(position(event)); }}
        onPointerUp={(event) => {
          if (drag.current?.pointer !== event.pointerId) return;
          place(drag.current.id, position(event));
          drag.current = null;
          setPending(null);
        }}
        onPointerCancel={() => { drag.current = null; setPending(null); }}>
        <MapArtwork mapKey={mapKey} width={map.width} height={map.height} />
        {data.npcs.filter((item) => item.placement?.map_key === mapKey).map((item) => {
          const point = pending && drag.current?.id === item.id ? pending : { x: item.placement!.tile_x, y: item.placement!.tile_y };
          return <g key={item.id} data-character={item.id} role="button" aria-label={`选择 ${item.name}`} tabIndex={0}
            onKeyDown={(event) => { if (event.key === "Enter" || event.key === " ") { event.preventDefault(); setSelected(item.id); onSelect?.(item.id); } }}
            className="group cursor-grab outline-none">
            <circle className="group-focus-visible:stroke-amber-400" cx={point.x + 0.5} cy={point.y + 0.5} r={0.55} fill={selected === item.id ? "#f5b55b" : "#f9fafb"} stroke="#263a30" strokeWidth={0.12} />
            <text pointerEvents="none" x={point.x + 0.5} y={point.y - 0.25} textAnchor="middle" fontSize={0.7} fill="#fff" stroke="#263a30" strokeWidth={0.12} paintOrder="stroke">{item.name}</text>
          </g>;
        })}
        {pending && !role?.placement && <circle cx={pending.x + 0.5} cy={pending.y + 0.5} r={0.55} fill="#f5b55b" />}
      </svg>}
      <p className="hint">选择角色后点击地图放置，拖动标记移动；也可在右侧输入坐标。保存时校验位置可达性。</p>
    </div>
    <div className="space-y-4">
      <h3 className="font-medium">{role?.name ?? "从左侧选择教学角色"}</h3>
      {role && <>
        {role.placement ? <>
          <div className="grid grid-cols-2 gap-3">
            {(["tile_x", "tile_y"] as const).map((field) => <div className="field" key={field}>
              <Label htmlFor={field}>{field === "tile_x" ? "横坐标" : "纵坐标"}</Label>
              <Input id={field} type="number" min={0} value={role.placement![field]} onChange={(event) => update({ ...role, placement: { ...role.placement!, [field]: Number(event.target.value) } })} />
            </div>)}
          </div>
          <Label htmlFor="character-direction">朝向</Label>
          <SelectField id="character-direction" value={role.placement.direction} onValueChange={(value) => update({ ...role, placement: { ...role.placement!, direction: value as NonNullable<Character["placement"]>["direction"] } })}>
            <option value="down">向下</option><option value="left">向左</option><option value="right">向右</option><option value="up">向上</option>
          </SelectField>
          <Button variant="outline" onClick={() => update({ ...role, placement: null })}>撤回地图放置</Button>
        </> : <p className="hint">尚未放置，点击地图即可安排位置。</p>}
        <Label>出现条件</Label>
        <ConditionEditor value={role.appearance_condition} catalog={data.catalog} flow={emptyFlow}
          onChange={(appearance_condition) => update({ ...role, appearance_condition })} />
      </>}
    </div>
  </div>;
}
