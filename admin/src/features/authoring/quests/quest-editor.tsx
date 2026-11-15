import { Empty } from "@/components/common";
import { SelectField } from "@/components/select-field";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { FlowCanvas } from "@/features/authoring/graph/flow-canvas";
import type {
  Authoring,
  Point,
  Quest,
  QuestNode,
  QuestNodeType,
} from "@/features/authoring/model";
import {
  AuthoringInspector,
  AuthoringWorkspace,
} from "@/features/authoring/workspace";
import { newId } from "@/lib/id";
import { Plus, SlidersHorizontal, Trash2 } from "lucide-react";
import { useMemo, useState, type MouseEvent } from "react";
import { GoalProperties } from "./goal-properties";
import {
  dependencyPort,
  describeGoals,
  emptyGoals,
  emptyPositions,
  goalKinds,
  goalPalette,
  newDependency,
  wouldCycle,
} from "./graph-model";
import { QuestSettings } from "./quest-settings";

type Inspector =
  | { kind: "settings"; anchor: HTMLElement | Point }
  | {
      kind: "goal";
      id: string;
      anchor: HTMLElement | Point;
      trigger: HTMLElement;
    };

export function QuestEditor({
  data,
  onChange,
  focusTarget,
}: {
  data: Authoring;
  onChange: (v: Authoring) => void;
  focusTarget?: { questId?: string; goalId?: string };
}) {
  const [selected, setSelected] = useState(
      focusTarget?.questId ?? data.quests?.[0]?.id ?? "",
    ),
    [nodeId, setNodeId] = useState<string | null>(focusTarget?.goalId ?? null),
    [graphFocus] = useState(() =>
      focusTarget?.goalId
        ? { kind: "node" as const, id: focusTarget.goalId }
        : undefined,
    ),
    [inspector, setInspector] = useState<Inspector | null>(null),
    [layoutEpoch, setLayoutEpoch] = useState(0),
    [graphIssue, setGraphIssue] = useState("");
  const quest = data.quests.find((q) => q.id === selected) ?? data.quests[0],
    goals = quest?.definition.nodes ?? emptyGoals,
    node = goals.find((n) => n.id === nodeId),
    inspectedGoal =
      inspector?.kind === "goal"
        ? goals.find((n) => n.id === inspector.id)
        : undefined,
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
      configuration: nextPositions
        ? withLayout(q.id, nextPositions)
        : data.configuration,
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
    setInspector({
      kind: "settings",
      anchor: { x: anchor.left, y: anchor.bottom },
    });
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
      dependencies: add
        ? [...goal.dependencies, parent]
        : goal.dependencies.filter((id) => id !== parent),
    });
  }
  function removeGoal(goal: QuestNode) {
    if (!quest) return;
    if (goals.some((other) => other.condition?.node === goal.id)) {
      setGraphIssue("请先移除依赖此目标的分支条件。");
      return;
    }
    if (
      !window.confirm(
        "删除目标及相连依赖？引用此目标的流程检查点需要重新连接。",
      )
    )
      return;
    const nextPositions = { ...positions };
    delete nextPositions[goal.id];
    replace(
      {
        ...quest,
        definition: {
          ...quest.definition,
          nodes: goals
            .filter((other) => other.id !== goal.id)
            .map((other) =>
              other.dependencies.includes(goal.id)
                ? {
                    ...other,
                    dependencies: other.dependencies.filter(
                      (id) => id !== goal.id,
                    ),
                  }
                : other,
            ),
        },
      },
      nextPositions,
    );
    setNodeId(null);
    setInspector(null);
    setGraphIssue("");
  }
  function selectGoal(id: string | null) {
    setNodeId(id);
    setGraphIssue("");
    setInspector((current) =>
      current?.kind === "goal" && current.id !== id ? null : current,
    );
  }
  function inspectGoal(
    id: string,
    anchor: HTMLElement | Point,
    trigger: HTMLElement,
  ) {
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
          <Button
            variant="outline"
            onClick={(event) =>
              setInspector({ kind: "settings", anchor: event.currentTarget })
            }
          >
            <SlidersHorizontal />
            任务设置
          </Button>
          {node ? (
            <Button
              variant="ghost"
              onClick={(event) =>
                inspectGoal(node.id, event.currentTarget, event.currentTarget)
              }
            >
              编辑目标属性
            </Button>
          ) : null}
          <Button
            variant="ghost"
            size="icon-sm"
            aria-label="删除任务"
            onClick={() => {
              if (
                !window.confirm(
                  `从草稿中删除「${quest.title}」？保存前可查看受影响的进度。`,
                )
              )
                return;
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
      <AuthoringWorkspace
        toolbar={toolbar}
        tools={<p className="hint">新建任务后可以添加目标。</p>}
      >
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
      canConnect={(source, port, target) =>
        source.kind === "node" &&
        port === newDependency &&
        !goals
          .find((goal) => goal.id === target)
          ?.dependencies.includes(source.id) &&
        !wouldCycle(goals, source.id, target)
      }
      onConnect={(source, port, target) => {
        if (source.kind !== "node") return;
        const goal = goals.find((item) =>
          target ? item.id === target : dependencyPort(item.id) === port,
        );
        if (goal) setDependency(goal, source.id, Boolean(target));
      }}
      onMove={(id, point) =>
        onChange({
          ...data,
          configuration: withLayout(quest.id, { ...positions, [id]: point }),
        })
      }
      onAutoArrange={() => {
        onChange({ ...data, configuration: withLayout(quest.id, {}) });
        setInspector(null);
        setLayoutEpoch((value) => value + 1);
      }}
      renderWorkspace={({ palette, canvas }) => (
        <AuthoringWorkspace
          className="authoring-flow-workspace"
          tools={palette}
          toolbar={toolbar}
        >
          {canvas}
          {graphIssue && (
            <Badge
              role="status"
              variant="destructive"
              className="absolute bottom-3 left-1/2 z-20 h-auto max-w-[calc(100%-1.5rem)] -translate-x-1/2 whitespace-normal bg-background px-3 py-1 shadow-sm"
            >
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
            returnFocus={
              inspector?.kind === "goal" ? inspector.trigger : undefined
            }
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
