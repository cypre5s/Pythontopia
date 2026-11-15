import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { Crosshair } from "lucide-react";
import { ResetImpactList } from "./reset-impact";
import type { useAuthoringDraft } from "./use-authoring-draft";
import type { useFlowEditor } from "./use-flow-editor";
export function ValidationFeedback({
  draft,
  editor,
  onValidate: validate,
}: {
  draft: ReturnType<typeof useAuthoringDraft>;
  editor: ReturnType<typeof useFlowEditor>;
  onValidate: (save?: boolean, token?: string) => void;
}) {
  const {
    diagnostics,
    busy,
    resetImpact,
    confirmReset,
    setConfirmReset,
    impactChanged,
  } = draft;
  const { canLocate, locate } = editor;
  return (
    <>
      {" "}
      {diagnostics.length > 0 && (
        <section
          aria-label="校验结果"
          className="mb-4 overflow-hidden rounded-lg border border-destructive/25 bg-destructive/5"
        >
          <p
            role="alert"
            className="border-b border-destructive/15 px-4 py-2.5 text-sm font-medium text-destructive"
          >
            {diagnostics.length} 处需要处理，修改尚未保存。
          </p>
          <ul className="max-h-52 divide-y divide-destructive/15 overflow-auto">
            {diagnostics.map(({ message, path, suggestion, target }, i) => (
              <li
                className="flex items-start justify-between gap-3 px-4 py-2.5 text-sm"
                key={`${path}:${i}`}
              >
                <div>
                  <p>{message}</p>
                  {suggestion && (
                    <p className="mt-1 text-xs text-muted-foreground">
                      {suggestion}
                    </p>
                  )}
                </div>
                {target && canLocate(target) && (
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => locate(target)}
                    disabled={busy}
                  >
                    <Crosshair />
                    定位
                  </Button>
                )}
              </li>
            ))}
          </ul>
        </section>
      )}
      {resetImpact && (
        <section
          aria-label="保存影响"
          className="mb-4 overflow-hidden rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-950"
        >
          <p role="status" className="font-medium">
            保存会重置 {resetImpact.students} 人的相关进度，需确认后才能发布。
          </p>
          <ResetImpactList impact={resetImpact} />
        </section>
      )}
      <AlertDialog
        open={confirmReset && !!resetImpact}
        onOpenChange={(open) => {
          if (!busy) setConfirmReset(open);
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>确认重置受影响的进度？</AlertDialogTitle>
            <AlertDialogDescription>
              确认后将保存修改。受影响的互动将结束，保留的任务需要重新完成。阅读、作答记录和成绩会保留。
            </AlertDialogDescription>
          </AlertDialogHeader>
          {impactChanged && (
            <p
              role="status"
              className="rounded-md bg-amber-50 p-2 text-sm text-amber-900"
            >
              受影响的记录已变化，请按最新范围重新确认。
            </p>
          )}
          {resetImpact && <ResetImpactList impact={resetImpact} />}
          <AlertDialogFooter>
            <AlertDialogCancel disabled={busy}>继续编辑</AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              disabled={busy || !resetImpact}
              onClick={() => void validate(true, resetImpact?.token)}
            >
              {busy ? "保存中…" : "重置进度并保存"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
