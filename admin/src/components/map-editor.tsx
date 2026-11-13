import { useEffect, useId, useRef, useState, type MouseEvent, type PointerEvent } from "react";
import { DoorOpen, MapPin, MousePointer2, SquareDashedMousePointer, Trash2, Users } from "lucide-react";
import { AuthoringInspector, AuthoringWorkspace } from "@/components/authoring-workspace";
import { Empty } from "@/components/common";
import { ConditionEditor } from "@/components/condition-editor";
import { SelectField } from "@/components/select-field";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Separator } from "@/components/ui/separator";
import { newId } from "@/lib/id";
import type { Authoring, Flow, Interaction, Point } from "@/lib/authoring";
import { MapArtwork } from "./map-artwork";
import { CharacterPlacement } from "./character-placement";

const emptyFlow: Flow = {
  id: "",
  title: "",
  chapter_id: null,
  quest_id: null,
  content: {
    schema_version: 1,
    mode: "public",
    bindings: {},
    entries: {},
    nodes: [],
  },
};
const defaultRule: BuildingRule = {
  enabled: false,
  default_unlocked: false,
  blocked_message: "此课程尚未开放该建筑。",
};
const regionFields = [
  ["x", "横坐标"],
  ["y", "纵坐标"],
  ["width", "宽度"],
  ["height", "高度"],
] as const;

type Region = Extract<Interaction["target"], { region: unknown }>["region"];
type BuildingRule = Authoring["configuration"]["buildings"][string];
type View = "map" | "buildings" | "npcs";
export type MapFocusTarget = { kind: "interaction" | "building" | "npc"; id: string };
type Inspector = MapFocusTarget & { anchor: HTMLElement | Point };
const noInteractions: Interaction[] = [];

function markerAnchor(element: SVGElement): Point {
  const bounds = element.getBoundingClientRect();
  return { x: bounds.left + bounds.width / 2, y: bounds.top + bounds.height / 2 };
}

function eventAnchor(event: MouseEvent | PointerEvent): Point {
  return { x: event.clientX, y: event.clientY };
}

function regionBetween(start: Point, end: Point): Region {
  return {
    x: Math.min(start.x, end.x),
    y: Math.min(start.y, end.y),
    width: Math.abs(start.x - end.x) + 1,
    height: Math.abs(start.y - end.y) + 1,
  };
}

