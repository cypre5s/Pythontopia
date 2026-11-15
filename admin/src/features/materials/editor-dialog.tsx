import { ErrorNotice } from "@/components/common";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { Course } from "@/lib/types";
import { FileUp } from "lucide-react";
import type { useMaterialEditor } from "./use-editor";
export function MaterialDialog({
  editor,
  course,
}: {
  editor: ReturnType<typeof useMaterialEditor>;
  course: { data: Course | null };
}) {
  const {
    editing,
    open,
    setOpen,
    title,
    setTitle,
    chapterIds,
    setChapterIds,
    setFile,
    error,
    busy,
    setDirty,
    save,
  } = editor;
  return (
    <Dialog
      open={open}
      onOpenChange={(v) => {
        if (!busy) setOpen(v);
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{editing ? "编辑资料" : "上传资料"}</DialogTitle>
          <DialogDescription>
            文件保存在私有存储中，仅向有权限的学生开放。
          </DialogDescription>
        </DialogHeader>
        <form
          inert={busy}
          aria-busy={busy}
          onSubmit={save}
          onChange={() => setDirty(true)}
        >
          <ErrorNotice message={error} />
          <div className="field">
            <Label htmlFor="material-title">资料名称</Label>
            <Input
              id="material-title"
              required
              maxLength={200}
              value={title}
              onChange={(e) => setTitle(e.target.value)}
            />
          </div>
          <div className="field">
            <Label htmlFor="material-file">
              {editing ? "替换文件（可选）" : "文件"}
            </Label>
            <Input
              id="material-file"
              type="file"
              required={!editing}
              accept=".pdf,.txt,.md,.ppt,.pptx,.mp4,.webm"
              onChange={(e) => {
                const f = e.target.files?.[0] ?? null;
                setFile(f);
                if (f && !title) setTitle(f.name.replace(/\.[^.]+$/, ""));
              }}
            />
          </div>
          <fieldset className="field">
            <legend>关联章节</legend>
            <div className="check-list">
              {course.data?.chapters.map((c) => (
                <label key={c.id}>
                  <input
                    type="checkbox"
                    checked={chapterIds.includes(c.id)}
                    onChange={() =>
                      setChapterIds((v) =>
                        v.includes(c.id)
                          ? v.filter((id) => id !== c.id)
                          : [...v, c.id],
                      )
                    }
                  />
                  {c.title}
                </label>
              ))}
            </div>
            <p className="hint">可以暂不归类，之后再关联章节。</p>
          </fieldset>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              disabled={busy}
              onClick={() => setOpen(false)}
            >
              取消
            </Button>
            <Button type="submit" disabled={busy}>
              <FileUp />
              {busy ? "处理中…" : "保存资料"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
