import { PageHeader } from "@/components/common";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { CheckCheck, Play, Save, Undo2 } from "lucide-react";
import type { useAuthoringDraft } from "./use-authoring-draft";
export function AuthoringActions({
  draft,
  hasEntries,
  onPreview,
  onDiscard,
  onValidate,
}: {
  draft: ReturnType<typeof useAuthoringDraft>;
  hasEntries: boolean;
  onPreview: () => void;
  onDiscard: () => void;
  onValidate: (save?: boolean) => void;
}) {
  const { busy, assetBusy, dirty, message } = draft;
  return (
    <PageHeader
      title="教学流程"
      description="组织公共与章节流程，连接地图交互和真实学习活动。"
    >
      <Button
        variant="outline"
        disabled={busy}
        onClick={() => void onValidate()}
      >
        <CheckCheck />
        校验
      </Button>
      <Button
        variant="outline"
        disabled={busy || !hasEntries}
        onClick={onPreview}
      >
        <Play />
        预览
      </Button>
      {dirty && (
        <AlertDialog>
          <AlertDialogTrigger
            render={<Button variant="ghost" disabled={busy || assetBusy} />}
          >
            <Undo2 />
            取消编辑
          </AlertDialogTrigger>
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>放弃本次修改？</AlertDialogTitle>
              <AlertDialogDescription>
                四个标签页的未保存编辑将一起丢弃，课程恢复到上次保存的内容。
              </AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel>继续编辑</AlertDialogCancel>
              <AlertDialogAction variant="destructive" onClick={onDiscard}>
                放弃修改
              </AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>
      )}
      <span role="status" className="flex items-center">
        {dirty ? (
          <Badge
            variant="outline"
            className="gap-1.5 border-amber-200 bg-amber-50 text-amber-800"
            title="有未保存的修改"
          >
            <span
              aria-hidden="true"
              className="size-1.5 rounded-full bg-amber-500"
            />
            未保存
          </Badge>
        ) : message === "课程已保存。" ? (
          <Badge variant="secondary" className="gap-1.5">
            <CheckCheck />
            {message}
          </Badge>
        ) : null}
      </span>
      <Button
        disabled={busy || assetBusy || !dirty}
        onClick={() => void onValidate(true)}
      >
        <Save />
        {busy ? "处理中…" : "保存课程"}
      </Button>
    </PageHeader>
  );
}
