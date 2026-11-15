import { GroupedPicker } from "@/components/grouped-picker";
import { SelectField } from "@/components/select-field";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { Authoring, Quest, QuestNode } from "@/features/authoring/model";
import { npcGroups } from "@/features/authoring/pickers";
import { newId } from "@/lib/id";
import { Plus, Trash2 } from "lucide-react";
import { goalKinds, wouldCycle } from "./graph-model";
export function GoalProperties({
  data,
  quest,
  node,
  update,
  issue,
  setDependency,
  removeGoal,
}: {
  data: Authoring;
  quest: Quest;
  node: QuestNode;
  update: (node: QuestNode) => void;
  issue: string;
  setDependency: (node: QuestNode, parent: string, add: boolean) => void;
  removeGoal: (node: QuestNode) => void;
}) {
  const objectOptions = new Map<string, string>();
  const isMapGoal =
    node.type === "REACH_LOCATION" || node.type === "INTERACT_OBJECT";
  if (isMapGoal) {
    for (const map of data.catalog.maps) {
      for (const object of map.objects)
        objectOptions.set(object.id, `${map.label} · ${object.label}`);
    }
    for (const interaction of data.configuration.world.interactions ?? []) {
      if (!objectOptions.has(interaction.id))
        objectOptions.set(interaction.id, `课程交互 · ${interaction.label}`);
    }
  }
  return (
    <>
      <div className="mb-4 flex items-center justify-between gap-2">
        <Badge variant="secondary">{goalKinds[node.type].label}</Badge>
        <Button
          variant="ghost"
          size="icon-sm"
          aria-label="删除任务目标"
          onClick={() => removeGoal(node)}
        >
          <Trash2 />
        </Button>
      </div>
      {issue ? (
        <p role="alert" className="mb-3 text-xs text-destructive">
          {issue}
        </p>
      ) : null}
      <div className="field">
        <Label htmlFor="goal-title">目标标题</Label>
        <Input
          id="goal-title"
          value={node.title}
          onChange={(e) => update({ ...node, title: e.target.value })}
        />
      </div>
      {["TALK_TO_NPC", "PLAY_DIALOGUE", "REPORT_BACK"].includes(node.type) && (
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
            {Array.from(objectOptions, ([id, label]) => (
              <option key={id} value={id}>
                {label}
              </option>
            ))}
          </SelectField>
        </div>
      )}
      {["STUDY_LESSON", "COMPLETE_PRACTICE", "PASS_ASSESSMENT"].includes(
        node.type,
      ) && (
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
            <Label htmlFor="goal-navigation">导航角色（可选）</Label>
            <GroupedPicker
              id="goal-navigation"
              label="导航角色（可选）"
              groups={npcGroups(data.catalog)}
              standalone={[{ value: "", label: "无导航提示" }]}
              value={node.navigation_target ?? ""}
              onChange={(selectedValue) => {
                const next = { ...node };
                if (selectedValue) next.navigation_target = selectedValue;
                else delete next.navigation_target;
                update(next);
              }}
            />
          </div>
        </>
      )}
      {node.type === "PLAY_DIALOGUE" && (
        <div className="field">
          <Label htmlFor="goal-dialogue">完成此目标的剧情流程</Label>
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
                <option key={f.id} value={`story:${f.id}:${node.id}`}>
                  {f.title}
                </option>
              ))}
          </SelectField>
          <p className="hint">同时在对应对白节点里选择此目标作为交谈检查点。</p>
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
                    (!node.dependencies.includes(n.id) &&
                      wouldCycle(quest.definition.nodes, n.id, node.id))
                  }
                  onCheckedChange={(checked) =>
                    setDependency(node, n.id, checked)
                  }
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
                <option key={`${n.id}|${c.id}`} value={`${n.id}|${c.id}`}>
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
                    i === j ? { ...old, label: e.target.value } : old,
                  ),
                })
              }
            />
            <Button
              variant="ghost"
              size="icon-sm"
              aria-label="删除目标分支"
              disabled={quest.definition.nodes.some(
                (goal) =>
                  goal.condition?.node === node.id &&
                  goal.condition.choice === c.id,
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
