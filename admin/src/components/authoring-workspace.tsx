import { useMemo, type ReactNode } from "react";
import { X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Popover, PopoverContent, PopoverDescription, PopoverTitle } from "@/components/ui/popover";
import { cn } from "@/lib/utils";
import type { Point } from "@/lib/authoring";
import "./authoring-workspace.css";

export function AuthoringWorkspace({ tools, toolbar, children, className }: {
  tools: ReactNode;
  toolbar?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <section className={cn("authoring-workspace", className)}>
      <aside className="authoring-tools" aria-label="编辑工具">
        <Card className="min-h-full gap-0 px-2 py-3 ring-0">{tools}</Card>
      </aside>
      <Card className="authoring-stage gap-0 p-0 ring-0">
        {toolbar && (
          <div className="authoring-toolbar" aria-label="工作区操作">
            <Card className="flex-row flex-wrap items-center gap-1.5 rounded-lg p-1.5 shadow-sm ring-0">
              {toolbar}
            </Card>
          </div>
        )}
        <div className="authoring-stage-content">{children}</div>
      </Card>
    </section>
  );
}

export function AuthoringInspector({ anchor, returnFocus, title, description, onClose, children }: {
  anchor: Element | Point | null;
  returnFocus?: HTMLElement;
  title: string;
  description?: string;
  onClose: () => void;
  children: ReactNode;
}) {
  const position = useMemo(() => {
    if (!anchor || anchor instanceof Element) return anchor;
    const { x, y } = anchor;
    return { getBoundingClientRect: () => new DOMRect(x, y, 0, 0) };
  }, [anchor]);
  const focus = returnFocus ?? (anchor instanceof HTMLElement ? anchor : undefined);

  return (
    <Popover open={Boolean(anchor)} onOpenChange={(open) => { if (!open) onClose(); }}>
      <PopoverContent
        anchor={position}
        side="right"
        align="start"
        sideOffset={10}
        positionMethod="fixed"
        collisionPadding={14}
        finalFocus={() => focus?.isConnected ? focus : true}
        className="authoring-inspector w-[min(25rem,calc(100vw-2rem))] gap-0 p-0 shadow-lg"
      >
        <div className="flex shrink-0 items-center gap-3 border-b px-4 py-2.5">
          <div className="min-w-0 flex-1">
            <PopoverTitle className="font-semibold">{title}</PopoverTitle>
            {description && <PopoverDescription className="mt-0.5 text-xs">{description}</PopoverDescription>}
          </div>
          <Button type="button" size="icon-sm" variant="ghost" aria-label="关闭属性" onClick={onClose}>
            <X />
          </Button>
        </div>
        <div className="authoring-inspector-body p-4">{children}</div>
      </PopoverContent>
    </Popover>
  );
}
