import { Empty } from "@/components/common";
import { SelectField } from "@/components/select-field";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import type { Authoring, Interaction, Point } from "@/features/authoring/model";
import {
  AuthoringInspector,
  AuthoringWorkspace,
} from "@/features/authoring/workspace";
import { newId } from "@/lib/id";
import {
  DoorOpen,
  MapPin,
  MousePointer2,
  SquareDashedMousePointer,
  Users,
} from "lucide-react";
import { useEffect, useId, useRef, useState } from "react";
import { BuildingProperties } from "./building-properties";
import { CharacterPlacement } from "./character-placement";
import { eventAnchor } from "./geometry";
import { InteractionProperties } from "./interaction-properties";
import { MapSurface } from "./map-surface";
import { useMapDrawing } from "./use-map-drawing";

const defaultRule: BuildingRule = {
  enabled: false,
  default_unlocked: false,
  blocked_message: "此课程尚未开放该建筑。",
};
type BuildingRule = Authoring["configuration"]["buildings"][string];
type View = "map" | "buildings" | "npcs";
export type MapFocusTarget = {
  kind: "interaction" | "building" | "npc";
  id: string;
};
type Inspector = MapFocusTarget & { anchor: HTMLElement | Point };
const noInteractions: Interaction[] = [];

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
  const [pendingFocus, setPendingFocus] = useState<MapFocusTarget | null>(null);
  const handledFocus = useRef<MapFocusTarget | null>(null);
  const grid = useId();
  const { world, buildings } = data.configuration;
  const map = data.catalog.maps.find((item) => item.key === mapKey);
  const interactions = world.interactions ?? noInteractions;
  const visible = interactions.filter((item) => item.map_key === mapKey);
  const interaction =
    inspector?.kind === "interaction"
      ? visible.find((item) => item.id === inspector.id)
      : undefined;
  const building =
    inspector?.kind === "building"
      ? data.catalog.buildings.find((item) => item.id === inspector.id)
      : undefined;
  const npc =
    inspector?.kind === "npc"
      ? data.catalog.npcs.find((item) => item.id === inspector.id)
      : undefined;
  const buildingRule = building
    ? (buildings[building.id] ?? defaultRule)
    : defaultRule;
  const entryKey = interaction ? `interaction:${interaction.id}` : "";
  const boundFlow = data.flows.find((item) => entryKey in item.content.entries);
  const width = map?.width ?? 1;
  const height = map?.height ?? 1;

  const drawing = useMapDrawing(width, height);
  const { start, setDraftRegion, clearDraw } = drawing;
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
        const items =
          focusTarget.kind === "building"
            ? data.catalog.buildings
            : data.catalog.npcs;
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
      const anchor = document.getElementById(
        `${grid}-${pendingFocus.kind}-${pendingFocus.id}`,
      );
      if (anchor) {
        anchor.scrollIntoView?.({ block: "nearest", inline: "nearest" });
        setInspector({ ...pendingFocus, anchor });
      }
      setPendingFocus(null);
    });
    return () => cancelAnimationFrame(frame);
  }, [grid, pendingFocus]);

  function updateWorld(next: Authoring["configuration"]["world"]) {
    onChange({
      ...data,
      configuration: { ...data.configuration, world: next },
    });
  }
  function updateInteraction(next: Interaction) {
    updateWorld({
      ...world,
      interactions: interactions.map((item) =>
        item.id === next.id ? next : item,
      ),
    });
  }
  function updateBuilding(id: string, next: BuildingRule) {
    onChange({
      ...data,
      configuration: {
        ...data.configuration,
        buildings: { ...buildings, [id]: next },
      },
    });
  }
  function close() {
    setInspector(null);
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
  function create(
    target: Interaction["target"],
    anchor: Point,
    label = "新的交互点",
  ) {
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
    const existing = visible.find(
      (item) => "object_id" in item.target && item.target.object_id === id,
    );
    if (existing) inspect(existing.id, anchor);
    else create({ object_id: id }, anchor, label);
  }
  function setFlow(id: string) {
    if (!interaction || boundFlow?.id === id) return;
    onChange({
      ...data,
      flows: data.flows.map((item) => {
        const entries = { ...item.content.entries };
        delete entries[entryKey];
        if (item.id === id) {
          entries[entryKey] = [
            { when: { always: true }, node: item.content.nodes[0]?.id ?? "" },
          ];
        }
        return { ...item, content: { ...item.content, entries } };
      }),
    });
  }
  function removeInteraction() {
    if (!interaction || !window.confirm("删除此交互点及其流程入口绑定？"))
      return;
    onChange({
      ...data,
      flows: data.flows.map((item) => {
        const entries = { ...item.content.entries };
        delete entries[entryKey];
        return { ...item, content: { ...item.content, entries } };
      }),
      configuration: {
        ...data.configuration,
        world: {
          ...world,
          interactions: interactions.filter(
            (item) => item.id !== interaction.id,
          ),
        },
      },
    });
    close();
  }

  return (
    <AuthoringWorkspace
      tools={
        <>
          <p className="px-2 pb-1 text-xs font-medium text-muted-foreground">
            地图工具
          </p>
          <Button
            className="justify-start"
            variant={view === "map" && !draw ? "secondary" : "ghost"}
            aria-pressed={view === "map" && !draw}
            onClick={() => chooseTool("map")}
          >
            <MousePointer2 />
            点选地图对象
          </Button>
          <Button
            className="justify-start"
            variant={view === "map" && draw ? "secondary" : "ghost"}
            aria-pressed={view === "map" && draw}
            disabled={!map}
            onClick={() => chooseTool("map", !draw)}
          >
            <SquareDashedMousePointer />
            绘制交互区域
          </Button>
          <Separator className="my-2" />
          <p className="px-2 pb-1 text-xs font-medium text-muted-foreground">
            开放规则
          </p>
          <Button
            className="justify-start"
            variant={view === "buildings" ? "secondary" : "ghost"}
            aria-pressed={view === "buildings"}
            onClick={() => chooseTool("buildings")}
          >
            <DoorOpen />
            建筑开放规则
          </Button>
          <Button
            className="justify-start"
            variant={view === "npcs" ? "secondary" : "ghost"}
            aria-pressed={view === "npcs"}
            onClick={() => chooseTool("npcs")}
          >
            <Users />
            角色摆放与出现
          </Button>
          {view === "npcs" && (
            <>
              <Separator className="my-2" />
              <p className="px-2 text-xs text-muted-foreground">教学角色</p>
              {data.npcs.map((item) => (
                <Button
                  key={item.id}
                  variant={
                    (characterId ?? data.npcs[0]?.id) === item.id
                      ? "secondary"
                      : "ghost"
                  }
                  className="min-w-0 justify-start"
                  title={`${item.name} · ${item.placement ? "已放置" : "未放置"}`}
                  onClick={() => setCharacterId(item.id)}
                >
                  <Users />
                  <span className="truncate">{item.name}</span>
                  <span className="ml-auto text-xs text-muted-foreground">
                    {item.placement ? "已放置" : "未放置"}
                  </span>
                </Button>
              ))}
            </>
          )}
          {view === "map" && visible.length > 0 && (
            <>
              <Separator className="my-2" />
              <p className="px-2 pb-1 text-xs font-medium text-muted-foreground">
                本地图的交互点
              </p>
              {visible.map((item) => (
                <Button
                  key={item.id}
                  id={`${grid}-interaction-${item.id}`}
                  variant={interaction?.id === item.id ? "secondary" : "ghost"}
                  className="min-w-0 justify-start"
                  title={item.label}
                  onClick={(event) => inspect(item.id, event.currentTarget)}
                  onContextMenu={(event) => {
                    event.preventDefault();
                    inspect(item.id, eventAnchor(event));
                  }}
                >
                  <MapPin />
                  <span className="truncate">{item.label}</span>
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
              onValueChange={(key) => {
                setMapKey(key);
                chooseTool("map");
              }}
            >
              {data.catalog.maps.map((item) => (
                <option key={item.key} value={item.key}>
                  {item.label}
                </option>
              ))}
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
      {view === "npcs" && (
        <CharacterPlacement
          onSelect={setCharacterId}
          data={data}
          onChange={onChange}
          focusId={
            characterId ??
            (focusTarget?.kind === "npc" ? focusTarget.id : undefined)
          }
        />
      )}
      {view === "map" ? (
        map ? (
          <MapSurface
            drawing={drawing}
            mapKey={mapKey}
            map={map}
            draw={draw}
            grid={grid}
            visible={visible}
            interactionId={interaction?.id}
            openObject={openObject}
            inspect={inspect}
            create={create}
          />
        ) : (
          <Empty title="暂无可编辑地图" />
        )
      ) : (
        <div className="grid content-start gap-3 p-4 sm:grid-cols-2 lg:grid-cols-3">
          {view === "buildings"
            ? data.catalog.buildings.map((item) => (
                <Button
                  key={item.id}
                  id={`${grid}-building-${item.id}`}
                  variant="outline"
                  className="h-auto min-w-0 justify-start gap-3 p-3 text-left"
                  onClick={(event) =>
                    setInspector({
                      kind: "building",
                      id: item.id,
                      anchor: event.currentTarget,
                    })
                  }
                  onContextMenu={(event) => {
                    event.preventDefault();
                    setInspector({
                      kind: "building",
                      id: item.id,
                      anchor: eventAnchor(event),
                    });
                  }}
                >
                  <DoorOpen className="text-muted-foreground" />
                  <span className="grid min-w-0 gap-1">
                    <span className="truncate">{item.label}</span>
                    <Badge variant="secondary">
                      {item.has_interior
                        ? buildings[item.id]?.enabled
                          ? "允许进入"
                          : "未开放"
                        : "无可用室内"}
                    </Badge>
                  </span>
                </Button>
              ))
            : null}
          {view === "buildings" && !data.catalog.buildings.length && (
            <Empty title="暂无建筑" />
          )}
        </div>
      )}
      {inspector && (
        <AuthoringInspector
          anchor={inspector.anchor}
          title={
            interaction
              ? "交互入口"
              : (building?.label ?? npc?.name ?? "地图属性")
          }
          onClose={close}
        >
          {interaction && (
            <InteractionProperties
              interaction={interaction}
              data={data}
              boundFlow={boundFlow}
              updateInteraction={updateInteraction}
              setFlow={setFlow}
              removeInteraction={removeInteraction}
            />
          )}
          {building && (
            <BuildingProperties
              building={building}
              data={data}
              buildingRule={buildingRule}
              updateBuilding={updateBuilding}
            />
          )}
        </AuthoringInspector>
      )}
    </AuthoringWorkspace>
  );
}
