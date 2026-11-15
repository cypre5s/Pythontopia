import { Button } from "@/components/ui/button";
import {
  Handle,
  Position,
  useUpdateNodeInternals,
  type NodeProps,
} from "@xyflow/react";

import { Ellipsis } from "lucide-react";
import { createContext, memo, useContext, useEffect } from "react";

import { kinds, targetHandle, type GraphNode } from "./canvas-model";
export const InspectContext = createContext<
  ((id: string, trigger: HTMLElement) => void) | undefined
>(undefined);

export const FlowCard = memo(function FlowCard({
  id,
  data,
  selected,
  isConnectable,
}: NodeProps<GraphNode>) {
  const Icon = data.appearance?.icon ?? kinds[data.kind].icon;
  const inspect = useContext(InspectContext);
  const updateNodeInternals = useUpdateNodeInternals();
  const ports = data.ports.map((port) => port.id).join("\0");
  useEffect(() => updateNodeInternals(id), [id, ports, updateNodeInternals]);
  return (
    <div
      className={`flow-card ${selected ? "is-selected" : ""}`}
      data-kind={data.appearance?.tone ?? data.kind}
    >
      {data.kind !== "entry" && (
        <Handle
          type="target"
          position={Position.Left}
          id={targetHandle}
          isConnectable={isConnectable}
          aria-label="输入"
        />
      )}
      <header>
        <Icon size={16} aria-hidden="true" />
        <span>{data.caption}</span>
        {inspect && selected && (
          <Button
            type="button"
            size="icon-xs"
            variant="ghost"
            className="flow-card-inspect nodrag nopan"
            aria-label="编辑属性"
            title="编辑属性（Shift+F10）"
            onClick={(event) => {
              event.stopPropagation();
              inspect(id, event.currentTarget);
            }}
          >
            <Ellipsis />
          </Button>
        )}
      </header>
      <p className="flow-card-title">{data.title}</p>
      {data.ports.map((port) => (
        <div className="flow-card-port" key={port.id} title={port.label}>
          <span>{port.label}</span>
          <Handle
            id={port.id}
            type="source"
            position={Position.Right}
            isConnectable={isConnectable}
            aria-label={`输出：${port.label}`}
          />
        </div>
      ))}
    </div>
  );
});

export const nodeTypes = { flowCard: FlowCard };
