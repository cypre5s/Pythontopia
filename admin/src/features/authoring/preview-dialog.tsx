import { ErrorNotice } from "@/components/common";
import { SelectField } from "@/components/select-field";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { nodeTitle } from "@/features/authoring/model";
import { Play } from "lucide-react";
import type { Document } from "./contracts";
import type { useAuthoringDraft } from "./use-authoring-draft";
import type { useAuthoringPreview } from "./use-authoring-preview";
export function PreviewDialog({
  initial,
  draft,
  state,
}: {
  initial: Document;
  draft: ReturnType<typeof useAuthoringDraft>;
  state: ReturnType<typeof useAuthoringPreview>;
}) {
  const { data, error, busy } = draft;
  const {
    previewOpen,
    setPreviewOpen,
    previewStudent,
    setPreviewStudent,
    previewEntry,
    setPreviewEntry,
    preview,
    runPreview,
  } = state;
  const entries = Array.from(
    new Set(data.flows.flatMap((f) => Object.keys(f.content.entries))),
  );
  const previewFlow = data.flows.find(
    (f) => f.id === preview?.selected_flow_id,
  );

  const entryLabel = (key: string) =>
    key.startsWith("interaction:")
      ? (data.configuration.world.interactions?.find(
          (i) => `interaction:${i.id}` === key,
        )?.label ?? "地图交互")
      : (data.catalog.npcs.find((n) => n.id === key)?.name ?? "角色入口");

  return (
    <Dialog
      open={previewOpen}
      onOpenChange={(v) => {
        if (!busy) setPreviewOpen(v);
      }}
    >
      <DialogContent className="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>预览流程</DialogTitle>
          <DialogDescription>
            依据所选学生在本班的真实记录判断分支。预览不会更新进度、授予解锁或调用
            AI。
          </DialogDescription>
        </DialogHeader>
        <ErrorNotice message={error} />
        <div className="field">
          <Label htmlFor="preview-student">学生</Label>
          <SelectField
            id="preview-student"
            value={previewStudent}
            onValueChange={(selectedValue) =>
              setPreviewStudent(Number(selectedValue))
            }
          >
            <option value={0}>选择学生</option>
            {initial.students?.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </SelectField>
        </div>
        <div className="field">
          <Label htmlFor="preview-entry">入口</Label>
          <SelectField
            id="preview-entry"
            value={previewEntry}
            onValueChange={(selectedValue) => setPreviewEntry(selectedValue)}
          >
            {entries.map((key) => (
              <option key={key} value={key}>
                {entryLabel(key)}
              </option>
            ))}
          </SelectField>
        </div>
        <Button
          disabled={busy || !previewStudent || !previewEntry}
          onClick={() => void runPreview()}
        >
          <Play />
          执行只读预览
        </Button>
        {preview && (
          <div className="border rounded-lg p-4 max-h-64 overflow-y-auto">
            <p className="font-medium mb-3">
              {previewFlow?.title ?? "没有匹配的流程"}
            </p>
            <ol className="list-decimal pl-5 space-y-2">
              {preview.trace.map((step, i) => {
                const n = previewFlow?.content.nodes.find(
                  (n) => n.id === step.node_id,
                );
                return (
                  <li key={i}>
                    {n ? nodeTitle(n, data.catalog, previewFlow) : step.type}
                  </li>
                );
              })}
            </ol>
            <p className="hint mt-3">预览停止：{preview.stopped_at}</p>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
