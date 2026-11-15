import { SelectField } from "@/components/select-field";
import { Label } from "@/components/ui/label";
import { ConditionEditor } from "@/features/authoring/conditions/condition-editor";
import {
  type Catalog,
  type Flow,
  type StoryNode,
} from "@/features/authoring/model";
import { NodeTarget } from "../connections";
export function UnlockFields({
  node,
  flow,
  catalog,
  onChange,
}: {
  node: Extract<StoryNode, { type: "unlock_building" }>;
  flow: Flow;
  catalog: Catalog;
  onChange: (node: StoryNode) => void;
}) {
  return (
    <>
      <div className="field">
        <Label htmlFor="node-building">解锁建筑</Label>
        <SelectField
          id="node-building"
          value={node.building_id}
          onValueChange={(selectedValue) =>
            onChange({ ...node, building_id: selectedValue })
          }
        >
          {catalog.buildings
            .filter((b) => b.has_interior)
            .map((b) => (
              <option key={b.id} value={b.id}>
                {b.label}
              </option>
            ))}
        </SelectField>
      </div>
      <p className="hint mb-3">
        解锁必须满足真实完成证据，不能无条件授予进度。
      </p>
      <ConditionEditor
        value={node.when}
        catalog={catalog}
        flow={flow}
        onChange={(when) => onChange({ ...node, when })}
      />
      <NodeTarget
        value={node.next}
        nodes={flow.content.nodes}
        exclude={node.id}
        onChange={(next) => onChange({ ...node, next })}
      />
    </>
  );
}
