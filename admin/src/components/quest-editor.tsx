import { SelectField } from "@/components/select-field";
import { GroupedPicker } from "@/components/grouped-picker";
import { npcGroups } from "@/lib/authoring-pickers";
import { useMemo, useState, type MouseEvent } from "react";
import {
  BookOpen,
  ClipboardCheck,
  Code,
  Hand,
  MapPin,
  MessageCircle,
  MessagesSquare,
  Plus,
  Reply,
  SlidersHorizontal,
  Trash2,
  type LucideIcon,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Empty } from "@/components/common";
import { FlowCanvas } from "@/components/flow-canvas";
import { AuthoringInspector, AuthoringWorkspace } from "@/components/authoring-workspace";
import { newId } from "@/lib/id";
import type {
  Authoring,
  FlowPort,
  Quest,
  QuestNode,
  QuestNodeType,
  Point,
  StoryNode,
} from "@/lib/authoring";
const goalKinds = {
  TALK_TO_NPC: { label: "与角色交谈", icon: MessageCircle, tone: "dialogue" },
  PLAY_DIALOGUE: { label: "完成剧情对白", icon: MessagesSquare, tone: "dialogue" },
  REACH_LOCATION: { label: "到达地点", icon: MapPin, tone: "action" },
  INTERACT_OBJECT: { label: "交互地图对象", icon: Hand, tone: "action" },
  STUDY_LESSON: { label: "完成阅读活动", icon: BookOpen, tone: "activity" },
  COMPLETE_PRACTICE: { label: "完成练习", icon: Code, tone: "activity" },
  PASS_ASSESSMENT: { label: "通过测验", icon: ClipboardCheck, tone: "activity" },
  REPORT_BACK: { label: "回报角色", icon: Reply, tone: "dialogue" },
} satisfies Record<QuestNodeType, { label: string; icon: LucideIcon; tone: StoryNode["type"] }>;
const goalPalette = (Object.keys(goalKinds) as QuestNodeType[]).map((kind) => ({
  kind, ...goalKinds[kind],
}));
const newDependency = "dependency:add";
const dependencyPort = (target: string) => `dependency:to:${target}`;
const emptyPositions: Record<string, Point> = {};
const emptyGoals: QuestNode[] = [];

function describeGoals(goals: QuestNode[]) {
  const nodes: StoryNode[] = goals.map(({ id }) => ({ id, type: "random", next: [] }));
  const labels = Object.fromEntries(goals.map(({ id, title }) => [id, title]));
  const captions = Object.fromEntries(goals.map((goal) => [
    goal.id, `${goalKinds[goal.type].label}${goal.optional ? " · 可选" : ""}`,
  ]));
  const nodeAppearances = Object.fromEntries(goals.map((goal) => [goal.id, goalKinds[goal.type]]));
  const ports: Record<string, FlowPort[]> = Object.fromEntries(goals.map(({ id }) => [id, []]));
  for (const goal of goals) {
    for (const id of goal.dependencies) {
      ports[id]?.push({ id: dependencyPort(goal.id), to: goal.id, label: goal.title });
    }
  }
  for (const { id } of goals) ports[id].push({ id: newDependency, to: "", label: "添加后续目标" });
  return { nodes, labels, captions, ports, nodeAppearances };
}

function wouldCycle(goals: QuestNode[], parent: string, child: string) {
  const byId = new Map(goals.map((goal) => [goal.id, goal]));
  const pending = [parent];
  const visited = new Set<string>();
  while (pending.length) {
    const id = pending.pop()!;
    if (id === child) return true;
    if (visited.has(id)) continue;
    visited.add(id);
    pending.push(...(byId.get(id)?.dependencies ?? []));
  }
  return false;
}

type Inspector =
  | { kind: "settings"; anchor: HTMLElement | Point }
  | { kind: "goal"; id: string; anchor: HTMLElement | Point; trigger: HTMLElement };

