import { Button } from "@/components/ui/button";
import { type Point, type StoryNode } from "@/features/authoring/model";
import {
  Background,
  ConnectionMode,
  Controls,
  MarkerType,
  MiniMap,
  Panel,
  ReactFlow,
  ReactFlowProvider,
  useNodesInitialized,
  useReactFlow,
  type Edge,
  type NodeChange,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type DragEvent,
  type KeyboardEvent,
  type MouseEvent,
} from "react";
import {
  CanvasProps,
  cardGap,
  cardWidth,
  decodeId,
  Entry,
  entryId,
  GraphNode,
  kinds,
  paletteMime,
  PortSource,
  samePoint,
  sourceId,
  storyId,
  storyPalette,
} from "./canvas-model";
import "./flow-canvas.css";
import { InspectContext, nodeTypes } from "./flow-card";
import { useFlowGraph } from "./use-flow-graph";
const emptyEntries: Entry[] = [];
const emptyPositions: Record<string, Point> = {};
const fitOptions = {
  padding: { top: "68px", right: "30px", bottom: "30px", left: "30px" },
  maxZoom: 1,
} as const;
const defaultViewport = { x: -10, y: 46, zoom: 0.8 };
const defaultEdgeOptions = {
  markerEnd: { type: MarkerType.ArrowClosed },
  interactionWidth: 20,
};
const canvasLabels = {
  "controls.ariaLabel": "画布视图",
  "controls.zoomIn.ariaLabel": "放大画布",
  "controls.zoomOut.ariaLabel": "缩小画布",
  "controls.fitView.ariaLabel": "适应画布",
  "minimap.ariaLabel": "流程缩略图",
  "node.a11yDescription.default":
    "按 Enter 选中节点，按方向键移动。按 Shift+F10 编辑属性。",
  "edge.a11yDescription.default": "按 Enter 选择连线，按 Delete 删除。",
  "handle.ariaLabel": "连线端口",
};

