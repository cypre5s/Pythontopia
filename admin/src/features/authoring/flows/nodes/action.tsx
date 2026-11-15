import { SelectField } from "@/components/select-field";
import { Label } from "@/components/ui/label";
import {
  workspaceNames,
  type Catalog,
  type Flow,
  type StoryNode,
} from "@/features/authoring/model";
import { NodeTarget } from "../connections";
export function ActionFields({
  node,
  flow,
  catalog,
  onChange,
}: {
  node: Extract<StoryNode, { type: "action" }>;
  flow: Flow;
  catalog: Catalog;
  onChange: (node: StoryNode) => void;
}) {
  return (
    <>
      <div className="field">
        <Label htmlFor="node-action">操作类型</Label>
        <SelectField
          id="node-action"
          value={node.action}
          onValueChange={(selectedValue) =>
            onChange(
              selectedValue === "enter_building"
                ? {
                    id: node.id,
                    type: "action",
                    action: "enter_building",
                    building_id:
                      catalog.buildings.find((b) => b.has_interior)?.id ?? "",
                    next: node.next,
                  }
                : {
                    id: node.id,
                    type: "action",
                    action: "open_workspace",
                    workspace: "library",
                    next: node.next,
                  },
            )
          }
        >
          <option value="open_workspace">打开工作区</option>
          <option value="enter_building">进入建筑</option>
        </SelectField>
      </div>
      {node.action === "enter_building" ? (
        <div className="field">
          <Label htmlFor="node-enter-building">目标建筑</Label>
          <SelectField
            id="node-enter-building"
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
          <p className="hint">使用系统维护的入口与落点，并检查建筑开放权限。</p>
        </div>
      ) : (
        <div className="field">
          <Label htmlFor="node-workspace">打开页面</Label>
          <SelectField
            id="node-workspace"
            value={node.workspace}
            onValueChange={(selectedValue) =>
              onChange({
                ...node,
                workspace: selectedValue as "library" | "journal" | "classroom",
              })
            }
          >
            {Object.entries(workspaceNames).map(([key, name]) => (
              <option key={key} value={key}>
                {name}
              </option>
            ))}
          </SelectField>
        </div>
      )}
      <details>
        <summary className="cursor-pointer text-sm font-medium">
          后续连接
        </summary>
        <div className="mt-2">
          <NodeTarget
            value={node.next}
            nodes={flow.content.nodes}
            exclude={node.id}
            onChange={(next) => onChange({ ...node, next })}
          />
        </div>
      </details>
    </>
  );
}
