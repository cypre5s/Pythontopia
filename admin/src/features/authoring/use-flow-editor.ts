import {
  setNodeTarget,
  setRouteTarget,
  type Authoring as AuthoringData,
  type Flow,
  type Point,
  type StoryNode,
} from "@/features/authoring/model";
import { newId } from "@/lib/id";
import { useState } from "react";
import type { DiagnosticTarget } from "./contracts";
export function useFlowEditor(
  data: AuthoringData,
  change: (next: AuthoringData) => void,
) {
  const [tab, setTab] = useState("flows"),
    [selectedFlow, setSelectedFlow] = useState<string | null>(
      data.flows[0]?.id ?? null,
    ),
    [selectedNode, setSelectedNode] = useState<string | null>(null),
    [selectedEntry, setSelectedEntry] = useState<string | null>(null),
    [inspector, setInspector] = useState<{
      point: Point;
      trigger: HTMLElement;
    } | null>(null),
    [pendingEntry, setPendingEntry] = useState<Point | null>(null),
    [graphFocus, setGraphFocus] = useState<{
      kind: "node" | "entry";
      id: string;
    }>(),
    [mapFocus, setMapFocus] = useState<{
      kind: "interaction" | "building" | "npc";
      id: string;
    }>(),
    [editorJump, setEditorJump] = useState<{
      sequence: number;
      target: DiagnosticTarget;
    }>(),
    [settingsOpen, setSettingsOpen] = useState(false),
    [layoutVersion, setLayoutVersion] = useState(0);
  const flow = data.flows.find((f) => f.id === selectedFlow),
    node = flow?.content.nodes.find((n) => n.id === selectedNode),
    layouts = (data.configuration.editor.flow_layouts ?? {}) as Record<
      string,
      Record<string, Point>
    >,
    entryLayouts = (data.configuration.editor.flow_entry_layouts ??
      {}) as typeof layouts,
    positions = flow ? (layouts[flow.id] ?? {}) : {},
    entryPositions = flow ? (entryLayouts[flow.id] ?? {}) : {},
    entry = selectedEntry && flow?.content.entries[selectedEntry];

  function reset(savedData: AuthoringData) {
    if (!savedData.flows.some((item) => item.id === selectedFlow)) {
      setSelectedFlow(savedData.flows[0]?.id ?? null);
    }
    selectNode(null);
    setInspector(null);
    setPendingEntry(null);
    setGraphFocus(undefined);
    setMapFocus(undefined);
    setEditorJump(undefined);
    setSettingsOpen(false);
  }
  function replaceFlow(next: Flow, nextPositions = positions) {
    change({
      ...data,
      flows: data.flows.map((f) => (f.id === next.id ? next : f)),
      configuration:
        nextPositions === positions
          ? data.configuration
          : {
              ...data.configuration,
              editor: {
                ...data.configuration.editor,
                flow_layouts: { ...layouts, [next.id]: nextPositions },
              },
            },
    });
  }
  function selectNode(id: string | null) {
    setSelectedNode(id);
    setSelectedEntry(null);
  }
  function selectEntry(id: string) {
    setSelectedEntry(id);
    setSelectedNode(null);
  }
  function locate(target: DiagnosticTarget) {
    setInspector(null);
    setSettingsOpen(false);
    if (
      target.kind === "flow" ||
      target.kind === "entry" ||
      target.kind === "node"
    ) {
      const selected = data.flows.find(
        (item) => item.id === (target.flow_id ?? target.id),
      );
      if (!selected) return;
      setTab("flows");
      setSelectedFlow(selected.id);
      setGraphFocus(undefined);
      if (
        target.kind === "node" &&
        target.id &&
        selected.content.nodes.some((item) => item.id === target.id)
      ) {
        selectNode(target.id);
        setGraphFocus({ kind: "node", id: target.id });
      } else if (
        target.kind === "entry" &&
        target.id &&
        Object.hasOwn(selected.content.entries, target.id)
      ) {
        selectEntry(target.id);
        setGraphFocus({ kind: "entry", id: target.id });
      } else {
        selectNode(null);
        setSettingsOpen(true);
      }
    } else if (
      target.kind === "interaction" ||
      target.kind === "building" ||
      target.kind === "npc"
    ) {
      if (!target.id) return;
      setTab("map");
      setMapFocus({ kind: target.kind, id: target.id });
    } else {
      setTab(target.kind === "assistant" ? "assistant" : "quests");
      setEditorJump((previous) => ({
        sequence: (previous?.sequence ?? 0) + 1,
        target,
      }));
    }
  }
  function connect(
    source: { kind: "entry" | "node"; id: string },
    port: string,
    target: string,
  ) {
    if (!flow) return;
    if (source.kind === "entry") {
      const routes = flow.content.entries[source.id];
      if (!routes) return;
      replaceFlow({
        ...flow,
        content: {
          ...flow.content,
          entries: {
            ...flow.content.entries,
            [source.id]: setRouteTarget(routes, port, target),
          },
        },
      });
    } else {
      const from = flow.content.nodes.find((n) => n.id === source.id);
      if (from) replaceNode(setNodeTarget(from, port, target));
    }
  }
  function replaceNode(next: StoryNode) {
    if (flow)
      replaceFlow({
        ...flow,
        content: {
          ...flow.content,
          nodes: flow.content.nodes.map((n) => (n.id === next.id ? next : n)),
        },
      });
  }
  function addFlow() {
    const end: StoryNode = { id: newId("node"), type: "end" };
    const f: Flow = {
      id: newId("flow"),
      title: "新的公共流程",
      chapter_id: null,
      quest_id: null,
      content: {
        schema_version: 1,
        mode: "public",
        priority: 0,
        bindings: {},
        entries: {},
        nodes: [end],
      },
    };
    change({ ...data, flows: [...data.flows, f] });
    setSelectedFlow(f.id);
    selectNode(null);
    setInspector(null);
    setGraphFocus(undefined);
    setSettingsOpen(true);
  }
  function addEntry(id: string, position?: Point) {
    if (
      !flow ||
      Object.hasOwn(flow.content.entries, id) ||
      Object.keys(flow.content.entries).length >= 30
    )
      return;
    change({
      ...data,
      flows: data.flows.map((item) =>
        item.id === flow.id
          ? {
              ...item,
              content: {
                ...item.content,
                entries: {
                  ...item.content.entries,
                  [id]: [{ when: { always: true }, node: "" }],
                },
              },
            }
          : item,
      ),
      configuration: position
        ? {
            ...data.configuration,
            editor: {
              ...data.configuration.editor,
              flow_entry_layouts: {
                ...entryLayouts,
                [flow.id]: { ...entryPositions, [id]: position },
              },
            },
          }
        : data.configuration,
    });
    selectEntry(id);
    setPendingEntry(null);
  }
  function addNode(type: StoryNode["type"], position: Point) {
    if (!flow) return;
    const id = newId("node");
    const bindings = { ...flow.content.bindings };
    let item: StoryNode;
    switch (type) {
      case "dialogue":
        item = { id, type, text: "请输入对白" };
        break;
      case "branch":
        item = {
          id,
          type,
          routes: [
            {
              when: {
                chapter: {
                  id: data.catalog.chapters[0]?.id ?? "",
                  status: "complete",
                },
              },
              node: "",
            },
            { when: { always: true }, node: "" },
          ],
        };
        break;
      case "activity": {
        const key = newId("binding");
        bindings[key] = { activity_id: data.catalog.activities[0]?.id ?? "" };
        item = {
          id,
          type,
          binding: key,
          returns: { completed: "", suspended: "", unavailable: "" },
        };
        break;
      }
      case "random":
        item = { id, type, next: [""] };
        break;
      case "action":
        item = {
          id,
          type,
          action: "open_workspace",
          workspace: "library",
          next: "",
        };
        break;
      case "unlock_building":
        item = {
          id,
          type,
          building_id:
            data.catalog.buildings.find((b) => b.has_interior)?.id ?? "",
          when: { activity_completed: data.catalog.activities[0]?.id ?? "" },
          next: "",
        };
        break;
      default:
        item = { id, type: "end" };
    }
    replaceFlow(
      {
        ...flow,
        content: {
          ...flow.content,
          bindings,
          nodes: [...flow.content.nodes, item],
        },
      },
      { ...positions, [id]: position },
    );
    selectNode(id);
    setInspector(null);
  }
  function removeNode() {
    if (
      !flow ||
      !node ||
      !window.confirm("删除节点？引用此节点的分支需要重新连接，保存时会校验。")
    )
      return;
    const bindings = { ...flow.content.bindings };
    if (
      node.type === "activity" &&
      !flow.content.nodes.some(
        (n) =>
          n.id !== node.id &&
          n.type === "activity" &&
          n.binding === node.binding,
      )
    )
      delete bindings[node.binding];
    replaceFlow({
      ...flow,
      content: {
        ...flow.content,
        bindings,
        nodes: flow.content.nodes.filter((n) => n.id !== node.id),
      },
    });
    selectNode(null);
    setInspector(null);
  }

  function canLocate(target: DiagnosticTarget) {
    if (
      target.kind === "flow" ||
      target.kind === "entry" ||
      target.kind === "node"
    ) {
      return data.flows.some(
        (item) => item.id === (target.flow_id ?? target.id),
      );
    }
    if (target.kind === "interaction")
      return data.configuration.world.interactions?.some(
        (item) => item.id === target.id,
      );
    if (target.kind === "building")
      return data.catalog.buildings.some((item) => item.id === target.id);
    if (target.kind === "npc")
      return data.catalog.npcs.some((item) => item.id === target.id);
    if (target.kind === "assistant") return false;
    return data.quests.some(
      (item) => item.id === (target.quest_id ?? target.id),
    );
  }

  return {
    tab,
    setTab,
    selectedFlow,
    setSelectedFlow,
    selectedNode,
    selectedEntry,
    inspector,
    setInspector,
    pendingEntry,
    setPendingEntry,
    graphFocus,
    setGraphFocus,
    mapFocus,
    setMapFocus,
    editorJump,
    setEditorJump,
    settingsOpen,
    setSettingsOpen,
    layoutVersion,
    setLayoutVersion,
    flow,
    node,
    layouts,
    entryLayouts,
    positions,
    entryPositions,
    entry,
    replaceFlow,
    selectNode,
    selectEntry,
    locate,
    connect,
    replaceNode,
    addFlow,
    addEntry,
    addNode,
    removeNode,
    canLocate,
    reset,
  };
}
