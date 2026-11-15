import { api, errorMessage, scoped } from "@/lib/api";
import { useDraft } from "@/lib/drafts";
import { newId } from "@/lib/id";
import type { Activity, Course } from "@/lib/types";
import { useState, type FormEvent } from "react";
import { Highlight } from "./types";
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
    [material, setMaterial] = useState<number | null>(null),
    [pointIds, setPointIds] = useState<number[]>([]),
    [npc, setNpc] = useState(""),
    [assistant, setAssistant] = useState(true),
    [rule, setRule] = useState("keep"),
    [start, setStart] = useState(1),
    [end, setEnd] = useState(1),
    [seconds, setSeconds] = useState(60),
    [pageHighlights, setPageHighlights] = useState<Highlight[]>([]),
    [videoHighlights, setVideoHighlights] = useState<Highlight[]>([]),
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
    setMaterial(a?.material_id ?? null);
    setPointIds(a?.knowledge_point_ids ?? []);
    setNpc(a?.npc_key ?? "");
    setAssistant(a?.assistant_allowed ?? true);
    setRule(a ? "keep" : "all");
    const highlights = a?.learning.highlights as
      | {
          pages?: { start_page: number; end_page: number; label: string }[];
          video?: {
            start_seconds: number;
            end_seconds: number;
            label: string;
          }[];
        }
      | undefined;
    setPageHighlights(
      highlights?.pages?.map((h) => ({
        start: h.start_page,
        end: h.end_page,
        label: h.label,
      })) ?? [],
    );
    setVideoHighlights(
      highlights?.video?.map((h) => ({
        start: h.start_seconds,
        end: h.end_seconds,
        label: h.label,
      })) ?? [],
    );
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
        const learning: Record<string, unknown> =
          rule === "keep"
            ? { ...(editing?.learning ?? {}) }
            : {
                ...(editing?.learning ?? {}),
                reading: undefined,
                video: undefined,
                ...(rule === "all"
                  ? { reading: { scope: "all" } }
                  : rule === "range"
                    ? {
                        reading: {
                          scope: "range",
                          start_page: start,
                          end_page: end,
                        },
                      }
                    : { video: { minimum_seconds: seconds } }),
              };
        if (kind === "lesson" || kind === "reading")
          learning.highlights = {
            pages: pageHighlights.map((h) => ({
              start_page: h.start,
              end_page: h.end,
              label: h.label,
            })),
            video: videoHighlights.map((h) => ({
              start_seconds: h.start,
              end_seconds: h.end,
              label: h.label,
            })),
          };
        const body = {
          title,
          npc_key: npc || null,
          assistant_allowed: kind === "assessment" ? false : assistant,
          material_id: material,
          knowledge_point_ids: pointIds,
          ...(learning ? { learning } : {}),
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
    material,
    setMaterial,
    pointIds,
    setPointIds,
    npc,
    setNpc,
    assistant,
    setAssistant,
    rule,
    setRule,
    start,
    setStart,
    end,
    setEnd,
    seconds,
    setSeconds,
    pageHighlights,
    setPageHighlights,
    videoHighlights,
    setVideoHighlights,
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
