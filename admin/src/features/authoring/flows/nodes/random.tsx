import { Button } from "@/components/ui/button";
import { type Flow, type StoryNode } from "@/features/authoring/model";
import { Trash2 } from "lucide-react";
import { NodeTarget } from "../connections";
export function RandomFields({
  node,
  flow,
  onChange,
}: {
  node: Extract<StoryNode, { type: "random" }>;
  flow: Flow;

  onChange: (node: StoryNode) => void;
}) {
  return (
    <div className="space-y-3">
      {node.next.map((target, i) => (
        <div className="flex gap-1" key={i}>
          <NodeTarget
            value={target}
            nodes={flow.content.nodes}
            exclude={node.id}
            onChange={(next) =>
              onChange({
                ...node,
                next: node.next.map((old, j) => (i === j ? next : old)),
              })
            }
          />
          <Button
            variant="ghost"
            size="icon-sm"
            aria-label="删除随机分支"
            onClick={() =>
              onChange({
                ...node,
                next: node.next.filter((_, j) => i !== j),
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
        disabled={node.next.length >= 20}
        onClick={() => onChange({ ...node, next: [...node.next, ""] })}
      >
        添加随机分支
      </Button>
    </div>
  );
}
