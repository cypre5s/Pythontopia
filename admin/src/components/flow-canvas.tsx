import {
  createContext,
  memo,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type DragEvent,
  type KeyboardEvent,
  type MouseEvent,
  type ReactNode,
} from "react";
import {
  Background,
  ConnectionMode,
  Controls,
  Handle,
  MarkerType,
  MiniMap,
  Panel,
  Position,
  ReactFlow,
  ReactFlowProvider,
  useNodesInitialized,
  useReactFlow,
  useUpdateNodeInternals,
  type Edge,
  type Node,
  type NodeChange,
  type NodeProps,
} from "@xyflow/react";
import {
  CircleStop,
  DoorOpen,
  Ellipsis,
  GitBranch,
  GraduationCap,
  KeyRound,
  MessageSquare,
  MousePointer2,
  Shuffle,
  type LucideIcon,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { nodeEdges, nodeTitle, type FlowPort, type Point, type StoryNode } from "@/lib/authoring";
import "@xyflow/react/dist/style.css";
import "./flow-canvas.css";

export const nodeKinds = {
  dialogue: { label: "对白", icon: MessageSquare },
  branch: { label: "条件判断", icon: GitBranch },
  activity: { label: "学习活动", icon: GraduationCap },
  action: { label: "打开页面", icon: DoorOpen },
  unlock_building: { label: "解锁建筑", icon: KeyRound },
  random: { label: "随机", icon: Shuffle },
  end: { label: "结束", icon: CircleStop },
};
const kinds = { ...nodeKinds, entry: { label: "触发入口", icon: MousePointer2 } };
const targetHandle = "in";
const paletteMime = "application/x-pythontopia-flow-block";
const cardWidth = 206;
const cardGap = 20;
const entryId = (id: string) => `entry:${id}`;
const storyId = (id: string) => `node:${id}`;
const decodeId = (id: string) => id.slice(id.indexOf(":") + 1);
type PortSource = { kind: "entry" | "node"; id: string };
function sourceId(id: string): PortSource {
  return { kind: id.startsWith("entry:") ? "entry" : "node", id: decodeId(id) };
}
type Entry = { id: string; title: string; ports: FlowPort[] };
type Appearance = { icon: LucideIcon; tone: StoryNode["type"] };
type GraphNodeData = {
  title: string;
  caption: string;
  kind: keyof typeof kinds;
  appearance?: Appearance;
  ports: FlowPort[];
};
type GraphNode = Node<GraphNodeData, "flowCard">;
type Inspect = (source: PortSource, anchor: Point, trigger: HTMLElement) => void;
const InspectContext = createContext<((id: string, trigger: HTMLElement) => void) | undefined>(undefined);
const emptyEntries: Entry[] = [];
const emptyPositions: Record<string, Point> = {};
const fitOptions = { padding: { top: "68px", right: "30px", bottom: "30px", left: "30px" }, maxZoom: 1 } as const;
const defaultViewport = { x: -10, y: 46, zoom: 0.8 };

const FlowCard = memo(function FlowCard({ id, data, selected, isConnectable }: NodeProps<GraphNode>) {
  const Icon = data.appearance?.icon ?? kinds[data.kind].icon;
  const inspect = useContext(InspectContext);
  const updateNodeInternals = useUpdateNodeInternals();
  const ports = data.ports.map((port) => port.id).join("\0");
  useEffect(() => updateNodeInternals(id), [id, ports, updateNodeInternals]);
  return (
    <div className={`flow-card ${selected ? "is-selected" : ""}`} data-kind={data.appearance?.tone ?? data.kind}>
      {data.kind !== "entry" && (
        <Handle type="target" position={Position.Left} id={targetHandle} isConnectable={isConnectable} aria-label="输入" />
      )}
      <header>
        <Icon size={16} aria-hidden="true" />
        <span>{data.caption}</span>
        {inspect && selected && (
          <Button
            type="button"
            size="icon-xs"
            variant="ghost"
            className="flow-card-inspect nodrag nopan"
            aria-label="编辑属性"
            title="编辑属性（Shift+F10）"
            onClick={(event) => {
              event.stopPropagation();
              inspect(id, event.currentTarget);
            }}
          >
            <Ellipsis />
          </Button>
        )}
      </header>
      <p className="flow-card-title">{data.title}</p>
      {data.ports.map((port) => (
        <div className="flow-card-port" key={port.id} title={port.label}>
          <span>{port.label}</span>
          <Handle
            id={port.id}
            type="source"
            position={Position.Right}
            isConnectable={isConnectable}
            aria-label={`输出：${port.label}`}
          />
        </div>
      ))}
    </div>
  );
});
const nodeTypes = { flowCard: FlowCard };
const defaultEdgeOptions = { markerEnd: { type: MarkerType.ArrowClosed }, interactionWidth: 20 };
const canvasLabels = {
  "controls.ariaLabel": "画布视图",
  "controls.zoomIn.ariaLabel": "放大画布",
  "controls.zoomOut.ariaLabel": "缩小画布",
  "controls.fitView.ariaLabel": "适应画布",
  "minimap.ariaLabel": "流程缩略图",
  "node.a11yDescription.default": "按 Enter 选中节点，按方向键移动。按 Shift+F10 编辑属性。",
  "edge.a11yDescription.default": "按 Enter 选择连线，按 Delete 删除。",
  "handle.ariaLabel": "连线端口",
};

function arrangeFlow(cards: GraphNode[]) {
  const cardIds = new Set(cards.map((card) => card.id));
  const incoming = new Map(cards.map((card) => [card.id, 0]));
  for (const { data } of cards)
    for (const port of data.ports) {
      const target = storyId(port.to);
      if (cardIds.has(target)) incoming.set(target, (incoming.get(target) ?? 0) + 1);
    }
  const byId = new Map(cards.map((card) => [card.id, card]));
  const queue = cards.filter((card) => !incoming.get(card.id));
  const depths = new Map<string, number>();
  for (const card of queue) {
    const depth = depths.get(card.id) ?? (card.data.kind === "entry" ? 0 : 1);
    depths.set(card.id, depth);
    for (const { to } of card.data.ports) {
      const target = storyId(to), next = byId.get(target);
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

type PaletteItem<T extends string> = { kind: T; label: string; icon: LucideIcon; tone?: StoryNode["type"] };
const storyPalette: PaletteItem<string>[] = Object.entries(nodeKinds).map(([kind, details]) => ({ ...details, kind }));

type CanvasProps<T extends string> = {
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
  renderWorkspace?: (slots: { palette: ReactNode; canvas: ReactNode }) => ReactNode;
};

function samePoint(a: Point, b: Point) {
  return a.x === b.x && a.y === b.y;
}

function sameData(a: GraphNodeData, b: GraphNodeData) {
  return a.title === b.title && a.caption === b.caption && a.kind === b.kind &&
    a.appearance?.icon === b.appearance?.icon && a.appearance?.tone === b.appearance?.tone &&
    a.ports.length === b.ports.length && a.ports.every(({ id, to, label }, i) =>
      id === b.ports[i].id && to === b.ports[i].to && label === b.ports[i].label,
    );
}

function Canvas<T extends string>({
  nodes, positions, selected, onSelect, onMove, labels, captions, nodeAppearances, ports, edgeLabel,
  entries = emptyEntries, entryPositions = emptyPositions, selectedEntry, onSelectEntry, onMoveEntry,
  onConnect, canConnect, onAddNode, onAddEntry, entryAvailable, focusTarget, paletteItems, maxNodes = 200, onInspect, onDismissInspector,
  onAutoArrange, renderWorkspace,
}: CanvasProps<T>) {
  const [selectedEdge, setSelectedEdge] = useState<string | null>(null);
  const viewport = useRef<HTMLDivElement>(null);
  const { getNodes, getNode, getZoom, setCenter, setNodes, screenToFlowPosition } = useReactFlow<GraphNode, Edge>();
  const initialized = useNodesInitialized();
  const focused = useRef<PortSource | undefined>(undefined);
  const cards = useMemo<GraphNode[]>(() => [
    ...entries.map((entry): GraphNode => ({
      id: entryId(entry.id),
      type: "flowCard",
      position: { x: 0, y: 0 },
      selected: entry.id === selectedEntry,
      deletable: false,
      ariaLabel: `触发入口：${entry.title}`,
      data: { title: entry.title, caption: "触发入口", kind: "entry", ports: entry.ports },
    })),
    ...nodes.map((node, i): GraphNode => {
      const title = labels?.[node.id] ?? nodeTitle(node);
      return {
        id: storyId(node.id),
        type: "flowCard",
        position: { x: 0, y: 0 },
        selected: node.id === selected,
        deletable: false,
        ariaLabel: `节点 ${i + 1}：${title}`,
        data: {
          title, caption: captions?.[node.id] ?? kinds[node.type].label, kind: node.type,
          appearance: nodeAppearances?.[node.id],
          ports: ports?.[node.id] ?? nodeEdges(node).map((port) => ({ ...port, label: edgeLabel ?? port.label })),
        },
      };
    }),
  ], [entries, nodes, labels, captions, nodeAppearances, ports, edgeLabel, selected, selectedEntry]);
  // Topology suggests an initial layout. Reconnecting a port shouldn't move untouched cards.
  const [initialPositions] = useState(() => arrangeFlow(cards));
  const graph = useMemo(() => {
    const newPositions = cards.some((card) => !initialPositions[card.id]) ? arrangeFlow(cards) : {};
    return cards.map((card) => {
      const { kind, id } = sourceId(card.id);
      return {
        ...card,
        position: (kind === "entry" ? entryPositions[id] : positions[id])
          ?? initialPositions[card.id] ?? newPositions[card.id],
      };
    });
  }, [cards, entryPositions, initialPositions, positions]);
  const [startingNodes] = useState(() => graph);
  useEffect(() => {
    // React Flow owns the measured size and live drag state. Reusing both avoids hiding
    // nodes between pointer frames while ResizeObserver measures a replacement node.
    setNodes((current) => {
      const byId = new Map(current.map((card) => [card.id, card]));
      const next = graph.map((card) => {
        const previous = byId.get(card.id);
        if (!previous) return card;
        const position = previous.dragging ? previous.position : card.position;
        if (samePoint(position, previous.position) && card.selected === previous.selected &&
          card.ariaLabel === previous.ariaLabel && sameData(previous.data, card.data)) return previous;
        return { ...previous, ...card, position };
      });
      return next.length === current.length && next.every((card, i) => card === current[i]) ? current : next;
    });
  }, [graph, setNodes]);
  useEffect(() => {
    if (!initialized || !focusTarget || focused.current === focusTarget) return;
    const id = focusTarget.kind === "entry" ? entryId(focusTarget.id) : storyId(focusTarget.id);
    const node = getNode(id);
    if (!node?.measured?.width || !node.measured.height) return;
    focused.current = focusTarget;
    const target = viewport.current?.querySelector<HTMLElement>(`.react-flow__node[data-id="${CSS.escape(id)}"]`);
    target?.focus({ preventScroll: true });
    viewport.current?.scrollIntoView({ block: "nearest" });
    void setCenter(node.position.x + node.measured.width / 2, node.position.y + node.measured.height / 2, {
      zoom: Math.max(0.85, Math.min(1.25, getZoom())), duration: 180,
    });
  }, [focusTarget, getNode, getZoom, initialized, setCenter]);

  const edges = useMemo(() => {
    const byId = new Map(cards.map((card) => [card.id, card]));
    return cards.flatMap((card) => card.data.ports.flatMap((port): Edge[] => {
      const target = byId.get(storyId(port.to));
      if (!target) return [];
      const id = JSON.stringify([card.id, port.id]);
      return [{
        id,
        source: card.id,
        sourceHandle: port.id,
        target: target.id,
        targetHandle,
        selected: id === selectedEdge,
        deletable: Boolean(onConnect),
        ariaLabel: `${card.data.title}，${port.label}，连接至${target.data.title}`,
      }];
    }));
  }, [cards, onConnect, selectedEdge]);

  const select = useCallback((id: string) => {
    setSelectedEdge(null);
    const source = sourceId(id);
    if (source.kind === "entry") onSelectEntry?.(source.id);
    else onSelect(source.id);
  }, [onSelect, onSelectEntry]);

  const inspect = useCallback((id: string, trigger: HTMLElement, point?: Point) => {
    const box = trigger.getBoundingClientRect();
    select(id);
    onInspect?.(sourceId(id), point ?? { x: box.right, y: box.top }, trigger);
  }, [onInspect, select]);

  function inspectFromKeyboard(event: KeyboardEvent) {
    if (!onInspect || !(event.key === "ContextMenu" || (event.shiftKey && event.key === "F10"))) return;
    if (!(event.target instanceof HTMLElement) || !event.target.matches(".react-flow__node")) return;
    const id = event.target.dataset.id;
    if (!id) return;
    event.preventDefault();
    event.stopPropagation();
    inspect(id, event.target);
  }

  function inspectFromPointer(event: MouseEvent, card: GraphNode) {
    if (!(event.currentTarget instanceof HTMLElement)) return;
    event.preventDefault();
    inspect(card.id, event.currentTarget, { x: event.clientX, y: event.clientY });
  }

  function nodeChanges(changes: NodeChange<GraphNode>[]) {
    const selectedChange = changes.find((change) => change.type === "select" && change.selected);
    if (selectedChange?.type === "select") select(selectedChange.id);
    else if (changes.some((change) => change.type === "select" &&
      (change.id === storyId(selected ?? "") || change.id === entryId(selectedEntry ?? "")))) {
      onSelect(null);
      onDismissInspector?.();
    }
    for (const change of changes) {
      if (change.type !== "position" || !change.position || change.dragging) continue;
      const card = graph.find(({ id }) => id === change.id);
      if (!card || samePoint(card.position, change.position)) continue;
      const source = sourceId(change.id);
      if (source.kind === "entry") onMoveEntry?.(source.id, change.position);
      else onMove(source.id, change.position);
    }
  }
  const items = onAddEntry ? [{ kind: "entry", ...kinds.entry }, ...(paletteItems ?? storyPalette)] : paletteItems ?? storyPalette;
  const canAddNode = Boolean(onAddNode) && nodes.length < maxNodes;
  function canAdd(kind: string) {
    return kind === "entry" && onAddEntry ? Boolean(entryAvailable) : canAddNode;
  }
  function add(kind: string, position: Point) {
    if (kind === "entry" && onAddEntry) onAddEntry(position);
    else onAddNode?.(kind as T, position);
  }
  function addAtCenter(kind: string) {
    const bounds = viewport.current?.getBoundingClientRect();
    if (!bounds) return;
    const center = screenToFlowPosition({
      x: bounds.left + bounds.width / 2,
      y: bounds.top + bounds.height / 2,
    });
    const origin = { x: Math.round(center.x - cardWidth / 2), y: Math.round(center.y - 42) };
    const nearby = getNodes().filter(({ position }) =>
      Math.abs(position.x - origin.x) < cardWidth + cardGap && Math.abs(position.y - origin.y) < 125,
    ).length;
    add(kind, { x: origin.x + nearby * 28, y: origin.y + nearby * 28 });
  }

  function drop(event: DragEvent) {
    if (!event.dataTransfer.types.includes(paletteMime)) return;
    event.preventDefault();
    const kind = event.dataTransfer.getData(paletteMime);
    if (!canAdd(kind) || !items.some((item) => item.kind === kind)) return;
    const point = screenToFlowPosition({ x: event.clientX, y: event.clientY });
    add(kind, { x: Math.round(point.x - cardWidth / 2), y: Math.round(point.y - 18) });
  }

  const palette = onAddNode || onAddEntry ? (
    <section className="flow-palette" aria-label={paletteItems ? "添加目标" : "添加节点"}>
      <p className="flow-palette-heading">{paletteItems ? "添加目标" : "添加节点"}</p>
      <p className="flow-palette-hint">拖入画布或点击添加</p>
      {items.map(({ kind, icon: Icon, label, ...appearance }) => (
        <Button
          key={kind}
          type="button"
          variant="outline"
          size="sm"
          data-kind={"tone" in appearance ? appearance.tone ?? kind : kind}
          className="flow-palette-block"
          draggable={canAdd(kind)}
          disabled={!canAdd(kind)}
          onClick={() => addAtCenter(kind)}
          onDragStart={(event) => {
            event.dataTransfer.effectAllowed = "copy";
            event.dataTransfer.setData(paletteMime, kind);
          }}
        >
          <Icon aria-hidden="true" />
          {label}
        </Button>
      ))}
    </section>
  ) : null;

  const canvas = (
    <div className="flow-canvas-shell" aria-label="流程画布">
      <div
        className="flow-viewport"
        ref={viewport}
        onKeyDownCapture={inspectFromKeyboard}
        onDragOver={(event) => {
          if ((!canAddNode && !entryAvailable) || !event.dataTransfer.types.includes(paletteMime)) return;
          event.preventDefault();
          event.dataTransfer.dropEffect = "copy";
        }}
        onDrop={drop}
      >
        <InspectContext.Provider value={onInspect ? inspect : undefined}>
          <ReactFlow<GraphNode, Edge>
            defaultNodes={startingNodes}
            edges={edges}
            nodeTypes={nodeTypes}
            onNodesChange={nodeChanges}
            onNodeClick={(_, card) => select(card.id)}
            onNodeContextMenu={onInspect ? inspectFromPointer : undefined}
            onNodeDoubleClick={onInspect ? inspectFromPointer : undefined}
            onNodeDragStart={onDismissInspector}
            onPaneClick={() => {
              setSelectedEdge(null);
              onSelect(null);
              onDismissInspector?.();
            }}
            onEdgesChange={(changes) => {
              for (const change of changes) {
                if (change.type === "select") setSelectedEdge((old) => change.selected ? change.id : old === change.id ? null : old);
                if (change.type !== "remove") continue;
                const edge = edges.find((old) => old.id === change.id);
                if (edge?.sourceHandle) onConnect?.(sourceId(edge.source), edge.sourceHandle, "");
              }
            }}
            onConnect={({ source, sourceHandle, target }) => {
              if (sourceHandle) onConnect?.(sourceId(source), sourceHandle, decodeId(target));
            }}
            isValidConnection={({ source, target, sourceHandle }) => Boolean(
              source !== target && sourceHandle && target.startsWith("node:") &&
              cards.find((card) => card.id === source)?.data.ports.some((port) => port.id === sourceHandle) &&
              (!canConnect || canConnect(sourceId(source), sourceHandle, decodeId(target))),
            )}
            nodesConnectable={Boolean(onConnect)}
            edgesFocusable={Boolean(onConnect)}
            edgesReconnectable={false}
            multiSelectionKeyCode={null}
            selectionKeyCode={null}
            deleteKeyCode={["Backspace", "Delete"]}
            connectionMode={ConnectionMode.Strict}
            defaultEdgeOptions={defaultEdgeOptions}
            minZoom={0.08}
            maxZoom={2}
            fitView={nodes.length <= 10}
            fitViewOptions={fitOptions}
            defaultViewport={defaultViewport}
            ariaLabelConfig={canvasLabels}
          >
            <Background gap={20} size={1.3} />
            <Controls showInteractive={false} />
            {nodes.length > 6 && <MiniMap pannable zoomable />}
            {onAutoArrange && (
              <Panel position="top-right" className="flow-arrange-panel">
                <Button variant="outline" size="sm" onClick={onAutoArrange}>自动排列</Button>
              </Panel>
            )}
          </ReactFlow>
        </InspectContext.Provider>
      </div>
      <p className="canvas-help">
        {onConnect
          ? "拖动空白处平移，滚轮缩放。端口拖线，选中连线后按 Delete 移除。右键或 Shift+F10 编辑属性。"
          : "拖动空白处平移，滚轮缩放。右键或 Shift+F10 编辑属性。"}
      </p>
    </div>
  );
  return renderWorkspace ? renderWorkspace({ palette, canvas }) : <>{palette}{canvas}</>;
}

export function FlowCanvas<T extends string = StoryNode["type"]>(props: CanvasProps<T>) {
  return <ReactFlowProvider><Canvas {...props} /></ReactFlowProvider>;
}
