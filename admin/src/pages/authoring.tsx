import { useState } from "react";
import {
  Plus,
  Save,
  CheckCheck,
  Play,
  GitBranch,
  Trash2,
  MapPinned,
  Settings2,
  Undo2,
  Crosshair,
  ListChecks,
  Bot,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Label } from "@/components/ui/label";
import { SelectField } from "@/components/select-field";
import { GroupedPicker } from "@/components/grouped-picker";
import { entryGroups, flowGroups } from "@/lib/authoring-pickers";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import { PageHeader, ErrorNotice, Loading, Empty } from "@/components/common";
import { FlowCanvas } from "@/components/flow-canvas";
import { FlowSettings } from "@/components/flow-settings";
import { NodeEditor, RoutesEditor } from "@/components/node-editor";
import { MapEditor } from "@/components/map-editor";
import { QuestEditor } from "@/components/quest-editor";
import { AssistantEditor } from "@/components/assistant-editor";
import { AuthoringInspector, AuthoringWorkspace } from "@/components/authoring-workspace";
import { api, ApiError, errorMessage } from "@/lib/api";
import { useResource } from "@/lib/use-resource";
import { useDraft } from "@/lib/drafts";
import { newId } from "@/lib/id";
import {
  nodeTitle,
  nodeEdges,
  routePorts,
  setNodeTarget,
  setRouteTarget,
  type Authoring as AuthoringData,
  type Flow,
  type StoryNode,
  type Point,
} from "@/lib/authoring";
type Document = AuthoringData & { students: { id: number; name: string }[] };
interface Validation {
  valid: boolean;
  diagnostics: Diagnostic[];
  reset_impact?: ResetImpact;
}
type ResetImpact = {
  token: string;
  students: number;
  flows: { id: string; title: string; reason: "delete" | "edit" | "task_reset"; sessions: number; active_sessions: number; waiting_sessions: number; students: number }[];
  quests: { id: string; title: string; reason: "delete" | "edit"; progress: number; students: number; events: number }[];
};
type DiagnosticTarget = {
  kind: "flow" | "entry" | "node" | "quest" | "goal" | "interaction" | "building" | "npc" | "assistant";
  id?: string;
  flow_id?: string;
  quest_id?: string;
};
type Diagnostic = { message: string; path: string; code?: string; suggestion?: string; target?: DiagnosticTarget };
interface Preview {
  selected_flow_id: string | null;
  node_id: string | null;
  trace: { node_id: string; type: string }[];
  stopped_at: string;
}
function ResetImpactList({ impact }: { impact: ResetImpact }) {
  const flowReason = { delete: "删除流程", edit: "修改流程", task_reset: "随任务重置" };
  return (
    <ul className="mt-2 max-h-44 space-y-2 overflow-auto text-sm">
      {impact.flows.map((flow) => (
        <li key={`flow:${flow.id}`}>
          <p className="font-medium">流程 · {flow.title}</p>
          <p>{flowReason[flow.reason]}，清理 {flow.sessions} 条会话（进行中 {flow.active_sessions}，等待活动 {flow.waiting_sessions}）。</p>
        </li>
      ))}
      {impact.quests.map((quest) => (
        <li key={`quest:${quest.id}`}>
          <p className="font-medium">任务 · {quest.title}</p>
          <p>{quest.reason === "delete" ? "删除任务" : "修改目标结构"}，清理 {quest.students} 人的任务进度。</p>
        </li>
      ))}
    </ul>
  );
}
export function Authoring({ classroomId }: { classroomId: number }) {
  const resource = useResource<Document>(
    `/teacher/classrooms/${classroomId}/authoring`,
  );
  return resource.loading ? (
    <Loading />
  ) : resource.error ? (
    <ErrorNotice message={resource.error} />
  ) : resource.data ? (
    <AuthoringEditor
      key={`${classroomId}-${resource.data.revision}`}
      initial={resource.data}
      classroomId={classroomId}
    />
  ) : null;
}
function AuthoringEditor({
  initial,
  classroomId,
}: {
  initial: Document;
  classroomId: number;
}) {
  const [assetBusy, setAssetBusy] = useState(false);
  const [data, setData] = useState<AuthoringData>(initial),
    [savedData, setSavedData] = useState<AuthoringData>(initial),
    [draftEpoch, setDraftEpoch] = useState(0),
    [tab, setTab] = useState("flows"),
    [selectedFlow, setSelectedFlow] = useState<string | null>(
      initial.flows[0]?.id ?? null,
    ),
    [selectedNode, setSelectedNode] = useState<string | null>(null),
    [selectedEntry, setSelectedEntry] = useState<string | null>(null),
    [inspector, setInspector] = useState<{ point: Point; trigger: HTMLElement } | null>(null),
    [pendingEntry, setPendingEntry] = useState<Point | null>(null),
    [graphFocus, setGraphFocus] = useState<{ kind: "node" | "entry"; id: string }>(),
    [mapFocus, setMapFocus] = useState<{ kind: "interaction" | "building" | "npc"; id: string }>(),
    [editorJump, setEditorJump] = useState<{ sequence: number; target: DiagnosticTarget }>(),
    [settingsOpen, setSettingsOpen] = useState(false),
    [layoutVersion, setLayoutVersion] = useState(0),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [message, setMessage] = useState(""),
    [diagnostics, setDiagnostics] = useState<Validation["diagnostics"]>([]),
    [resetImpact, setResetImpact] = useState<ResetImpact | undefined>(),
    [confirmReset, setConfirmReset] = useState(false),
    [impactChanged, setImpactChanged] = useState(false),
    [previewOpen, setPreviewOpen] = useState(false),
    [previewStudent, setPreviewStudent] = useState(
      initial.students?.[0]?.id ?? 0,
    ),
    [previewEntry, setPreviewEntry] = useState(""),
    [preview, setPreview] = useState<Preview | null>(null);
  const dirty = data !== savedData;
  useDraft(dirty);
  const base = `/teacher/classrooms/${classroomId}/authoring`,
    flow = data.flows.find((f) => f.id === selectedFlow),
    node = flow?.content.nodes.find((n) => n.id === selectedNode),
    layouts = (data.configuration.editor.flow_layouts ?? {}) as Record<
      string,
      Record<string, Point>
    >,
    entryLayouts = (data.configuration.editor.flow_entry_layouts ?? {}) as typeof layouts,
    positions = flow ? (layouts[flow.id] ?? {}) : {},
    entryPositions = flow ? (entryLayouts[flow.id] ?? {}) : {},
    entry = selectedEntry && flow?.content.entries[selectedEntry];
  function change(next: AuthoringData) {
    setData({ ...next, catalog: { ...next.catalog, npcs: next.npcs.map((npc) => ({ id: npc.id, name: npc.name, map_key: npc.placement?.map_key })) } });
    setMessage("");
    setDiagnostics([]);
    setResetImpact(undefined);
  }
  function discardEdits() {
    setData(savedData);
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
    setPreview(null);
    setError("");
    setMessage("");
    setDiagnostics([]);
    setResetImpact(undefined);
    setDraftEpoch((epoch) => epoch + 1);
  }
  function replaceFlow(next: Flow, nextPositions = positions) {
    change({
      ...data,
      flows: data.flows.map((f) => (f.id === next.id ? next : f)),
      configuration: nextPositions === positions ? data.configuration : {
        ...data.configuration,
        editor: { ...data.configuration.editor, flow_layouts: { ...layouts, [next.id]: nextPositions } },
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
    if (target.kind === "flow" || target.kind === "entry" || target.kind === "node") {
      const selected = data.flows.find((item) => item.id === (target.flow_id ?? target.id));
      if (!selected) return;
      setTab("flows");
      setSelectedFlow(selected.id);
      setGraphFocus(undefined);
      if (target.kind === "node" && target.id && selected.content.nodes.some((item) => item.id === target.id)) {
        selectNode(target.id);
        setGraphFocus({ kind: "node", id: target.id });
      } else if (target.kind === "entry" && target.id && Object.hasOwn(selected.content.entries, target.id)) {
        selectEntry(target.id);
        setGraphFocus({ kind: "entry", id: target.id });
      } else {
        selectNode(null);
        setSettingsOpen(true);
      }
    } else if (target.kind === "interaction" || target.kind === "building" || target.kind === "npc") {
      if (!target.id) return;
      setTab("map");
      setMapFocus({ kind: target.kind, id: target.id });
    } else {
      setTab(target.kind === "assistant" ? "assistant" : "quests");
      setEditorJump((previous) => ({ sequence: (previous?.sequence ?? 0) + 1, target }));
    }
  }
  function connect(source: { kind: "entry" | "node"; id: string }, port: string, target: string) {
    if (!flow) return;
    if (source.kind === "entry") {
      const routes = flow.content.entries[source.id];
      if (!routes) return;
      replaceFlow({ ...flow, content: { ...flow.content, entries: {
        ...flow.content.entries, [source.id]: setRouteTarget(routes, port, target),
      } } });
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
    if (!flow || Object.hasOwn(flow.content.entries, id) || Object.keys(flow.content.entries).length >= 30) return;
    change({
      ...data,
      flows: data.flows.map((item) => item.id === flow.id ? {
        ...item,
        content: { ...item.content, entries: { ...item.content.entries, [id]: [{ when: { always: true }, node: "" }] } },
      } : item),
      configuration: position ? {
        ...data.configuration,
        editor: { ...data.configuration.editor, flow_entry_layouts: { ...entryLayouts, [flow.id]: { ...entryPositions, [id]: position } } },
      } : data.configuration,
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
    replaceFlow({
      ...flow,
      content: { ...flow.content, bindings, nodes: [...flow.content.nodes, item] },
    }, { ...positions, [id]: position });
    selectNode(id);
    setInspector(null);
  }
  function convertActionText() {
    if (!flow || node?.type !== "action" || !node.text || flow.content.nodes.length >= 200) return;
    const id = newId("node");
    const dialogue: StoryNode = { id, type: "dialogue", text: node.text, next: node.id };
    const action = { ...node };
    delete action.text;
    const nodes = flow.content.nodes.flatMap((n) =>
      n.id === node.id ? [dialogue, action] : [
        nodeEdges(n).filter((port) => port.to === node.id)
          .reduce((source, port) => setNodeTarget(source, port.id, id), n),
      ],
    );
    const entries = Object.fromEntries(Object.entries(flow.content.entries).map(([key, routes]) => [
      key, routes.map((route) => route.node === node.id ? { ...route, node: id } : route),
    ]));
    const position = positions[node.id];
    replaceFlow({ ...flow, content: { ...flow.content, nodes, entries } },
      position ? { ...positions, [id]: { x: position.x - 266, y: position.y } } : positions,
    );
    selectNode(id);
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
  const body = () => ({
    expected_revision: data.revision,
      npcs: data.npcs,
    flows: data.flows,
    quests: data.quests,
    configuration: data.configuration,
  });
  async function validate(save = false, confirmationToken?: string) {
    if (busy || assetBusy) return;
    setInspector(null);
    setBusy(true);
    setError("");
    setMessage("");
    setImpactChanged(false);
    try {
      if (!confirmationToken) {
        const result = await api.post<Validation>(`${base}/validate`, body());
        setDiagnostics(result.diagnostics);
        setResetImpact(result.reset_impact);
        if (!result.valid) return;
        if (result.reset_impact) {
          setConfirmReset(save);
          return;
        }
        if (!save) {
          setMessage("校验通过：流程连接、条件和引用有效。");
          return;
        }
      }
      const saved = await api.put<{ revision: number }>(base, {
        ...body(),
        confirmation_token: confirmationToken,
      });
      const next = { ...data, revision: saved.revision };
      setData(next);
      setSavedData(next);
      setResetImpact(undefined);
      setConfirmReset(false);
      setMessage("课程已保存。");
    } catch (e) {
      const detail = e instanceof ApiError ? e.detail : null;
      if (detail && typeof detail === "object" && "reset_impact" in detail && detail.reset_impact) {
        setResetImpact(detail.reset_impact as ResetImpact);
        setImpactChanged(Boolean(confirmationToken));
        setConfirmReset(true);
      } else {
        setConfirmReset(false);
        setResetImpact(undefined);
        if (detail && typeof detail === "object" && "diagnostics" in detail && Array.isArray(detail.diagnostics)) {
          setDiagnostics(detail.diagnostics as Diagnostic[]);
        } else setError(errorMessage(e));
      }
    } finally {
      setBusy(false);
    }
  }
  async function runPreview() {
    setBusy(true);
    setError("");
    setPreview(null);
    try {
      setPreview(
        await api.post<Preview>(`${base}/preview`, {
          ...body(),
          student_id: previewStudent,
          entry: previewEntry,
        }),
      );
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  const entries = Array.from(
    new Set(data.flows.flatMap((f) => Object.keys(f.content.entries))),
  );
  const previewFlow = data.flows.find((f) => f.id === preview?.selected_flow_id);
  const availableEntries = flow ? entryGroups(data, flow) : [];
  const entryLabel = (key: string) =>
    key.startsWith("interaction:")
      ? (data.configuration.world.interactions?.find(
          (i) => `interaction:${i.id}` === key,
        )?.label ?? "地图交互")
      : (data.catalog.npcs.find((n) => n.id === key)?.name ?? "角色入口");
  function canLocate(target: DiagnosticTarget) {
    if (target.kind === "flow" || target.kind === "entry" || target.kind === "node") {
      return data.flows.some((item) => item.id === (target.flow_id ?? target.id));
    }
    if (target.kind === "interaction") return data.configuration.world.interactions?.some((item) => item.id === target.id);
    if (target.kind === "building") return data.catalog.buildings.some((item) => item.id === target.id);
    if (target.kind === "npc") return data.catalog.npcs.some((item) => item.id === target.id);
    if (target.kind === "assistant") return true;
    return data.quests.some((item) => item.id === (target.quest_id ?? target.id));
  }
  const flowToolbar = (
    <>
      <GroupedPicker
        className="w-52"
        label="选择流程"
        placeholder="选择流程"
        value={flow?.id}
        groups={flowGroups(data.flows, data.catalog.chapters)}
        onChange={(id) => {
          setSelectedFlow(id);
          selectNode(null);
          setInspector(null);
          setGraphFocus(undefined);
        }}
      />
      <Button size="sm" variant="outline" onClick={addFlow}><Plus />新建流程</Button>
      {flow && (
        <>
          <Button size="sm" variant="ghost" onClick={() => setSettingsOpen(true)}>
            <Settings2 />流程设置与入口
          </Button>
          <Button
            variant="ghost"
            size="icon-sm"
            aria-label="删除流程"
            title="删除流程"
            onClick={() => {
              if (!window.confirm(`删除「${flow.title}」及其流程入口？`)) return;
              const remaining = data.flows.filter((item) => item.id !== flow.id);
              change({ ...data, flows: remaining });
              setSelectedFlow(remaining[0]?.id ?? null);
              selectNode(null);
              setInspector(null);
            }}
          >
            <Trash2 />
          </Button>
        </>
      )}
    </>
  );
  return (
    <>
      <PageHeader
        title="教学流程"
        description="组织公共与章节流程，连接地图交互和真实学习活动。"
      >
        <Button
          variant="outline"
          disabled={busy}
          onClick={() => void validate()}
        >
          <CheckCheck />
          校验
        </Button>
        <Button
          variant="outline"
          disabled={busy || !entries.length}
          onClick={() => {
            setPreviewEntry(entries[0]);
            setPreview(null);
            setPreviewOpen(true);
          }}
        >
          <Play />
          预览
        </Button>
        {dirty && (
          <AlertDialog>
            <AlertDialogTrigger render={<Button variant="ghost" disabled={busy || assetBusy} />}>
              <Undo2 />取消编辑
            </AlertDialogTrigger>
            <AlertDialogContent>
              <AlertDialogHeader>
                <AlertDialogTitle>放弃本次修改？</AlertDialogTitle>
                <AlertDialogDescription>
                  四个标签页的未保存编辑将一起丢弃，课程恢复到上次保存的内容。
                </AlertDialogDescription>
              </AlertDialogHeader>
              <AlertDialogFooter>
                <AlertDialogCancel>继续编辑</AlertDialogCancel>
                <AlertDialogAction variant="destructive" onClick={discardEdits}>放弃修改</AlertDialogAction>
              </AlertDialogFooter>
            </AlertDialogContent>
          </AlertDialog>
        )}
        <span role="status" className="flex items-center">
          {dirty ? (
            <Badge variant="outline" className="gap-1.5 border-amber-200 bg-amber-50 text-amber-800" title="有未保存的修改">
              <span aria-hidden="true" className="size-1.5 rounded-full bg-amber-500" />
              未保存
            </Badge>
          ) : message === "课程已保存。" ? (
            <Badge variant="secondary" className="gap-1.5"><CheckCheck />{message}</Badge>
          ) : null}
        </span>
        <Button disabled={busy || assetBusy || !dirty} onClick={() => void validate(true)}>
          <Save />
          {busy ? "处理中…" : "保存课程"}
        </Button>
      </PageHeader>
      <ErrorNotice message={!previewOpen ? error : ""} />
      {message && message !== "课程已保存。" && (
        <p role="status" className="notice-success">
          {message}
        </p>
      )}
      {diagnostics.length > 0 && (
        <section aria-label="校验结果" className="mb-4 overflow-hidden rounded-lg border border-destructive/25 bg-destructive/5">
          <p role="alert" className="border-b border-destructive/15 px-4 py-2.5 text-sm font-medium text-destructive">
            {diagnostics.length} 处需要处理，修改尚未保存。
          </p>
          <ul className="max-h-52 divide-y divide-destructive/15 overflow-auto">
            {diagnostics.map(({ message, path, suggestion, target }, i) => (
              <li className="flex items-start justify-between gap-3 px-4 py-2.5 text-sm" key={`${path}:${i}`}>
                <div>
                  <p>{message}</p>
                  {suggestion && <p className="mt-1 text-xs text-muted-foreground">{suggestion}</p>}
                </div>
                {target && canLocate(target) && (
                  <Button size="sm" variant="outline" onClick={() => locate(target)} disabled={busy}><Crosshair />定位</Button>
                )}
              </li>
            ))}
          </ul>
        </section>
      )}
      {resetImpact && (
        <section aria-label="保存影响" className="mb-4 overflow-hidden rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-950">
          <p role="status" className="font-medium">保存会重置 {resetImpact.students} 人的相关进度，需确认后才能发布。</p>
          <ResetImpactList impact={resetImpact} />
        </section>
      )}
      <AlertDialog open={confirmReset && !!resetImpact} onOpenChange={(open) => { if (!busy) setConfirmReset(open); }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>确认重置受影响的进度？</AlertDialogTitle>
            <AlertDialogDescription>
              确认后将保存修改。受影响的互动将结束，保留的任务需要重新完成。阅读、作答记录和成绩会保留。
            </AlertDialogDescription>
          </AlertDialogHeader>
          {impactChanged && <p role="status" className="rounded-md bg-amber-50 p-2 text-sm text-amber-900">受影响的记录已变化，请按最新范围重新确认。</p>}
          {resetImpact && <ResetImpactList impact={resetImpact} />}
          <AlertDialogFooter>
            <AlertDialogCancel disabled={busy}>继续编辑</AlertDialogCancel>
            <AlertDialogAction variant="destructive" disabled={busy || !resetImpact} onClick={() => void validate(true, resetImpact?.token)}>
              {busy ? "保存中…" : "重置进度并保存"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
      <div inert={busy} aria-busy={busy} data-testid="authoring-fields">
        <Tabs key={draftEpoch} value={tab} className="gap-3" onValueChange={(value) => { setTab(value); setInspector(null); setMapFocus(undefined); setEditorJump(undefined); }}>
            <TabsList variant="line" aria-label="教学流程视图">
              <TabsTrigger value="flows"><GitBranch />公共与章节流程</TabsTrigger>
              <TabsTrigger value="map"><MapPinned />地图交互与开放规则</TabsTrigger>
              <TabsTrigger value="quests"><ListChecks aria-hidden="true" />任务目标与依赖</TabsTrigger>
              <TabsTrigger value="assistant"><Bot aria-hidden="true" />助教教学角色</TabsTrigger>
            </TabsList>
          <TabsContent value="map">
            <MapEditor data={data} onChange={change} focusTarget={mapFocus} />
          </TabsContent>
          <TabsContent value="quests">
            <QuestEditor key={editorJump?.sequence} data={data} onChange={change} focusTarget={editorJump && {
              questId: editorJump.target.quest_id ?? editorJump.target.id,
              goalId: editorJump.target.kind === "goal" ? editorJump.target.id : undefined,
            }} />
          </TabsContent>
          <TabsContent value="assistant">
            <AssistantEditor onUploadState={setAssetBusy} classroomId={classroomId} key={editorJump?.sequence} data={data} onChange={change} focusTarget={editorJump && {
              roleId: editorJump.target.id,
            }} />
          </TabsContent>
          <TabsContent value="flows">
            {flow ? (
              <>
                <FlowCanvas
                  key={`${flow.id}:${layoutVersion}`}
                  nodes={flow.content.nodes}
                  labels={Object.fromEntries(flow.content.nodes.map((n) => [n.id, nodeTitle(n, data.catalog, flow)]))}
                  entries={Object.entries(flow.content.entries).map(([key, routes]) => ({
                    id: key, title: entryLabel(key), ports: routePorts(routes),
                  }))}
                  positions={positions}
                  entryPositions={entryPositions}
                  selected={selectedNode}
                  selectedEntry={selectedEntry}
                  focusTarget={graphFocus}
                  onSelect={selectNode}
                  onSelectEntry={selectEntry}
                  onInspect={(source, point, trigger) => {
                    if (source.kind === "entry") selectEntry(source.id);
                    else selectNode(source.id);
                    setInspector({ point, trigger });
                  }}
                  onDismissInspector={() => setInspector(null)}
                  renderWorkspace={({ palette, canvas }) => (
                    <AuthoringWorkspace tools={palette} toolbar={flowToolbar} className="authoring-flow-workspace">
                      {canvas}
                    </AuthoringWorkspace>
                  )}
                  onConnect={connect}
                  onAddNode={addNode}
                  onAddEntry={setPendingEntry}
                  entryAvailable={availableEntries.length > 0 && Object.keys(flow.content.entries).length < 30}
                  onMove={(id, point) => replaceFlow(flow, { ...positions, [id]: point })}
                  onMoveEntry={(id, point) => change({
                    ...data,
                    configuration: {
                      ...data.configuration,
                      editor: { ...data.configuration.editor, flow_entry_layouts: {
                        ...entryLayouts, [flow.id]: { ...entryPositions, [id]: point },
                      } },
                    },
                  })}
                  onAutoArrange={() => {
                    change({
                      ...data,
                      configuration: {
                        ...data.configuration,
                        editor: {
                          ...data.configuration.editor,
                          flow_layouts: { ...layouts, [flow.id]: {} },
                          flow_entry_layouts: { ...entryLayouts, [flow.id]: {} },
                        },
                      },
                    });
                    setLayoutVersion((v) => v + 1);
                  }}
                />
                <AuthoringInspector
                  anchor={node || entry ? inspector?.point ?? null : null}
                  returnFocus={inspector?.trigger}
                  title={node ? "节点属性" : "入口属性"}
                  onClose={() => setInspector(null)}
                >
                  {node ? (
                    <NodeEditor
                      key={node.id}
                      node={node}
                      flow={flow}
                      catalog={data.catalog}
                      onChange={replaceNode}
                      onFlowChange={replaceFlow}
                      onDelete={removeNode}
                      onConvertActionText={convertActionText}
                    />
                  ) : selectedEntry && entry ? (
                    <div className="node-properties" key={selectedEntry}>
                      <p className="hint">触发入口</p>
                      <h2 className="font-semibold mb-3">{entryLabel(selectedEntry)}</h2>
                      <RoutesEditor
                        routes={entry}
                        onChange={(routes) => replaceFlow({
                          ...flow,
                          content: { ...flow.content, entries: { ...flow.content.entries, [selectedEntry]: routes } },
                        })}
                        flow={flow}
                        catalog={data.catalog}
                      />
                      <Button variant="ghost" size="sm" className="mt-3" onClick={() => {
                        if (!window.confirm("移除此触发入口？节点会保留。")) return;
                        const nextEntries = { ...flow.content.entries };
                        delete nextEntries[selectedEntry];
                        replaceFlow({ ...flow, content: { ...flow.content, entries: nextEntries } });
                        selectNode(null);
                      }}><Trash2 />移除入口</Button>
                    </div>
                  ) : null}
                </AuthoringInspector>
              </>
            ) : (
              <AuthoringWorkspace tools={<p className="hint p-2">先新建流程，再添加节点。</p>} toolbar={flowToolbar}>
                <Empty title="选择或创建一个流程" description="公共流程可以贯穿各章节。章节流程归属具体教学章节。" />
              </AuthoringWorkspace>
            )}
          </TabsContent>
        </Tabs>
      </div>
      {settingsOpen && flow && (
        <FlowSettings
          flow={flow}
          catalog={data.catalog}
          entries={availableEntries}
          onChange={replaceFlow}
          onClose={() => setSettingsOpen(false)}
          onAddEntry={addEntry}
        />
      )}
      {pendingEntry && flow && (
        <Dialog open onOpenChange={(open) => { if (!open) setPendingEntry(null); }}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>添加触发入口</DialogTitle>
              <DialogDescription>选择触发流程的 NPC 或交互点，再在画布上把入口连到起始节点。</DialogDescription>
            </DialogHeader>
            <GroupedPicker label="选择入口位置" placeholder="选择地图位置" groups={availableEntries} onChange={(id) => addEntry(id, pendingEntry)} />
          </DialogContent>
        </Dialog>
      )}
      <Dialog
        open={previewOpen}
        onOpenChange={(v) => {
          if (!busy) setPreviewOpen(v);
        }}
      >
        <DialogContent className="sm:max-w-xl">
          <DialogHeader>
            <DialogTitle>预览流程</DialogTitle>
            <DialogDescription>
              依据所选学生在本班的真实记录判断分支。预览不会更新进度、授予解锁或调用
              AI。
            </DialogDescription>
          </DialogHeader>
          <ErrorNotice message={error} />
          <div className="field">
            <Label htmlFor="preview-student">学生</Label>
            <SelectField
              id="preview-student"
              value={previewStudent}
              onValueChange={(selectedValue) => setPreviewStudent(Number(selectedValue))}
            >
              <option value={0}>选择学生</option>
              {initial.students?.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </SelectField>
          </div>
          <div className="field">
            <Label htmlFor="preview-entry">入口</Label>
            <SelectField
              id="preview-entry"
              value={previewEntry}
              onValueChange={(selectedValue) => setPreviewEntry(selectedValue)}
            >
              {entries.map((key) => (
                <option key={key} value={key}>
                  {entryLabel(key)}
                </option>
              ))}
            </SelectField>
          </div>
          <Button
            disabled={busy || !previewStudent || !previewEntry}
            onClick={() => void runPreview()}
          >
            <Play />
            执行只读预览
          </Button>
          {preview && (
            <div className="border rounded-lg p-4 max-h-64 overflow-y-auto">
              <p className="font-medium mb-3">
                {previewFlow?.title ?? "没有匹配的流程"}
              </p>
              <ol className="list-decimal pl-5 space-y-2">
                {preview.trace.map((step, i) => {
                  const n = previewFlow?.content.nodes.find((n) => n.id === step.node_id);
                  return <li key={i}>{n ? nodeTitle(n, data.catalog, previewFlow) : step.type}</li>;
                })}
              </ol>
              <p className="hint mt-3">预览停止：{preview.stopped_at}</p>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </>
  );
}