export function QuestEditor({
  data,
  onChange,
  focusTarget,
}: {
  data: Authoring;
  onChange: (v: Authoring) => void;
  focusTarget?: { questId?: string; goalId?: string };
}) {
  const [selected, setSelected] = useState(focusTarget?.questId ?? data.quests?.[0]?.id ?? ""),
    [nodeId, setNodeId] = useState<string | null>(focusTarget?.goalId ?? null),
    [graphFocus] = useState(() => focusTarget?.goalId ? { kind: "node" as const, id: focusTarget.goalId } : undefined),
    [inspector, setInspector] = useState<Inspector | null>(null),
    [layoutEpoch, setLayoutEpoch] = useState(0),
    [graphIssue, setGraphIssue] = useState("");
  const quest = data.quests.find((q) => q.id === selected) ?? data.quests[0],
    goals = quest?.definition.nodes ?? emptyGoals,
    node = goals.find((n) => n.id === nodeId),
    inspectedGoal = inspector?.kind === "goal" ? goals.find((n) => n.id === inspector.id) : undefined,
    layouts = (data.configuration.editor.quest_layouts ?? {}) as Record<
      string,
      Record<string, Point>
    >,
    positions = (quest && layouts[quest.id]) ?? emptyPositions;
  const graph = useMemo(() => describeGoals(goals), [goals]);

  function withLayout(questId: string, nextPositions: Record<string, Point>) {
    return {
      ...data.configuration,
      editor: {
        ...data.configuration.editor,
        quest_layouts: { ...layouts, [questId]: nextPositions },
      },
    };
  }
  function replace(q: Quest, nextPositions?: Record<string, Point>) {
    const updated = {
      ...q,
      definition: {
        ...q.definition,
        id: q.id,
        title: q.title,
        chapter_id: q.chapter_id,
      },
    };
    const quests = data.quests.map((old) => (old.id === q.id ? updated : old));
    onChange({
      ...data,
      quests,
      configuration: nextPositions ? withLayout(q.id, nextPositions) : data.configuration,
      catalog: {
        ...data.catalog,
        quests: quests.map((q) => ({
          id: q.id,
          title: q.title,
          nodes: q.definition.nodes,
        })),
      },
    });
  }
  function update(n: QuestNode) {
    if (quest)
      replace({
        ...quest,
        definition: {
          ...quest.definition,
          nodes: quest.definition.nodes.map((old) =>
            old.id === n.id ? n : old,
          ),
        },
      });
  }
  function create(event: MouseEvent<HTMLButtonElement>) {
    // Selecting the new task replaces the previous workspace and its toolbar.
    const anchor = event.currentTarget.getBoundingClientRect();
    const id = newId("quest"),
      chapter = data.catalog.chapters[0]?.id ?? "",
      q: Quest = {
        id,
        title: "新的教学任务",
        chapter_id: chapter,
        definition: {
          id,
          title: "新的教学任务",
          chapter_id: chapter,
          kind: "side",
          nodes: [],
          effects: [],
        },
      };
    onChange({
      ...data,
      quests: [...data.quests, q],
      catalog: {
        ...data.catalog,
        quests: [...data.catalog.quests, { id, title: q.title, nodes: [] }],
      },
    });
    setSelected(id);
    setNodeId(null);
    setGraphIssue("");
    setInspector({ kind: "settings", anchor: { x: anchor.left, y: anchor.bottom } });
  }
  function addNode(type: QuestNodeType, position: Point) {
    if (!quest) return;
    const n: QuestNode = {
      id: newId("goal"),
      type,
      title: goalKinds[type].label,
      dependencies: [],
      aggregation: "ALL",
      optional: false,
    };
    if (["TALK_TO_NPC", "PLAY_DIALOGUE", "REPORT_BACK"].includes(type))
      n.target = data.catalog.npcs[0]?.id ?? "";
    if (["STUDY_LESSON", "COMPLETE_PRACTICE", "PASS_ASSESSMENT"].includes(type))
      n.activity_id =
        data.catalog.activities.find((a) =>
          type === "STUDY_LESSON"
            ? ["lesson", "reading"].includes(a.kind ?? "")
            : a.kind ===
              (type === "PASS_ASSESSMENT" ? "assessment" : "practice"),
        )?.id ?? "";
    if (["REACH_LOCATION", "INTERACT_OBJECT"].includes(type))
      n.target = data.catalog.maps[0]?.objects[0]?.id ?? "";
    replace(
      { ...quest, definition: { ...quest.definition, nodes: [...goals, n] } },
      { ...positions, [n.id]: position },
    );
    setNodeId(n.id);
    setInspector(null);
    setGraphIssue("");
  }
  function setDependency(goal: QuestNode, parent: string, add: boolean) {
    const present = goal.dependencies.includes(parent);
    if (present === add) return;
    if (add && wouldCycle(goals, parent, goal.id)) {
      setGraphIssue("这条依赖会形成循环。");
      return;
    }
    if (!add && goal.condition?.node === parent) {
      setGraphIssue("请先移除依赖该前置目标的分支条件。");
      return;
    }
    setGraphIssue("");
    update({
      ...goal,
      dependencies: add ? [...goal.dependencies, parent] : goal.dependencies.filter((id) => id !== parent),
    });
  }
  function removeGoal(goal: QuestNode) {
    if (!quest) return;
    if (goals.some((other) => other.condition?.node === goal.id)) {
      setGraphIssue("请先移除依赖此目标的分支条件。");
      return;
    }
    if (!window.confirm("删除目标及相连依赖？引用此目标的流程检查点需要重新连接。")) return;
    const nextPositions = { ...positions };
    delete nextPositions[goal.id];
    replace({
      ...quest,
      definition: {
        ...quest.definition,
        nodes: goals.filter((other) => other.id !== goal.id).map((other) =>
          other.dependencies.includes(goal.id)
            ? { ...other, dependencies: other.dependencies.filter((id) => id !== goal.id) }
            : other,
        ),
      },
    }, nextPositions);
    setNodeId(null);
    setInspector(null);
    setGraphIssue("");
  }
  function selectGoal(id: string | null) {
    setNodeId(id);
    setGraphIssue("");
    setInspector((current) => current?.kind === "goal" && current.id !== id ? null : current);
  }
  function inspectGoal(id: string, anchor: HTMLElement | Point, trigger: HTMLElement) {
    setNodeId(id);
    setGraphIssue("");
    setInspector({ kind: "goal", id, anchor, trigger });
  }
  const toolbar = (
    <>
        <SelectField
          aria-label="选择任务"
          value={quest?.id ?? ""}
          disabled={!data.quests.length}
          onValueChange={(selectedValue) => {
            setSelected(selectedValue);
            setNodeId(null);
            setInspector(null);
            setGraphIssue("");
          }}
        >
          <option value="" disabled>
            选择任务
          </option>
          {data.quests.map((q) => (
            <option key={q.id} value={q.id}>
              {q.title}
            </option>
          ))}
        </SelectField>
        <Button
          variant="outline"
          disabled={!data.catalog.chapters.length}
          onClick={create}
        >
          <Plus />
          新建任务
        </Button>
        {quest ? (
          <>
            <Button variant="outline" onClick={(event) => setInspector({ kind: "settings", anchor: event.currentTarget })}>
              <SlidersHorizontal />
              任务设置
            </Button>
            {node ? (
              <Button variant="ghost" onClick={(event) => inspectGoal(node.id, event.currentTarget, event.currentTarget)}>
                编辑目标属性
              </Button>
            ) : null}
            <Button
              variant="ghost"
              size="icon-sm"
              aria-label="删除任务"
              onClick={() => {
                if (!window.confirm("删除任务？服务端会阻止删除已有学习证据或流程引用的任务。")) return;
                onChange({
                  ...data,
                  quests: data.quests.filter((q) => q.id !== quest.id),
                  catalog: {
                    ...data.catalog,
                    quests: data.catalog.quests.filter((q) => q.id !== quest.id),
                  },
                });
                setSelected("");
                setNodeId(null);
                setInspector(null);
                setGraphIssue("");
              }}
            >
              <Trash2 />
            </Button>
          </>
        ) : null}
    </>
  );

  if (!quest) {
    return (
      <AuthoringWorkspace toolbar={toolbar} tools={<p className="hint">新建任务后可以添加目标。</p>}>
        <Empty
          title="选择或创建教学任务"
          description="用依赖关系连接学习目标，支持 ALL、ANY、可选节点和条件分支。"
        />
      </AuthoringWorkspace>
    );
  }

  return (
    <FlowCanvas
      key={`${quest.id}:${layoutEpoch}`}
      {...graph}
      positions={positions}
      selected={nodeId}
      focusTarget={quest.id === focusTarget?.questId ? graphFocus : undefined}
      onSelect={selectGoal}
      onInspect={(source, anchor, trigger) => {
        if (source.kind === "node") inspectGoal(source.id, anchor, trigger);
      }}
      onDismissInspector={() => setInspector(null)}
      paletteItems={goalPalette}
      maxNodes={100}
      onAddNode={addNode}
      canConnect={(source, port, target) => source.kind === "node" && port === newDependency
        && !goals.find((goal) => goal.id === target)?.dependencies.includes(source.id)
        && !wouldCycle(goals, source.id, target)}
      onConnect={(source, port, target) => {
        if (source.kind !== "node") return;
        const goal = goals.find((item) => target ? item.id === target : dependencyPort(item.id) === port);
        if (goal) setDependency(goal, source.id, Boolean(target));
      }}
      onMove={(id, point) => onChange({
        ...data,
        configuration: withLayout(quest.id, { ...positions, [id]: point }),
      })}
      onAutoArrange={() => {
        onChange({ ...data, configuration: withLayout(quest.id, {}) });
        setInspector(null);
        setLayoutEpoch((value) => value + 1);
      }}
      renderWorkspace={({ palette, canvas }) => (
        <AuthoringWorkspace className="authoring-flow-workspace" tools={palette} toolbar={toolbar}>
          {canvas}
          {graphIssue && (
            <Badge role="status" variant="destructive" className="absolute bottom-3 left-1/2 z-20 h-auto max-w-[calc(100%-1.5rem)] -translate-x-1/2 whitespace-normal bg-background px-3 py-1 shadow-sm">
              {graphIssue}
            </Badge>
          )}
          <AuthoringInspector
            anchor={inspector?.kind === "settings" ? inspector.anchor : null}
            title="任务设置"
            onClose={() => setInspector(null)}
          >
            <QuestSettings quest={quest} data={data} onChange={replace} />
          </AuthoringInspector>
          <AuthoringInspector
            anchor={inspector?.kind === "goal" ? inspector.anchor : null}
            returnFocus={inspector?.kind === "goal" ? inspector.trigger : undefined}
            title="目标属性"
            onClose={() => setInspector(null)}
          >
            {inspectedGoal ? (
              <GoalProperties
                key={inspectedGoal.id}
                data={data}
                quest={quest}
                node={inspectedGoal}
                update={update}
                issue={graphIssue}
                setDependency={setDependency}
                removeGoal={removeGoal}
              />
            ) : null}
          </AuthoringInspector>
        </AuthoringWorkspace>
      )}
    />
  );
}

