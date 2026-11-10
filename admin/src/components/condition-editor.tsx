import { SelectField } from "@/components/select-field";
import { Plus, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import type { Catalog, Condition, Flow } from "@/lib/authoring";
const labels: Record<string, string> = {
  always: "总是",
  all: "全部满足（且）",
  any: "任一满足（或）",
  not: "条件不满足",
  chapter: "章节状态",
  quest: "任务状态",
  goal: "任务目标状态",
  all_quests_complete: "所有任务已完成",
  goal_complete: "当前任务目标已完成",
  goal_ready: "当前任务目标已就绪",
  activity_started: "已开始活动",
  activity_completed: "已真实完成活动",
  building_unlocked: "建筑已解锁",
  assessment_score: "测验成绩",
};
export function ConditionEditor({
  value,
  onChange,
  catalog,
  flow,
  depth = 0,
}: {
  value: Condition;
  onChange: (v: Condition) => void;
  catalog: Catalog;
  flow: Flow;
  depth?: number;
}) {
  const kind = Object.keys(value)[0];
  const goals = catalog.quests.find((q) => q.id === flow.quest_id)?.nodes ?? [];
  function changeKind(key: string) {
    const activity = catalog.activities[0]?.id ?? "",
      quest = catalog.quests[0]?.id ?? "";
    const next: Record<string, Condition> = {
      always: { always: true },
      all: { all: [{ always: true }] },
      any: { any: [{ always: true }] },
      not: { not: { always: true } },
      chapter: {
        chapter: { id: catalog.chapters[0]?.id ?? "", status: "complete" },
      },
      quest: { quest: { id: quest, status: "complete" } },
      goal: {
        goal: {
          quest_id: quest,
          id: catalog.quests[0]?.nodes?.[0]?.id ?? "",
          status: "complete",
        },
      },
      all_quests_complete: { all_quests_complete: true },
      goal_complete: { goal_complete: goals[0]?.id ?? "" },
      goal_ready: { goal_ready: goals[0]?.id ?? "" },
      activity_started: {
        activity_started: Object.keys(flow.content.bindings)[0] ?? "",
      },
      activity_completed: { activity_completed: activity },
      building_unlocked: { building_unlocked: catalog.buildings[0]?.id ?? "" },
      assessment_score: {
        assessment_score: { activity_id: activity, min: 60, max: 100 },
      },
    };
    onChange(next[key]);
  }
  return (
    <div className="condition-editor">
      <SelectField
        aria-label="条件类型"
        value={kind}
        onValueChange={(selectedValue) => changeKind(selectedValue)}
      >
        {Object.entries(labels)
          .filter(([k]) => depth < 7 || !["all", "any", "not"].includes(k))
          .map(([key, label]) => (
            <option key={key} value={key}>
              {label}
            </option>
          ))}
      </SelectField>
      {"all" in value || "any" in value ? (
        <div className="condition-children">
          {("all" in value ? value.all : value.any).map((c, i) => {
            const values = "all" in value ? value.all : value.any;
            return (
              <div className="flex items-start gap-1" key={i}>
                <ConditionEditor
                  depth={depth + 1}
                  value={c}
                  catalog={catalog}
                  flow={flow}
                  onChange={(v) =>
                    onChange(
                      "all" in value
                        ? { all: values.map((old, j) => (i === j ? v : old)) }
                        : { any: values.map((old, j) => (i === j ? v : old)) },
                    )
                  }
                />
                <Button
                  variant="ghost"
                  size="icon-sm"
                  aria-label="删除条件"
                  disabled={values.length === 1}
                  onClick={() =>
                    onChange(
                      "all" in value
                        ? { all: values.filter((_, j) => i !== j) }
                        : { any: values.filter((_, j) => i !== j) },
                    )
                  }
                >
                  <X />
                </Button>
              </div>
            );
          })}
          <Button
            variant="outline"
            size="sm"
            disabled={("all" in value ? value.all : value.any).length >= 20}
            onClick={() =>
              onChange(
                "all" in value
                  ? { all: [...value.all, { always: true }] }
                  : { any: [...value.any, { always: true }] },
              )
            }
          >
            <Plus />
            添加条件
          </Button>
        </div>
      ) : "not" in value ? (
        <ConditionEditor
          depth={depth + 1}
          value={value.not}
          onChange={(v) => onChange({ not: v })}
          catalog={catalog}
          flow={flow}
        />
      ) : "chapter" in value ? (
        <>
          <SelectField
            aria-label="条件章节"
            value={value.chapter.id}
            onValueChange={(selectedValue) =>
              onChange({ chapter: { ...value.chapter, id: selectedValue } })
            }
          >
            {catalog.chapters.map((c) => (
              <option key={c.id} value={c.id}>
                {c.title}
              </option>
            ))}
          </SelectField>
          <SelectField
            aria-label="章节状态"
            value={value.chapter.status}
            onValueChange={(selectedValue) =>
              onChange({
                chapter: {
                  ...value.chapter,
                  status: selectedValue as "complete" | "available",
                },
              })
            }
          >
            <option value="complete">已完成</option>
            <option value="available">已开放</option>
          </SelectField>
        </>
      ) : "quest" in value ? (
        <>
          <SelectField
            aria-label="条件任务"
            value={value.quest.id}
            onValueChange={(selectedValue) =>
              onChange({ quest: { ...value.quest, id: selectedValue } })
            }
          >
            {catalog.quests.map((q) => (
              <option key={q.id} value={q.id}>
                {q.title}
              </option>
            ))}
          </SelectField>
          <SelectField
            aria-label="任务状态"
            value={value.quest.status}
            onValueChange={(selectedValue) =>
              onChange({
                quest: {
                  ...value.quest,
                  status: selectedValue as "active" | "complete",
                },
              })
            }
          >
            <option value="complete">已完成</option>
            <option value="active">进行中</option>
          </SelectField>
        </>
      ) : "goal" in value ? (
        <>
          <SelectField
            aria-label="目标所属任务"
            value={value.goal.quest_id}
            onValueChange={(selectedValue) =>
              onChange({
                goal: {
                  ...value.goal,
                  quest_id: selectedValue,
                  id:
                    catalog.quests.find((q) => q.id === selectedValue)
                      ?.nodes?.[0]?.id ?? "",
                },
              })
            }
          >
            {catalog.quests.map((q) => (
              <option key={q.id} value={q.id}>
                {q.title}
              </option>
            ))}
          </SelectField>
          <SelectField
            aria-label="条件目标"
            value={value.goal.id}
            onValueChange={(selectedValue) =>
              onChange({ goal: { ...value.goal, id: selectedValue } })
            }
          >
            {catalog.quests
              .find((q) => q.id === value.goal.quest_id)
              ?.nodes?.map((n) => (
                <option key={n.id} value={n.id}>
                  {n.title ?? n.type}
                </option>
              ))}
          </SelectField>
          <SelectField
            aria-label="目标状态"
            value={value.goal.status}
            onValueChange={(selectedValue) =>
              onChange({
                goal: {
                  ...value.goal,
                  status: selectedValue as
                    "ready" | "complete" | "locked" | "skipped",
                },
              })
            }
          >
            <option value="ready">就绪</option>
            <option value="complete">完成</option>
            <option value="locked">未解锁</option>
            <option value="skipped">已跳过</option>
          </SelectField>
        </>
      ) : "assessment_score" in value ? (
        <>
          <SelectField
            aria-label="测验活动"
            value={value.assessment_score.activity_id}
            onValueChange={(selectedValue) =>
              onChange({
                assessment_score: {
                  ...value.assessment_score,
                  activity_id: selectedValue,
                },
              })
            }
          >
            {catalog.activities
              .filter((a) => !a.kind || a.kind === "assessment")
              .map((a) => (
                <option key={a.id} value={a.id}>
                  {a.title}
                </option>
              ))}
          </SelectField>
          <div className="grid grid-cols-2 gap-2">
            <Input
              aria-label="最低分数"
              type="number"
              min={0}
              max={100}
              value={value.assessment_score.min ?? 0}
              onChange={(e) =>
                onChange({
                  assessment_score: {
                    ...value.assessment_score,
                    min: Number(e.target.value),
                  },
                })
              }
            />
            <Input
              aria-label="最高分数"
              type="number"
              min={0}
              max={100}
              value={value.assessment_score.max ?? 100}
              onChange={(e) =>
                onChange({
                  assessment_score: {
                    ...value.assessment_score,
                    max: Number(e.target.value),
                  },
                })
              }
            />
          </div>
        </>
      ) : "activity_completed" in value ? (
        <SelectField
          aria-label="已完成活动"
          value={value.activity_completed}
          onValueChange={(selectedValue) => onChange({ activity_completed: selectedValue })}
        >
          {catalog.activities.map((a) => (
            <option key={a.id} value={a.id}>
              {a.title}
            </option>
          ))}
        </SelectField>
      ) : "activity_started" in value ? (
        <SelectField
          aria-label="已开始活动"
          value={value.activity_started}
          onValueChange={(selectedValue) => onChange({ activity_started: selectedValue })}
        >
          {Object.entries(flow.content.bindings).map(([key, b]) => (
            <option key={key} value={key}>
              {catalog.activities.find((a) => a.id === b.activity_id)?.title ??
                key}
            </option>
          ))}
        </SelectField>
      ) : "building_unlocked" in value ? (
        <SelectField
          aria-label="已解锁建筑"
          value={value.building_unlocked}
          onValueChange={(selectedValue) => onChange({ building_unlocked: selectedValue })}
        >
          {catalog.buildings.map((b) => (
            <option key={b.id} value={b.id}>
              {b.label}
            </option>
          ))}
        </SelectField>
      ) : "goal_complete" in value || "goal_ready" in value ? (
        <SelectField
          aria-label="当前任务目标"
          value={
            "goal_complete" in value ? value.goal_complete : value.goal_ready
          }
          onValueChange={(selectedValue) =>
            onChange(
              "goal_complete" in value
                ? { goal_complete: selectedValue }
                : { goal_ready: selectedValue },
            )
          }
        >
          {goals.map((g) => (
            <option key={g.id} value={g.id}>
              {g.title ?? g.type}
            </option>
          ))}
        </SelectField>
      ) : null}
    </div>
  );
}
