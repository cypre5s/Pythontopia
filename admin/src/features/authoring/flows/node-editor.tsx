import { Button } from "@/components/ui/button";
import {
  nodeTitle,
  type Catalog,
  type Flow,
  type StoryNode,
} from "@/features/authoring/model";
import { Trash2 } from "lucide-react";
import { RoutesEditor } from "./connections";
import { ActionFields } from "./nodes/action";
import { ActivityFields } from "./nodes/activity";
import { DialogueFields } from "./nodes/dialogue";
import { RandomFields } from "./nodes/random";
import { UnlockFields } from "./nodes/unlock_building";
export function NodeEditor({
  node,
  flow,
  catalog,
  onChange,
  onFlowChange,
  onDelete,
}: {
  node: StoryNode;
  flow: Flow;
  catalog: Catalog;
  onChange: (n: StoryNode) => void;
  onFlowChange: (f: Flow) => void;
  onDelete: () => void;
}) {
  return (
    <div className="node-properties">
      <div className="flex justify-between items-center gap-3 mb-4">
        <h3
          className="line-clamp-2 font-semibold"
          title={nodeTitle(node, catalog, flow)}
        >
          {nodeTitle(node, catalog, flow)}
        </h3>
        <Button
          variant="ghost"
          size="icon-sm"
          aria-label="删除节点"
          onClick={onDelete}
        >
          <Trash2 />
        </Button>
      </div>
      {node.type === "dialogue" ? (
        <DialogueFields
          node={node}
          flow={flow}
          catalog={catalog}
          onChange={onChange}
        />
      ) : node.type === "branch" ? (
        <RoutesEditor
          routes={node.routes}
          onChange={(routes) => onChange({ ...node, routes })}
          flow={flow}
          catalog={catalog}
          exclude={node.id}
        />
      ) : node.type === "activity" ? (
        <ActivityFields
          node={node}
          flow={flow}
          catalog={catalog}
          onChange={onChange}
          onFlowChange={onFlowChange}
        />
      ) : node.type === "action" ? (
        <ActionFields
          node={node}
          flow={flow}
          catalog={catalog}
          onChange={onChange}
        />
      ) : node.type === "unlock_building" ? (
        <UnlockFields
          node={node}
          flow={flow}
          catalog={catalog}
          onChange={onChange}
        />
      ) : node.type === "random" ? (
        <RandomFields node={node} flow={flow} onChange={onChange} />
      ) : (
        <p className="text-sm text-muted-foreground">
          到达此节点后结束本次交互。玩家下次触发入口时会重新判断条件。
        </p>
      )}
    </div>
  );
}
