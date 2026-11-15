import type { Authoring, Interaction, Point } from "@/features/authoring/model";
import { type MouseEvent } from "react";
import { eventAnchor, markerAnchor, regionBetween } from "./geometry";
import { MapArtwork } from "./map-artwork";
import type { useMapDrawing } from "./use-map-drawing";
export function MapSurface({
  drawing,
  mapKey,
  map,
  draw,
  grid,
  visible,
  interactionId,
  openObject,
  inspect,
  create,
}: {
  drawing: ReturnType<typeof useMapDrawing>;
  mapKey: string;
  map: Authoring["catalog"]["maps"][number];
  draw: boolean;
  grid: string;
  visible: Interaction[];
  interactionId?: string;
  openObject: (id: string, label: string, anchor: Point) => void;
  inspect: (id: string, anchor: Point) => void;
  create: (target: Interaction["target"], anchor: Point) => void;
}) {
  const {
    draftRegion,
    setDraftRegion,
    start,
    point,
    clearDraw,
    width,
    height,
  } = drawing;
  return (
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
          if (position)
            setDraftRegion(regionBetween(start.current.point, position));
        }}
        onPointerUp={(event) => {
          if (start.current?.pointer !== event.pointerId) return;
          const position = point(event);
          if (position)
            create(
              { region: regionBetween(start.current.point, position) },
              eventAnchor(event),
            );
          else clearDraw();
        }}
        onPointerCancel={clearDraw}
        onLostPointerCapture={clearDraw}
      >
        <defs>
          <pattern id={grid} width="1" height="1" patternUnits="userSpaceOnUse">
            <path
              d="M 1 0 L 0 0 0 1"
              fill="none"
              stroke="#f7ffef"
              strokeWidth=".045"
            />
          </pattern>
        </defs>
        <MapArtwork
          key={mapKey}
          mapKey={mapKey}
          width={width}
          height={height}
        />
        <rect
          width={width}
          height={height}
          fill={`url(#${grid})`}
          opacity={draw ? 0.75 : 0.22}
          pointerEvents="none"
        />
        {map.objects.flatMap((object) =>
          object.points
            .filter((item) => item.map_key === mapKey)
            .map((tile, index) => (
              <g
                key={`${object.id}-${index}`}
                className="map-object"
                role="button"
                tabIndex={draw ? -1 : 0}
                aria-label={`选择地图对象 ${object.label}`}
                onClick={(event) => {
                  if (!draw)
                    openObject(
                      object.id,
                      object.label,
                      markerAnchor(event.currentTarget),
                    );
                }}
                onContextMenu={(event) => {
                  const existing = visible.find(
                    (item) =>
                      "object_id" in item.target &&
                      item.target.object_id === object.id,
                  );
                  if (draw || !existing) return;
                  event.preventDefault();
                  inspect(existing.id, eventAnchor(event));
                }}
                onKeyDown={(event) => {
                  if (draw || !["Enter", " "].includes(event.key)) return;
                  event.preventDefault();
                  openObject(
                    object.id,
                    object.label,
                    markerAnchor(event.currentTarget),
                  );
                }}
              >
                <rect
                  x={tile.tile_x - 0.1}
                  y={tile.tile_y - 0.1}
                  width={1.2}
                  height={1.2}
                  rx={0.2}
                />
                <text
                  x={tile.tile_x + 0.5}
                  y={tile.tile_y - 0.35}
                  textAnchor="middle"
                  fontSize={Math.max(0.48, width / 78)}
                >
                  {object.label}
                </text>
              </g>
            )),
        )}
        {visible.flatMap((item) => {
          const selected = item.id === interactionId;
          const target = item.target;
          const marker = {
            role: "button",
            tabIndex: draw ? -1 : 0,
            className:
              "cursor-pointer focus-visible:outline-none focus-visible:stroke-orange-700",
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
              <rect
                key={item.id}
                {...target.region}
                {...marker}
                pointerEvents={draw ? "none" : undefined}
                fill={selected ? "#52785e60" : "#52785e25"}
                stroke={selected ? "#c76527" : "#52785e"}
                strokeWidth={0.12}
              />
            );
          }
          return (
            map.objects
              .find((object) => object.id === target.object_id)
              ?.points.filter((tile) => tile.map_key === mapKey)
              .map((tile, index) => (
                <circle
                  key={`${item.id}-${index}`}
                  {...marker}
                  cx={tile.tile_x + 0.5}
                  cy={tile.tile_y + 0.5}
                  r={0.87}
                  fill="none"
                  stroke={selected ? "#ffbf70" : "#37685a"}
                  strokeWidth={0.24}
                  pointerEvents={draw ? "none" : "stroke"}
                />
              )) ?? []
          );
        })}
        {draftRegion && (
          <rect
            {...draftRegion}
            fill="#52785e40"
            stroke="#52785e"
            strokeWidth={0.12}
            pointerEvents="none"
          />
        )}
      </svg>
      <p className="hint">
        {draw
          ? "在地图上拖出区域，松开后设置交互。"
          : "点击对象创建交互，或打开已有交互的属性。"}
        底图、碰撞与建筑入口由系统维护。
      </p>
    </div>
  );
}
