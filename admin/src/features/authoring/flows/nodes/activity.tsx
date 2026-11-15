import { SelectField } from "@/components/select-field";
import { Label } from "@/components/ui/label";
import {
  type Catalog,
  type Flow,
  type StoryNode,
} from "@/features/authoring/model";
import { NodeTarget } from "../connections";
export function ActivityFields({
  node,
  flow,
  catalog,
  onChange,
  onFlowChange,
}: {
  node: Extract<StoryNode, { type: "activity" }>;
  flow: Flow;
  catalog: Catalog;
  onChange: (node: StoryNode) => void;
  onFlowChange: (flow: Flow) => void;
}) {
  const goals = catalog.quests.find((q) => q.id === flow.quest_id)?.nodes ?? [];
  return (
    <>
      <div className="field">
        <Label htmlFor="node-activity">学习活动</Label>
        <SelectField
          id="node-activity"
          value={flow.content.bindings[node.binding]?.activity_id ?? ""}
          onValueChange={(selectedValue) =>
            onFlowChange({
              ...flow,
              content: {
                ...flow.content,
                bindings: {
                  ...flow.content.bindings,
                  [node.binding]: {
                    ...flow.content.bindings[node.binding],
                    activity_id: selectedValue,
                  },
                },
              },
            })
          }
        >
          <option value="">选择活动</option>
          {catalog.activities.map((a) => (
            <option key={a.id} value={a.id}>
              {a.title}
            </option>
          ))}
        </SelectField>
      </div>
      {flow.quest_id && (
        <div className="field">
          <Label htmlFor="node-goal">任务目标</Label>
          <SelectField
            id="node-goal"
            value={flow.content.bindings[node.binding]?.goal_id ?? ""}
            onValueChange={(selectedValue) =>
              onFlowChange({
                ...flow,
                content: {
                  ...flow.content,
                  bindings: {
                    ...flow.content.bindings,
                    [node.binding]: {
                      ...flow.content.bindings[node.binding],
                      goal_id: selectedValue,
                    },
                  },
                },
              })
            }
          >
            <option value="">选择目标</option>
            {goals
              .filter((g) =>
                [
                  "STUDY_LESSON",
                  "COMPLETE_PRACTICE",
                  "PASS_ASSESSMENT",
                ].includes(g.type),
              )
              .map((g) => (
                <option key={g.id} value={g.id}>
                  {g.title ?? g.type}
                </option>
              ))}
          </SelectField>
        </div>
      )}
      <p className="hint mb-4">
        只有服务端验证真实完成后才走“完成”分支。关闭活动走“暂停”分支。
      </p>
      {(["completed", "suspended", "unavailable"] as const).map((key) => (
        <div className="field" key={key}>
          <Label>
            {
              {
                completed: "真实完成后",
                suspended: "暂停后",
                unavailable: "活动不可用时",
              }[key]
            }
          </Label>
          <NodeTarget
            label={key}
            value={node.returns[key]}
            nodes={flow.content.nodes}
            exclude={node.id}
            onChange={(next) =>
              onChange({
                ...node,
                returns: { ...node.returns, [key]: next },
              })
            }
          />
        </div>
      ))}
    </>
  );
}
