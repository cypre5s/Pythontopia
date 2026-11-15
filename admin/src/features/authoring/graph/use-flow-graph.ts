import { nodeEdges, nodeTitle, type Point } from "@/features/authoring/model";
import { useReactFlow, type Edge } from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { useEffect, useMemo, useState } from "react";
import {
  arrangeFlow,
  CanvasProps,
  Entry,
  entryId,
  GraphNode,
  kinds,
  sameData,
  samePoint,
  sourceId,
  storyId,
  targetHandle,
} from "./canvas-model";
import "./flow-canvas.css";
export function useFlowGraph(
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
  }: Pick<
    CanvasProps<string>,
    | "nodes"
    | "positions"
    | "selected"
    | "labels"
    | "captions"
    | "nodeAppearances"
    | "ports"
    | "edgeLabel"
    | "entries"
    | "entryPositions"
    | "selectedEntry"
    | "onConnect"
  > & { entries: Entry[]; entryPositions: Record<string, Point> },
  selectedEdge: string | null,
) {
  const { setNodes } = useReactFlow<GraphNode, Edge>();
  const cards = useMemo<GraphNode[]>(
    () => [
      ...entries.map(
        (entry): GraphNode => ({
          id: entryId(entry.id),
          type: "flowCard",
          position: { x: 0, y: 0 },
          selected: entry.id === selectedEntry,
          deletable: false,
          ariaLabel: `触发入口：${entry.title}`,
          data: {
            title: entry.title,
            caption: "触发入口",
            kind: "entry",
            ports: entry.ports,
          },
        }),
      ),
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
            title,
            caption: captions?.[node.id] ?? kinds[node.type].label,
            kind: node.type,
            appearance: nodeAppearances?.[node.id],
            ports:
              ports?.[node.id] ??
              nodeEdges(node).map((port) => ({
                ...port,
                label: edgeLabel ?? port.label,
              })),
          },
        };
      }),
    ],
    [
      entries,
      nodes,
      labels,
      captions,
      nodeAppearances,
      ports,
      edgeLabel,
      selected,
      selectedEntry,
    ],
  );
  const [initialPositions] = useState(() => arrangeFlow(cards));
  const graph = useMemo(() => {
    const newPositions = cards.some((card) => !initialPositions[card.id])
      ? arrangeFlow(cards)
      : {};
    return cards.map((card) => {
      const { kind, id } = sourceId(card.id);
      return {
        ...card,
        position:
          (kind === "entry" ? entryPositions[id] : positions[id]) ??
          initialPositions[card.id] ??
          newPositions[card.id],
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
        if (
          samePoint(position, previous.position) &&
          card.selected === previous.selected &&
          card.ariaLabel === previous.ariaLabel &&
          sameData(previous.data, card.data)
        )
          return previous;
        return { ...previous, ...card, position };
      });
      return next.length === current.length &&
        next.every((card, i) => card === current[i])
        ? current
        : next;
    });
  }, [graph, setNodes]);
  const edges = useMemo(() => {
    const byId = new Map(cards.map((card) => [card.id, card]));
    return cards.flatMap((card) =>
      card.data.ports.flatMap((port): Edge[] => {
        const target = byId.get(storyId(port.to));
        if (!target) return [];
        const id = JSON.stringify([card.id, port.id]);
        return [
          {
            id,
            source: card.id,
            sourceHandle: port.id,
            target: target.id,
            targetHandle,
            selected: id === selectedEdge,
            deletable: Boolean(onConnect),
            ariaLabel: `${card.data.title}，${port.label}，连接至${target.data.title}`,
          },
        ];
      }),
    );
  }, [cards, onConnect, selectedEdge]);
  return { cards, graph, startingNodes, edges };
}
