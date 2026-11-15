import {
  type FlowPort,
  type Point,
  type StoryNode,
} from "@/features/authoring/model";
import { type Node } from "@xyflow/react";

import {
  CircleStop,
  DoorOpen,
  GitBranch,
  GraduationCap,
  KeyRound,
  MessageSquare,
  MousePointer2,
  Shuffle,
  type LucideIcon,
} from "lucide-react";
import { type ReactNode } from "react";

export const nodeKinds = {
  dialogue: { label: "对白", icon: MessageSquare },
  branch: { label: "条件判断", icon: GitBranch },
  activity: { label: "学习活动", icon: GraduationCap },
  action: { label: "打开页面", icon: DoorOpen },
  unlock_building: { label: "解锁建筑", icon: KeyRound },
  random: { label: "随机", icon: Shuffle },
  end: { label: "结束", icon: CircleStop },
};

export const kinds = {
  ...nodeKinds,
  entry: { label: "触发入口", icon: MousePointer2 },
};

export const targetHandle = "in";

export const paletteMime = "application/x-pythontopia-flow-block";

export const cardWidth = 206;

export const cardGap = 20;

export const entryId = (id: string) => `entry:${id}`;

export const storyId = (id: string) => `node:${id}`;

export const decodeId = (id: string) => id.slice(id.indexOf(":") + 1);

export type PortSource = { kind: "entry" | "node"; id: string };

export function sourceId(id: string): PortSource {
  return { kind: id.startsWith("entry:") ? "entry" : "node", id: decodeId(id) };
}

export type Entry = { id: string; title: string; ports: FlowPort[] };

export type Appearance = { icon: LucideIcon; tone: StoryNode["type"] };

export type GraphNodeData = {
  title: string;
  caption: string;
  kind: keyof typeof kinds;
  appearance?: Appearance;
  ports: FlowPort[];
};

export type GraphNode = Node<GraphNodeData, "flowCard">;

export type Inspect = (
  source: PortSource,
  anchor: Point,
  trigger: HTMLElement,
) => void;

export function arrangeFlow(cards: GraphNode[]) {
  const cardIds = new Set(cards.map((card) => card.id));
  const incoming = new Map(cards.map((card) => [card.id, 0]));
  for (const { data } of cards)
    for (const port of data.ports) {
      const target = storyId(port.to);
      if (cardIds.has(target))
        incoming.set(target, (incoming.get(target) ?? 0) + 1);
    }
  const byId = new Map(cards.map((card) => [card.id, card]));
  const queue = cards.filter((card) => !incoming.get(card.id));
  const depths = new Map<string, number>();
  for (const card of queue) {
    const depth = depths.get(card.id) ?? (card.data.kind === "entry" ? 0 : 1);
    depths.set(card.id, depth);
    for (const { to } of card.data.ports) {
      const target = storyId(to),
        next = byId.get(target);
      if (!next) continue;
      const remaining = (incoming.get(target) ?? 1) - 1;
      incoming.set(target, remaining);
      depths.set(target, Math.max(depth + 1, depths.get(target) ?? 0));
      if (!remaining) queue.push(next);
    }
  }
  const lane = new Map<number, number>();
  const result: Record<string, Point> = {};
  for (const card of cards) {
    const column = depths.get(card.id) ?? 1;
    const y = lane.get(column) ?? 40;
    result[card.id] = { x: 40 + column * 266, y };
    lane.set(column, y + 140 + card.data.ports.length * 24);
  }
  return result;
}

export type PaletteItem<T extends string> = {
  kind: T;
  label: string;
  icon: LucideIcon;
  tone?: StoryNode["type"];
};

export const storyPalette: PaletteItem<string>[] = Object.entries(
  nodeKinds,
).map(([kind, details]) => ({ ...details, kind }));

export type CanvasProps<T extends string> = {
  nodes: StoryNode[];
  positions: Record<string, Point>;
  selected: string | null;
  onSelect: (id: string | null) => void;
  onMove: (id: string, point: Point) => void;
  labels?: Record<string, string>;
  captions?: Record<string, string>;
  nodeAppearances?: Record<string, Appearance>;
  ports?: Record<string, FlowPort[]>;
  edgeLabel?: string;
  entries?: Entry[];
  entryPositions?: Record<string, Point>;
  selectedEntry?: string | null;
  onSelectEntry?: (id: string) => void;
  onMoveEntry?: (id: string, point: Point) => void;
  onConnect?: (source: PortSource, port: string, to: string) => void;
  canConnect?: (source: PortSource, port: string, to: string) => boolean;
  onAddNode?: (kind: T, position: Point) => void;
  onAddEntry?: (position: Point) => void;
  entryAvailable?: boolean;
  focusTarget?: PortSource;
  paletteItems?: ReadonlyArray<PaletteItem<T>>;
  maxNodes?: number;
  onInspect?: Inspect;
  onDismissInspector?: () => void;
  onAutoArrange?: () => void;
  renderWorkspace?: (slots: {
    palette: ReactNode;
    canvas: ReactNode;
  }) => ReactNode;
};

export function samePoint(a: Point, b: Point) {
  return a.x === b.x && a.y === b.y;
}

export function sameData(a: GraphNodeData, b: GraphNodeData) {
  return (
    a.title === b.title &&
    a.caption === b.caption &&
    a.kind === b.kind &&
    a.appearance?.icon === b.appearance?.icon &&
    a.appearance?.tone === b.appearance?.tone &&
    a.ports.length === b.ports.length &&
    a.ports.every(
      ({ id, to, label }, i) =>
        id === b.ports[i].id &&
        to === b.ports[i].to &&
        label === b.ports[i].label,
    )
  );
}