export function MapEditor({
  data,
  onChange,
  focusTarget,
}: {
  data: Authoring;
  onChange: (value: Authoring) => void;
  focusTarget?: MapFocusTarget | null;
}) {
  const [mapKey, setMapKey] = useState(data.catalog.maps[0]?.key ?? "");
  const [characterId, setCharacterId] = useState<string>();
  const [view, setView] = useState<View>("map");
  const [inspector, setInspector] = useState<Inspector | null>(null);
  const [draw, setDraw] = useState(false);
  const [draftRegion, setDraftRegion] = useState<Region | null>(null);
  const [pendingFocus, setPendingFocus] = useState<MapFocusTarget | null>(null);
  const handledFocus = useRef<MapFocusTarget | null>(null);
  const start = useRef<{ pointer: number; point: Point } | null>(null);
  const grid = useId();
  const { world, buildings } = data.configuration;
  const map = data.catalog.maps.find((item) => item.key === mapKey);
  const interactions = world.interactions ?? noInteractions;
  const visible = interactions.filter((item) => item.map_key === mapKey);
  const interaction = inspector?.kind === "interaction" ? visible.find((item) => item.id === inspector.id) : undefined;
  const building = inspector?.kind === "building" ? data.catalog.buildings.find((item) => item.id === inspector.id) : undefined;
  const npc = inspector?.kind === "npc" ? data.catalog.npcs.find((item) => item.id === inspector.id) : undefined;
  const buildingRule = building ? buildings[building.id] ?? defaultRule : defaultRule;
  const entryKey = interaction ? `interaction:${interaction.id}` : "";
  const boundFlow = data.flows.find((item) => entryKey in item.content.entries);
  const region = interaction && "region" in interaction.target ? interaction.target.region : null;
  const width = map?.width ?? 1;
  const height = map?.height ?? 1;

  useEffect(() => {
    if (!focusTarget || handledFocus.current === focusTarget) return;
    const frame = requestAnimationFrame(() => {
      handledFocus.current = focusTarget;
      if (focusTarget.kind === "interaction") {
        const item = interactions.find(({ id }) => id === focusTarget.id);
        if (!item) return;
        setMapKey(item.map_key);
        setView("map");
      } else {
        const items = focusTarget.kind === "building" ? data.catalog.buildings : data.catalog.npcs;
        if (!items.some(({ id }) => id === focusTarget.id)) return;
        setView(focusTarget.kind === "building" ? "buildings" : "npcs");
      }
      start.current = null;
      setDraftRegion(null);
      setDraw(false);
      setInspector(null);
      setPendingFocus(focusTarget);
    });
    return () => cancelAnimationFrame(frame);
  }, [focusTarget, interactions, data.catalog.buildings, data.catalog.npcs]);

  useEffect(() => {
    if (!pendingFocus) return;
    const frame = requestAnimationFrame(() => {
      const anchor = document.getElementById(`${grid}-${pendingFocus.kind}-${pendingFocus.id}`);
      if (anchor) {
        anchor.scrollIntoView?.({ block: "nearest", inline: "nearest" });
        setInspector({ ...pendingFocus, anchor });
      }
      setPendingFocus(null);
    });
    return () => cancelAnimationFrame(frame);
  }, [grid, pendingFocus]);

  function updateWorld(next: Authoring["configuration"]["world"]) {
    onChange({ ...data, configuration: { ...data.configuration, world: next } });
  }
  function updateInteraction(next: Interaction) {
    updateWorld({
      ...world,
      interactions: interactions.map((item) => item.id === next.id ? next : item),
    });
  }
  function updateBuilding(id: string, next: BuildingRule) {
    onChange({
      ...data,
      configuration: { ...data.configuration, buildings: { ...buildings, [id]: next } },
    });
  }
  function close() {
    setInspector(null);
  }
  function clearDraw() {
    start.current = null;
    setDraftRegion(null);
  }
  function chooseTool(nextView: View, drawing = false) {
    close();
    clearDraw();
    setView(nextView);
    setDraw(drawing);
  }
  function inspect(id: string, anchor: Inspector["anchor"]) {
    setInspector({ kind: "interaction", id, anchor });
  }
  function create(target: Interaction["target"], anchor: Point, label = "新的交互点") {
    const item: Interaction = {
      id: newId("interaction"),
      label,
      prompt: "点击交互",
      trigger: "click",
      map_key: mapKey,
      target,
      when: { always: true },
    };
    updateWorld({ ...world, interactions: [...interactions, item] });
    inspect(item.id, anchor);
    setDraw(false);
    clearDraw();
  }
  function openObject(id: string, label: string, anchor: Point) {
    const existing = visible.find((item) => "object_id" in item.target && item.target.object_id === id);
    if (existing) inspect(existing.id, anchor);
    else create({ object_id: id }, anchor, label);
  }
  function point(event: PointerEvent<SVGSVGElement>, clampToMap = true): Point | null {
    const transform = event.currentTarget.getScreenCTM();
    if (!transform) return null;
    const position = new DOMPoint(event.clientX, event.clientY).matrixTransform(transform.inverse());
    if (!clampToMap && (position.x < 0 || position.x >= width || position.y < 0 || position.y >= height)) {
      return null;
    }
    return {
      x: Math.min(width - 1, Math.max(0, Math.floor(position.x))),
      y: Math.min(height - 1, Math.max(0, Math.floor(position.y))),
    };
  }
  function setFlow(id: string) {
    if (!interaction || boundFlow?.id === id) return;
    onChange({
      ...data,
      flows: data.flows.map((item) => {
        const entries = { ...item.content.entries };
        delete entries[entryKey];
        if (item.id === id) {
          entries[entryKey] = [{ when: { always: true }, node: item.content.nodes[0]?.id ?? "" }];
        }
        return { ...item, content: { ...item.content, entries } };
      }),
    });
  }
  function removeInteraction() {
    if (!interaction || !window.confirm("删除此交互点及其流程入口绑定？")) return;
    onChange({
      ...data,
      flows: data.flows.map((item) => {
        const entries = { ...item.content.entries };
        delete entries[entryKey];
        return { ...item, content: { ...item.content, entries } };
      }),
      configuration: {
        ...data.configuration,
        world: { ...world, interactions: interactions.filter((item) => item.id !== interaction.id) },
      },
    });
    close();
  }

  return (
    <AuthoringWorkspace
      tools={
        <>
          <p className="px-2 pb-1 text-xs font-medium text-muted-foreground">地图工具</p>
          <Button className="justify-start" variant={view === "map" && !draw ? "secondary" : "ghost"}
            aria-pressed={view === "map" && !draw} onClick={() => chooseTool("map")}>
            <MousePointer2 />点选地图对象
          </Button>
          <Button className="justify-start" variant={view === "map" && draw ? "secondary" : "ghost"}
            aria-pressed={view === "map" && draw} disabled={!map} onClick={() => chooseTool("map", !draw)}>
            <SquareDashedMousePointer />绘制交互区域
          </Button>
          <Separator className="my-2" />
          <p className="px-2 pb-1 text-xs font-medium text-muted-foreground">开放规则</p>
          <Button className="justify-start" variant={view === "buildings" ? "secondary" : "ghost"}
            aria-pressed={view === "buildings"} onClick={() => chooseTool("buildings")}>
            <DoorOpen />建筑开放规则
          </Button>
          <Button className="justify-start" variant={view === "npcs" ? "secondary" : "ghost"}
            aria-pressed={view === "npcs"} onClick={() => chooseTool("npcs")}>
            <Users />角色摆放与出现
          </Button>
          {view === "npcs" && <><Separator className="my-2" /><p className="px-2 text-xs text-muted-foreground">教学角色</p>{data.npcs.map(item => <Button key={item.id} variant={(characterId ?? data.npcs[0]?.id) === item.id ? "secondary" : "ghost"} className="min-w-0 justify-start" title={`${item.name} · ${item.placement ? "已放置" : "未放置"}`} onClick={() => setCharacterId(item.id)}><Users /><span className="truncate">{item.name}</span><span className="ml-auto text-xs text-muted-foreground">{item.placement ? "已放置" : "未放置"}</span></Button>)}</>}
          {view === "map" && visible.length > 0 && (
            <>
              <Separator className="my-2" />
              <p className="px-2 pb-1 text-xs font-medium text-muted-foreground">本地图的交互点</p>
              {visible.map((item) => (
                <Button key={item.id} id={`${grid}-interaction-${item.id}`} variant={interaction?.id === item.id ? "secondary" : "ghost"}
                  className="min-w-0 justify-start" title={item.label}
                  onClick={(event) => inspect(item.id, event.currentTarget)}
                  onContextMenu={(event) => { event.preventDefault(); inspect(item.id, eventAnchor(event)); }}>
                  <MapPin /><span className="truncate">{item.label}</span>
                </Button>
              ))}
            </>
          )}
        </>
      }
      toolbar={
        view === "map" ? (
          <>
            <SelectField
              aria-label="选择地图"
              value={mapKey}
              onValueChange={(key) => { setMapKey(key); chooseTool("map"); }}
            >
              {data.catalog.maps.map((item) => <option key={item.key} value={item.key}>{item.label}</option>)}
            </SelectField>
            <Badge variant="outline">{draw ? "区域绘制" : "对象点选"}</Badge>
          </>
        ) : (
          <span className="px-2 text-sm font-medium">
            {view === "buildings" ? "建筑开放规则" : "角色摆放与出现"}
          </span>
        )
      }
    >
      {view === "npcs" && <CharacterPlacement onSelect={setCharacterId} data={data} onChange={onChange} focusId={characterId ?? (focusTarget?.kind === "npc" ? focusTarget.id : undefined)} />}
      {view === "map" ? (
        map ? (
          <div className="space-y-3 p-4">
            <svg
              className={`map-authoring ${draw ? "drawing" : ""}`}
              viewBox={`0 0 ${width} ${height}`}
              role="group"
              aria-label="地图交互区域"
              onPointerDown={(event) => {
                if (!draw || event.button !== 0) return;
                const position = point(event, false);
                if (!position) return;
                start.current = { point: position, pointer: event.pointerId };
                setDraftRegion(regionBetween(position, position));
                event.currentTarget.setPointerCapture(event.pointerId);
              }}
              onPointerMove={(event) => {
                if (start.current?.pointer !== event.pointerId) return;
                const position = point(event);
                if (position) setDraftRegion(regionBetween(start.current.point, position));
              }}
              onPointerUp={(event) => {
                if (start.current?.pointer !== event.pointerId) return;
                const position = point(event);
                if (position) create({ region: regionBetween(start.current.point, position) }, eventAnchor(event));
                else clearDraw();
              }}
              onPointerCancel={clearDraw}
              onLostPointerCapture={clearDraw}
            >
              <defs>
                <pattern id={grid} width="1" height="1" patternUnits="userSpaceOnUse">
                  <path d="M 1 0 L 0 0 0 1" fill="none" stroke="#f7ffef" strokeWidth=".045" />
                </pattern>
              </defs>
              <MapArtwork key={mapKey} mapKey={mapKey} width={width} height={height} />
              <rect width={width} height={height} fill={`url(#${grid})`} opacity={draw ? 0.75 : 0.22} pointerEvents="none" />
              {map.objects.flatMap((object) =>
                object.points.filter((item) => item.map_key === mapKey).map((tile, index) => (
                  <g key={`${object.id}-${index}`} className="map-object" role="button" tabIndex={draw ? -1 : 0}
                    aria-label={`选择地图对象 ${object.label}`}
                    onClick={(event) => {
                      if (!draw) openObject(object.id, object.label, markerAnchor(event.currentTarget));
                    }}
                    onContextMenu={(event) => {
                      const existing = visible.find((item) => "object_id" in item.target && item.target.object_id === object.id);
                      if (draw || !existing) return;
                      event.preventDefault();
                      inspect(existing.id, eventAnchor(event));
                    }}
                    onKeyDown={(event) => {
                      if (draw || !["Enter", " "].includes(event.key)) return;
                      event.preventDefault();
                      openObject(object.id, object.label, markerAnchor(event.currentTarget));
                    }}
                  >
                    <rect x={tile.tile_x - 0.1} y={tile.tile_y - 0.1} width={1.2} height={1.2} rx={0.2} />
                    <text x={tile.tile_x + 0.5} y={tile.tile_y - 0.35} textAnchor="middle" fontSize={Math.max(0.48, width / 78)}>
                      {object.label}
                    </text>
                  </g>
                )),
              )}
              {visible.flatMap((item) => {
                const selected = item.id === interaction?.id;
                const target = item.target;
                const marker = {
                  role: "button",
                  tabIndex: draw ? -1 : 0,
                  className: "cursor-pointer focus-visible:outline-none focus-visible:stroke-orange-700",
                  "aria-label": `编辑交互点 ${item.label}`,
                  onClick: (event: MouseEvent<SVGElement>) => {
                    if (!draw) inspect(item.id, markerAnchor(event.currentTarget));
                  },
                  onContextMenu: (event: MouseEvent<SVGElement>) => {
                    if (draw) return;
                    event.preventDefault();
                    inspect(item.id, eventAnchor(event));
                  },
                  onKeyDown: (event: React.KeyboardEvent<SVGElement>) => {
                    if (draw || !["Enter", " "].includes(event.key)) return;
                    event.preventDefault();
                    inspect(item.id, markerAnchor(event.currentTarget));
                  },
                };
                if ("region" in target) {
                  return (
                    <rect key={item.id} {...target.region} {...marker} pointerEvents={draw ? "none" : undefined}
                      fill={selected ? "#52785e60" : "#52785e25"}
                      stroke={selected ? "#c76527" : "#52785e"} strokeWidth={0.12} />
                  );
                }
                return map.objects.find((object) => object.id === target.object_id)?.points
                  .filter((tile) => tile.map_key === mapKey)
                  .map((tile, index) => (
                    <circle key={`${item.id}-${index}`} {...marker} cx={tile.tile_x + 0.5} cy={tile.tile_y + 0.5}
                      r={0.87} fill="none" stroke={selected ? "#ffbf70" : "#37685a"} strokeWidth={0.24}
                      pointerEvents={draw ? "none" : "stroke"} />
                  )) ?? [];
              })}
              {draftRegion && <rect {...draftRegion} fill="#52785e40" stroke="#52785e" strokeWidth={0.12} pointerEvents="none" />}
            </svg>
            <p className="hint">
              {draw ? "在地图上拖出区域，松开后设置交互。" : "点击对象创建交互，或打开已有交互的属性。"}
              底图、碰撞与建筑入口由系统维护。
            </p>
          </div>
        ) : <Empty title="暂无可编辑地图" />
      ) : (
        <div className="grid content-start gap-3 p-4 sm:grid-cols-2 lg:grid-cols-3">
          {view === "buildings" ? data.catalog.buildings.map((item) => (
            <Button key={item.id} id={`${grid}-building-${item.id}`} variant="outline" className="h-auto min-w-0 justify-start gap-3 p-3 text-left"
              onClick={(event) => setInspector({ kind: "building", id: item.id, anchor: event.currentTarget })}
              onContextMenu={(event) => {
                event.preventDefault();
                setInspector({ kind: "building", id: item.id, anchor: eventAnchor(event) });
              }}>
              <DoorOpen className="text-muted-foreground" />
              <span className="grid min-w-0 gap-1">
                <span className="truncate">{item.label}</span>
                <Badge variant="secondary">{item.has_interior ? buildings[item.id]?.enabled ? "允许进入" : "未开放" : "无可用室内"}</Badge>
              </span>
            </Button>
          )) : null}
          {view === "buildings" && !data.catalog.buildings.length && <Empty title="暂无建筑" />}

        </div>
      )}
      {inspector && (
        <AuthoringInspector
          anchor={inspector.anchor}
          title={interaction ? "交互入口" : building?.label ?? npc?.name ?? "地图属性"}
          onClose={close}
        >
          {interaction && (
            <>
              <div className="field">
                <Label htmlFor="interaction-name">交互名称</Label>
                <Input id="interaction-name" value={interaction.label}
                  onChange={(event) => updateInteraction({ ...interaction, label: event.target.value })} />
              </div>
              <div className="field">
                <Label htmlFor="interaction-prompt">提示文字</Label>
                <Input id="interaction-prompt" value={interaction.prompt}
                  onChange={(event) => updateInteraction({ ...interaction, prompt: event.target.value })} />
              </div>
              <div className="field">
                <Label htmlFor="interaction-trigger">触发方式</Label>
                <SelectField id="interaction-trigger" value={interaction.trigger}
                  onValueChange={(value) => updateInteraction({ ...interaction, trigger: value === "enter" ? "enter" : "click" })}>
                  <option value="click">点击</option>
                  {region && <option value="enter">进入区域</option>}
                </SelectField>
              </div>
              {region && (
                <fieldset className="field">
                  <legend>区域位置（格）</legend>
                  <div className="grid grid-cols-2 gap-2">
                    {regionFields.map(([key, label]) => (
                      <label key={key} className="text-xs">
                        {label}
                        <Input aria-label={key} type="number" min={key === "x" || key === "y" ? 0 : 1}
                          value={region[key]}
                          onChange={(event) => updateInteraction({
                            ...interaction,
                            target: { region: { ...region, [key]: Number(event.target.value) } },
                          })} />
                      </label>
                    ))}
                  </div>
                </fieldset>
              )}
              <div className="field">
                <Label htmlFor="interaction-flow">触发流程</Label>
                <SelectField id="interaction-flow" value={boundFlow?.id ?? ""} onValueChange={setFlow}>
                  <option value="">尚未绑定</option>
                  {data.flows.map((item) => <option key={item.id} value={item.id}>{item.title}</option>)}
                </SelectField>
                <p className="hint">在流程画布中编辑此入口的条件分支。</p>
              </div>
              <div className="field">
                <Label>生效条件</Label>
                <ConditionEditor value={interaction.when ?? { always: true }} onChange={(when) => updateInteraction({ ...interaction, when })}
                  catalog={data.catalog} flow={boundFlow ?? emptyFlow} />
              </div>
              <Button variant="ghost" onClick={removeInteraction}><Trash2 />删除交互点</Button>
            </>
          )}
          {building && (
            building.has_interior ? (
              <div className="space-y-4">
                <Label className="gap-2">
                  <Checkbox checked={buildingRule.enabled}
                    onCheckedChange={(enabled) => updateBuilding(building.id, { ...buildingRule, enabled })} />
                  允许进入
                </Label>
                <Label className="gap-2">
                  <Checkbox checked={buildingRule.default_unlocked}
                    onCheckedChange={(default_unlocked) => updateBuilding(building.id, { ...buildingRule, default_unlocked })} />
                  默认开放
                </Label>
                <div className="field">
                  <Label htmlFor="building-message">未开放提示</Label>
                  <Input id="building-message" value={buildingRule.blocked_message}
                    onChange={(event) => updateBuilding(building.id, { ...buildingRule, blocked_message: event.target.value })} />
                </div>
                <Label className="gap-2">
                  <Checkbox checked={Boolean(buildingRule.when)} onCheckedChange={(conditional) => {
                    const next = { ...buildingRule };
                    if (conditional) next.when = { always: true };
                    else delete next.when;
                    updateBuilding(building.id, next);
                  }} />
                  满足条件时开放
                </Label>
                {buildingRule.when && (
                  <ConditionEditor value={buildingRule.when} catalog={data.catalog} flow={emptyFlow}
                    onChange={(when) => updateBuilding(building.id, { ...buildingRule, when })} />
                )}
              </div>
            ) : <p className="hint">此建筑没有可用室内。</p>
          )}

        </AuthoringInspector>
      )}
    </AuthoringWorkspace>
  );
}