function QuestSettings({ quest, data, onChange: replace }: {
  quest: Quest;
  data: Authoring;
  onChange: (quest: Quest) => void;
}) {
  return (
    <>
      <div className="field">
        <Label htmlFor="quest-title">任务名称</Label>
        <Input
          id="quest-title"
          value={quest.title}
          onChange={(e) => replace({ ...quest, title: e.target.value })}
        />
      </div>
      <div className="field">
        <Label htmlFor="quest-chapter">所属章节</Label>
        <SelectField
          id="quest-chapter"
          value={quest.chapter_id}
          onValueChange={(selectedValue) =>
            replace({ ...quest, chapter_id: selectedValue })
          }
        >
          {data.catalog.chapters.map((c) => (
            <option key={c.id} value={c.id}>
              {c.title}
            </option>
          ))}
        </SelectField>
      </div>
      <div className="field">
        <Label htmlFor="quest-kind">任务类型</Label>
        <SelectField
          id="quest-kind"
          value={quest.definition.kind ?? "main"}
          onValueChange={(selectedValue) =>
            replace({
              ...quest,
              definition: {
                ...quest.definition,
                kind: selectedValue as "main" | "side",
              },
            })
          }
        >
          <option value="main">主线</option>
          <option value="side">支线</option>
        </SelectField>
      </div>
      <p className="hint">
        任务定义与学生进度分开保存。有学习证据的任务会受到服务端保护；完成条件始终由服务端验证。
      </p>
    </>
  );
}

