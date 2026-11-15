import { api, errorMessage, scoped } from "@/lib/api";
import { useDraft } from "@/lib/drafts";
import { newId } from "@/lib/id";
import type { Activity, ActivityResource, Course } from "@/lib/types";
import { useState, type FormEvent } from "react";
export function useActivityEditor(
  classroomId: number,
  onSaved: () => void,
  course: Course | null,
) {
  const [editing, setEditing] = useState<Activity | null>(null),
    [open, setOpen] = useState(false),
    [questionMode, setQuestionMode] = useState(false),
    [title, setTitle] = useState(""),
    [id, setId] = useState(""),
    [chapter, setChapter] = useState(0),
    [kind, setKind] = useState<Activity["kind"]>("lesson"),
    [resources, setResources] = useState<ActivityResource[]>([]),
    [pointIds, setPointIds] = useState<number[]>([]),
    [npc, setNpc] = useState(""),
    [assistant, setAssistant] = useState(true),
    [selected, setSelected] = useState<string[]>([]),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [dirty, setDirty] = useState(false);
  useDraft(dirty);
  function edit(a: Activity | null, pick = false) {
    if (dirty && !window.confirm("放弃当前活动的未保存修改？")) return;
    setEditing(a);
    setId(a?.id ?? newId("activity"));
    setTitle(a?.title ?? "");
    setChapter(a?.chapter_id ?? course?.chapters[0]?.id ?? 0);
    setKind(a?.kind ?? "lesson");
    setResources(structuredClone(a?.resources ?? []));
    setPointIds(a?.knowledge_point_ids ?? []);
    setNpc(a?.npc_key ?? "");
    setAssistant(a?.assistant_allowed ?? true);
    setSelected(a?.question_ids ?? []);
    setQuestionMode(pick);
    setError("");
    setDirty(false);
    setOpen(true);
  }
  async function save(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      if (questionMode) {
        await api.put(
          scoped(
            `/teacher/activities/${encodeURIComponent(id)}/questions`,
            classroomId,
          ),
          { question_ids: selected },
        );
      } else {
        const body = {
          title,
          npc_key: npc || null,
          assistant_allowed: kind === "assessment" ? false : assistant,
          resources,
          knowledge_point_ids: pointIds,
        };
        if (editing)
          await api.put(
            scoped(
              `/teacher/activities/${encodeURIComponent(id)}`,
              classroomId,
            ),
            body,
          );
        else
          await api.post(scoped("/teacher/activities", classroomId), {
            id,
            chapter_id: chapter,
            kind,
            ...body,
          });
      }
      setOpen(false);
      setDirty(false);
      onSaved();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  function move(i: number, delta: number) {
    const next = [...selected];
    [next[i], next[i + delta]] = [next[i + delta], next[i]];
    setSelected(next);
    setDirty(true);
  }
  return {
    classroomId,
    editing,
    setEditing,
    open,
    setOpen,
    questionMode,
    setQuestionMode,
    title,
    setTitle,
    id,
    setId,
    chapter,
    setChapter,
    kind,
    setKind,
    resources,
    setResources,
    pointIds,
    setPointIds,
    npc,
    setNpc,
    assistant,
    setAssistant,
    selected,
    setSelected,
    error,
    setError,
    busy,
    setBusy,
    dirty,
    setDirty,
    edit,
    save,
    move,
  };
}
