import { SelectField } from "@/components/select-field";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ConditionEditor } from "@/features/authoring/conditions/condition-editor";
import type { Authoring, Flow, Interaction } from "@/features/authoring/model";
import { Trash2 } from "lucide-react";
const emptyFlow: Flow = {
  id: "",
  title: "",
  chapter_id: null,
  quest_id: null,
  content: {
    schema_version: 1,
    mode: "public",
    bindings: {},
    entries: {},
    nodes: [],
  },
};
const regionFields = [
  ["x", "横坐标"],
  ["y", "纵坐标"],
  ["width", "宽度"],
  ["height", "高度"],
] as const;
export function InteractionProperties({
  interaction,
  data,
  boundFlow,
  updateInteraction,
  setFlow,
  removeInteraction,
}: {
  interaction: Interaction;
  data: Authoring;
  boundFlow: Flow | undefined;
  updateInteraction: (value: Interaction) => void;
  setFlow: (id: string) => void;
  removeInteraction: () => void;
}) {
  const region =
    "region" in interaction.target ? interaction.target.region : null;
  return (
    <>
      <div className="field">
        <Label htmlFor="interaction-name">交互名称</Label>
        <Input
          id="interaction-name"
          value={interaction.label}
          onChange={(event) =>
            updateInteraction({ ...interaction, label: event.target.value })
          }
        />
      </div>
      <div className="field">
        <Label htmlFor="interaction-prompt">提示文字</Label>
        <Input
          id="interaction-prompt"
          value={interaction.prompt}
          onChange={(event) =>
            updateInteraction({ ...interaction, prompt: event.target.value })
          }
        />
      </div>
      <div className="field">
        <Label htmlFor="interaction-trigger">触发方式</Label>
        <SelectField
          id="interaction-trigger"
          value={interaction.trigger}
          onValueChange={(value) =>
            updateInteraction({
              ...interaction,
              trigger: value === "enter" ? "enter" : "click",
            })
          }
        >
          <option value="click">点击</option>
          {region && <option value="enter">进入区域</option>}
        </SelectField>
      </div>
      {region && (
        <fieldset className="field">
          <legend>区域位置（格）</legend>
          <div className="grid grid-cols-2 gap-2">
            {regionFields.map(([key, label]) => (
              <label key={key} className="text-xs">
                {label}
                <Input
                  aria-label={key}
                  type="number"
                  min={key === "x" || key === "y" ? 0 : 1}
                  value={region[key]}
                  onChange={(event) =>
                    updateInteraction({
                      ...interaction,
                      target: {
                        region: {
                          ...region,
                          [key]: Number(event.target.value),
                        },
                      },
                    })
                  }
                />
              </label>
            ))}
          </div>
        </fieldset>
      )}
      <div className="field">
        <Label htmlFor="interaction-flow">触发流程</Label>
        <SelectField
          id="interaction-flow"
          value={boundFlow?.id ?? ""}
          onValueChange={setFlow}
        >
          <option value="">尚未绑定</option>
          {data.flows.map((item) => (
            <option key={item.id} value={item.id}>
              {item.title}
            </option>
          ))}
        </SelectField>
        <p className="hint">在流程画布中编辑此入口的条件分支。</p>
      </div>
      <div className="field">
        <Label>生效条件</Label>
        <ConditionEditor
          value={interaction.when ?? { always: true }}
          onChange={(when) => updateInteraction({ ...interaction, when })}
          catalog={data.catalog}
          flow={boundFlow ?? emptyFlow}
        />
      </div>
      <Button variant="ghost" onClick={removeInteraction}>
        <Trash2 />
        删除交互点
      </Button>
    </>
  );
}