function GoalProperties({ data, quest, node, update, issue, setDependency, removeGoal }: {
  data: Authoring;
  quest: Quest;
  node: QuestNode;
  update: (node: QuestNode) => void;
  issue: string;
  setDependency: (node: QuestNode, parent: string, add: boolean) => void;
  removeGoal: (node: QuestNode) => void;
}) {
  const objectOptions = new Map<string, string>();
  const isMapGoal = node.type === "REACH_LOCATION" || node.type === "INTERACT_OBJECT";
  if (isMapGoal) {
    for (const map of data.catalog.maps) {
      for (const object of map.objects) objectOptions.set(object.id, `${map.label} · ${object.label}`);
    }
    for (const interaction of data.configuration.world.interactions ?? []) {
      if (!objectOptions.has(interaction.id)) objectOptions.set(interaction.id, `课程交互 · ${interaction.label}`);
    }
  }
  return (
    <>
      <div className="mb-4 flex items-center justify-between gap-2">
        <Badge variant="secondary">{goalKinds[node.type].label}</Badge>
        <Button variant="ghost" size="icon-sm" aria-label="删除任务目标" onClick={() => removeGoal(node)}>
          <Trash2 />
        </Button>
      </div>
      {issue ? <p role="alert" className="mb-3 text-xs text-destructive">{issue}</p> : null}
      <div className="field">
        <Label htmlFor="goal-title">目标标题</Label>
        <Input
          id="goal-title"
          value={node.title}
          onChange={(e) =>
            update({ ...node, title: e.target.value })
          }
        />
      </div>
      {["TALK_TO_NPC", "PLAY_DIALOGUE", "REPORT_BACK"].includes(
        node.type,
      ) && (
        <div className="field">
          <Label htmlFor="goal-npc">目标角色</Label>
          <GroupedPicker
            id="goal-npc"
            label="目标角色"
            groups={npcGroups(data.catalog)}
            value={node.target ?? ""}
            onChange={(target) => update({ ...node, target })}
          />
        </div>
      )}
      {isMapGoal && (
        <div className="field">
          <Label htmlFor="goal-object">地图对象</Label>
          <SelectField
            id="goal-object"
            value={node.target ?? ""}
            onValueChange={(selectedValue) =>
              update({ ...node, target: selectedValue })
            }
          >
            {Array.from(objectOptions, ([id, label]) => <option key={id} value={id}>{label}</option>)}
          </SelectField>
        </div>
      )}
      {[
        "STUDY_LESSON",
        "COMPLETE_PRACTICE",
        "PASS_ASSESSMENT",
      ].includes(node.type) && (
        <>
          <div className="field">
            <Label htmlFor="goal-activity">学习活动</Label>
            <SelectField
              id="goal-activity"
              value={node.activity_id ?? ""}
              onValueChange={(selectedValue) =>
                update({ ...node, activity_id: selectedValue })
              }
            >
              <option value="">选择活动</option>
              {data.catalog.activities
                .filter(
                  (a) =>
                    !a.kind ||
                    (node.type === "STUDY_LESSON"
                      ? ["lesson", "reading"].includes(a.kind)
                      : a.kind ===
                        (node.type === "PASS_ASSESSMENT"
                          ? "assessment"
                          : "practice")),
                )
                .map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.title}
                  </option>
                ))}
            </SelectField>
          </div>
          <div className="field">
            <Label htmlFor="goal-navigation">
              导航角色（可选）
            </Label>
            <GroupedPicker
              id="goal-navigation"
              label="导航角色（可选）"
              groups={npcGroups(data.catalog)}
              standalone={[{ value: "", label: "无导航提示" }]}
              value={node.navigation_target ?? ""}
              onChange={(selectedValue) => {
                const next = { ...node };
                if (selectedValue)
                  next.navigation_target = selectedValue;
                else delete next.navigation_target;
                update(next);
              }}
            />
          </div>
        </>
      )}
      {node.type === "PLAY_DIALOGUE" && (
        <div className="field">
          <Label htmlFor="goal-dialogue">
            完成此目标的剧情流程
          </Label>
          <SelectField
            id="goal-dialogue"
            value={node.dialogue_id ?? ""}
            onValueChange={(selectedValue) =>
              update({ ...node, dialogue_id: selectedValue })
            }
          >
            <option value="">选择已关联任务的流程</option>
            {data.flows
              .filter((f) => f.quest_id === quest.id)
              .map((f) => (
                <option
                  key={f.id}
                  value={`story:${f.id}:${node.id}`}
                >
                  {f.title}
                </option>
              ))}
          </SelectField>
          <p className="hint">
            同时在对应对白节点里选择此目标作为交谈检查点。
          </p>
        </div>
      )}
      <div className="field">
        <Label htmlFor="goal-aggregation">前置依赖关系</Label>
        <SelectField
          id="goal-aggregation"
          value={node.aggregation ?? "ALL"}
          onValueChange={(selectedValue) =>
            update({
              ...node,
              aggregation: selectedValue as "ALL" | "ANY",
            })
          }
        >
          <option value="ALL">全部前置完成（ALL）</option>
          <option value="ANY">任一前置完成（ANY）</option>
        </SelectField>
        <div className="check-list mt-1 max-h-40">
          {quest.definition.nodes
            .filter((n) => n.id !== node.id)
            .map((n) => (
              <div key={n.id} className="flex items-center gap-2">
                <Checkbox
                  id={`goal-dependency-${n.id}`}
                  aria-label={n.title}
                  checked={node.dependencies.includes(n.id)}
                  disabled={
                    node.condition?.node === n.id ||
                    (!node.dependencies.includes(n.id) && wouldCycle(quest.definition.nodes, n.id, node.id))
                  }
                  onCheckedChange={(checked) => setDependency(node, n.id, checked)}
                />
                <Label htmlFor={`goal-dependency-${n.id}`}>{n.title}</Label>
              </div>
            ))}
        </div>
        <p className="hint">
          也可从前置目标右侧的「添加后续目标」端口，拖到此目标左侧。
        </p>
      </div>
      <div className="mb-4 flex items-center gap-2">
        <Checkbox
          id="goal-optional"
          checked={node.optional ?? false}
          onCheckedChange={(checked) => update({ ...node, optional: checked })}
        />
        <Label htmlFor="goal-optional">可选目标</Label>
      </div>
      <div className="field">
        <Label htmlFor="goal-condition">仅在特定选择后启用</Label>
        <SelectField
          id="goal-condition"
          value={
            node.condition
              ? `${node.condition.node}|${node.condition.choice}`
              : ""
          }
          onValueChange={(selectedValue) => {
            const next = { ...node };
            if (selectedValue) {
              const [parent, choice] = selectedValue.split("|");
              next.condition = { node: parent, choice };
            } else delete next.condition;
            update(next);
          }}
        >
          <option value="">不限制分支选择</option>
          {quest.definition.nodes
            .filter((n) => node.dependencies.includes(n.id))
            .flatMap((n) =>
              (n.choices ?? []).map((c) => (
                <option
                  key={`${n.id}|${c.id}`}
                  value={`${n.id}|${c.id}`}
                >
                  {n.title} → {c.label}
                </option>
              )),
            )}
        </SelectField>
      </div>
      <div className="field">
        <Label>玩家可选分支</Label>
        {node.choices?.map((c, i) => (
          <div className="flex gap-1 mb-2" key={c.id}>
            <Input
              aria-label={`分支选项 ${i + 1}`}
              value={c.label}
              onChange={(e) =>
                update({
                  ...node,
                  choices: node.choices?.map((old, j) =>
                    i === j
                      ? { ...old, label: e.target.value }
                      : old,
                  ),
                })
              }
            />
            <Button
              variant="ghost"
              size="icon-sm"
              aria-label="删除目标分支"
              disabled={quest.definition.nodes.some(
                (goal) => goal.condition?.node === node.id && goal.condition.choice === c.id,
              )}
              onClick={() =>
                update({
                  ...node,
                  choices: node.choices?.filter((_, j) => i !== j),
                })
              }
            >
              <Trash2 />
            </Button>
          </div>
        ))}
        <Button
          variant="outline"
          size="sm"
          onClick={() =>
            update({
              ...node,
              choices: [
                ...(node.choices ?? []),
                { id: newId("option"), label: "新选项" },
              ],
            })
          }
        >
          <Plus />
          添加选项
        </Button>
      </div>
    </>
  );
}
