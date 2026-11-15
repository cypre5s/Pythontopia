import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ConditionEditor } from "@/features/authoring/conditions/condition-editor";
import type { Authoring, Flow } from "@/features/authoring/model";
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
type BuildingRule = Authoring["configuration"]["buildings"][string];
export function BuildingProperties({
  building,
  data,
  buildingRule,
  updateBuilding,
}: {
  building: Authoring["catalog"]["buildings"][number];
  data: Authoring;
  buildingRule: BuildingRule;
  updateBuilding: (id: string, value: BuildingRule) => void;
}) {
  return building.has_interior ? (
    <div className="space-y-4">
      <Label className="gap-2">
        <Checkbox
          checked={buildingRule.enabled}
          onCheckedChange={(enabled) =>
            updateBuilding(building.id, { ...buildingRule, enabled })
          }
        />
        允许进入
      </Label>
      <Label className="gap-2">
        <Checkbox
          checked={buildingRule.default_unlocked}
          onCheckedChange={(default_unlocked) =>
            updateBuilding(building.id, { ...buildingRule, default_unlocked })
          }
        />
        默认开放
      </Label>
      <div className="field">
        <Label htmlFor="building-message">未开放提示</Label>
        <Input
          id="building-message"
          value={buildingRule.blocked_message}
          onChange={(event) =>
            updateBuilding(building.id, {
              ...buildingRule,
              blocked_message: event.target.value,
            })
          }
        />
      </div>
      <Label className="gap-2">
        <Checkbox
          checked={Boolean(buildingRule.when)}
          onCheckedChange={(conditional) => {
            const next = { ...buildingRule };
            if (conditional) next.when = { always: true };
            else delete next.when;
            updateBuilding(building.id, next);
          }}
        />
        满足条件时开放
      </Label>
      {buildingRule.when && (
        <ConditionEditor
          value={buildingRule.when}
          catalog={data.catalog}
          flow={emptyFlow}
          onChange={(when) =>
            updateBuilding(building.id, { ...buildingRule, when })
          }
        />
      )}
    </div>
  ) : (
    <p className="hint">此建筑没有可用室内。</p>
  );
}
