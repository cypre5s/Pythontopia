import { api, errorMessage, scoped } from "@/lib/api";
import { useDraft } from "@/lib/drafts";
import type { Material } from "@/lib/types";
import { useState, type FormEvent } from "react";
export function useMaterialEditor(classroomId: number, onSaved: () => void) {
  const [editing, setEditing] = useState<Material | null>(null),
    [open, setOpen] = useState(false),
    [title, setTitle] = useState(""),
    [chapterIds, setChapterIds] = useState<number[]>([]),
    [file, setFile] = useState<File | null>(null),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [dirty, setDirty] = useState(false);
  useDraft(dirty);
  function edit(m: Material | null) {
    if (dirty && !window.confirm("放弃当前资料的未保存修改？")) return;
    setEditing(m);
    setTitle(m?.title ?? "");
    setChapterIds(m?.chapter_ids ?? []);
    setFile(null);
    setDirty(false);
    setError("");
    setOpen(true);
  }
  async function save(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      if (file) {
        const body = new FormData();
        body.append("file", file);
        body.append("title", title);
        body.append("chapter_ids", JSON.stringify(chapterIds));
        if (editing) body.append("material_id", String(editing.id));
        await api.upload(
          scoped("/teacher/materials/upload", classroomId),
          body,
        );
      } else if (editing) {
        await api.put(scoped(`/teacher/materials/${editing.id}`, classroomId), {
          title,
          chapter_ids: chapterIds,
        });
      } else throw new Error("请选择需要上传的文件");
      setOpen(false);
      setDirty(false);
      onSaved();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return {
    editing,
    setEditing,
    open,
    setOpen,
    title,
    setTitle,
    chapterIds,
    setChapterIds,
    file,
    setFile,
    error,
    setError,
    busy,
    setBusy,
    dirty,
    setDirty,
    edit,
    save,
  };
}