function Canvas<T extends string>({
  nodes,
  positions,
  selected,
  onSelect,
  onMove,
  labels,
  captions,
  nodeAppearances,
  ports,
  edgeLabel,
  entries = emptyEntries,
  entryPositions = emptyPositions,
  selectedEntry,
  onSelectEntry,
  onMoveEntry,
  onConnect,
  canConnect,
  onAddNode,
  onAddEntry,
  entryAvailable,
  focusTarget,
  paletteItems,
  maxNodes = 200,
  onInspect,
  onDismissInspector,
  onAutoArrange,
  renderWorkspace,
}: CanvasProps<T>) {
  const [selectedEdge, setSelectedEdge] = useState<string | null>(null);
  const viewport = useRef<HTMLDivElement>(null);
  const { getNodes, getNode, getZoom, setCenter, screenToFlowPosition } =
    useReactFlow<GraphNode, Edge>();
  const { cards, graph, startingNodes, edges } = useFlowGraph(
    {
      nodes,
      positions,
      selected,
      labels,
      captions,
      nodeAppearances,
      ports,
      edgeLabel,
      entries,
      entryPositions,
      selectedEntry,
      onConnect,
    },
    selectedEdge,
  );
  const initialized = useNodesInitialized();
  const focused = useRef<PortSource | undefined>(undefined);
  useEffect(() => {
    if (!initialized || !focusTarget || focused.current === focusTarget) return;
    const id =
      focusTarget.kind === "entry"
        ? entryId(focusTarget.id)
        : storyId(focusTarget.id);
    const node = getNode(id);
    if (!node?.measured?.width || !node.measured.height) return;
    focused.current = focusTarget;
    const target = viewport.current?.querySelector<HTMLElement>(
      `.react-flow__node[data-id="${CSS.escape(id)}"]`,
    );
    target?.focus({ preventScroll: true });
    viewport.current?.scrollIntoView({ block: "nearest" });
    void setCenter(
      node.position.x + node.measured.width / 2,
      node.position.y + node.measured.height / 2,
      {
        zoom: Math.max(0.85, Math.min(1.25, getZoom())),
        duration: 180,
      },
    );
  }, [focusTarget, getNode, getZoom, initialized, setCenter]);

  const select = useCallback(
    (id: string) => {
      setSelectedEdge(null);
      const source = sourceId(id);
      if (source.kind === "entry") onSelectEntry?.(source.id);
      else onSelect(source.id);
    },
    [onSelect, onSelectEntry],
  );

  const inspect = useCallback(
    (id: string, trigger: HTMLElement, point?: Point) => {
      const box = trigger.getBoundingClientRect();
      select(id);
      onInspect?.(sourceId(id), point ?? { x: box.right, y: box.top }, trigger);
    },
    [onInspect, select],
  );

  function inspectFromKeyboard(event: KeyboardEvent) {
    if (
      !onInspect ||
      !(event.key === "ContextMenu" || (event.shiftKey && event.key === "F10"))
    )
      return;
    if (
      !(event.target instanceof HTMLElement) ||
      !event.target.matches(".react-flow__node")
    )
      return;
    const id = event.target.dataset.id;
    if (!id) return;
    event.preventDefault();
    event.stopPropagation();
    inspect(id, event.target);
  }

  function inspectFromPointer(event: MouseEvent, card: GraphNode) {
    if (!(event.currentTarget instanceof HTMLElement)) return;
    event.preventDefault();
    inspect(card.id, event.currentTarget, {
      x: event.clientX,
      y: event.clientY,
    });
  }

  function nodeChanges(changes: NodeChange<GraphNode>[]) {
    const selectedChange = changes.find(
      (change) => change.type === "select" && change.selected,
    );
    if (selectedChange?.type === "select") select(selectedChange.id);
    else if (
      changes.some(
        (change) =>
          change.type === "select" &&
          (change.id === storyId(selected ?? "") ||
            change.id === entryId(selectedEntry ?? "")),
      )
    ) {
      onSelect(null);
      onDismissInspector?.();
    }
    for (const change of changes) {
      if (change.type !== "position" || !change.position || change.dragging)
        continue;
      const card = graph.find(({ id }) => id === change.id);
      if (!card || samePoint(card.position, change.position)) continue;
      const source = sourceId(change.id);
      if (source.kind === "entry") onMoveEntry?.(source.id, change.position);
      else onMove(source.id, change.position);
    }
  }
  const items = onAddEntry
    ? [{ kind: "entry", ...kinds.entry }, ...(paletteItems ?? storyPalette)]
    : (paletteItems ?? storyPalette);
  const canAddNode = Boolean(onAddNode) && nodes.length < maxNodes;
  function canAdd(kind: string) {
    return kind === "entry" && onAddEntry
      ? Boolean(entryAvailable)
      : canAddNode;
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
    const origin = {
      x: Math.round(center.x - cardWidth / 2),
      y: Math.round(center.y - 42),
    };
    const nearby = getNodes().filter(
      ({ position }) =>
        Math.abs(position.x - origin.x) < cardWidth + cardGap &&
        Math.abs(position.y - origin.y) < 125,
    ).length;
    add(kind, { x: origin.x + nearby * 28, y: origin.y + nearby * 28 });
  }

  function drop(event: DragEvent) {
    if (!event.dataTransfer.types.includes(paletteMime)) return;
    event.preventDefault();
    const kind = event.dataTransfer.getData(paletteMime);
    if (!canAdd(kind) || !items.some((item) => item.kind === kind)) return;
    const point = screenToFlowPosition({ x: event.clientX, y: event.clientY });
    add(kind, {
      x: Math.round(point.x - cardWidth / 2),
      y: Math.round(point.y - 18),
    });
  }

  const palette =
    onAddNode || onAddEntry ? (
      <section
        className="flow-palette"
        aria-label={paletteItems ? "添加目标" : "添加节点"}
      >
        <p className="flow-palette-heading">
          {paletteItems ? "添加目标" : "添加节点"}
        </p>
        <p className="flow-palette-hint">拖入画布或点击添加</p>
        {items.map(({ kind, icon: Icon, label, ...appearance }) => (
          <Button
            key={kind}
            type="button"
            variant="outline"
            size="sm"
            data-kind={"tone" in appearance ? (appearance.tone ?? kind) : kind}
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
          if (
            (!canAddNode && !entryAvailable) ||
            !event.dataTransfer.types.includes(paletteMime)
          )
            return;
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
                if (change.type === "select")
                  setSelectedEdge((old) =>
                    change.selected
                      ? change.id
                      : old === change.id
                        ? null
                        : old,
                  );
                if (change.type !== "remove") continue;
                const edge = edges.find((old) => old.id === change.id);
                if (edge?.sourceHandle)
                  onConnect?.(sourceId(edge.source), edge.sourceHandle, "");
              }
            }}
            onConnect={({ source, sourceHandle, target }) => {
              if (sourceHandle)
                onConnect?.(sourceId(source), sourceHandle, decodeId(target));
            }}
            isValidConnection={({ source, target, sourceHandle }) =>
              Boolean(
                source !== target &&
                  sourceHandle &&
                  target.startsWith("node:") &&
                  cards
                    .find((card) => card.id === source)
                    ?.data.ports.some((port) => port.id === sourceHandle) &&
                  (!canConnect ||
                    canConnect(
                      sourceId(source),
                      sourceHandle,
                      decodeId(target),
                    )),
              )
            }
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
                <Button variant="outline" size="sm" onClick={onAutoArrange}>
                  自动排列
                </Button>
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
  return renderWorkspace ? (
    renderWorkspace({ palette, canvas })
  ) : (
    <>
      {palette}
      {canvas}
    </>
  );
}

export function FlowCanvas<T extends string = StoryNode["type"]>(
  props: CanvasProps<T>,
) {
  return (
    <ReactFlowProvider>
      <Canvas {...props} />
    </ReactFlowProvider>
  );
}
