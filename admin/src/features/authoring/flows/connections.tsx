import { SelectField } from "@/components/select-field";
import { Button } from "@/components/ui/button";
import { ConditionEditor } from "@/features/authoring/conditions/condition-editor";
import {
  nodeTitle,
  type Catalog,
  type Flow,
  type Route,
  type StoryNode,
} from "@/features/authoring/model";
import { Plus, Trash2 } from "lucide-react";
export function NodeTarget({
  value,
  onChange,
  nodes,
  exclude,
  optional = false,
  label = "后续节点",
}: {
  value: string;
  onChange: (v: string) => void;
  nodes: StoryNode[];
  exclude?: string;
  optional?: boolean;
  label?: string;
}) {
  return (
    <SelectField
      aria-label={label}
      value={value}
      onValueChange={(selectedValue) => onChange(selectedValue)}
    >
      <option value="" disabled={!optional}>
        {optional ? "无" : "选择节点"}
      </option>
      {nodes
        .filter((n) => n.id !== exclude)
        .map((n, i) => (
          <option key={n.id} value={n.id}>
            {i + 1}. {nodeTitle(n)}
          </option>
        ))}
    </SelectField>
  );
}

export function RoutesEditor({
  routes,
  onChange,
  flow,
  catalog,
  exclude,
}: {
  routes: Route[];
  onChange: (v: Route[]) => void;
  flow: Flow;
  catalog: Catalog;
  exclude?: string;
}) {
  return (
    <div className="space-y-3">
      {routes.map((r, i) => (
        <section key={i} className="route-card">
          <div className="flex justify-between items-center mb-2">
            <strong className="text-xs">
              {i === routes.length - 1 ? "否则（兜底）" : `条件 ${i + 1}`}
            </strong>
            {i < routes.length - 1 && (
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label="移除分支"
                onClick={() => onChange(routes.filter((_, j) => i !== j))}
              >
                <Trash2 />
              </Button>
            )}
          </div>
          {i < routes.length - 1 && (
            <ConditionEditor
              value={r.when}
              onChange={(when) =>
                onChange(
                  routes.map((old, j) => (i === j ? { ...old, when } : old)),
                )
              }
              catalog={catalog}
              flow={flow}
            />
          )}
          <NodeTarget
            nodes={flow.content.nodes}
            exclude={exclude}
            value={r.node}
            onChange={(node) =>
              onChange(
                routes.map((old, j) => (i === j ? { ...old, node } : old)),
              )
            }
          />
        </section>
      ))}
      <Button
        variant="outline"
        size="sm"
        disabled={routes.length >= 30}
        onClick={() =>
          onChange([
            ...routes.slice(0, -1),
            {
              when: {
                chapter: {
                  id: catalog.chapters[0]?.id ?? "",
                  status: "complete",
                },
              },
              node: "",
            },
            routes[routes.length - 1] ?? { when: { always: true }, node: "" },
          ])
        }
      >
        <Plus />
        添加条件分支
      </Button>
    </div>
  );
}
