import { api, errorMessage } from "@/lib/api";
import { useState } from "react";
import type { Document, Preview } from "./contracts";
import { authoringBody } from "./request";
import type { useAuthoringDraft } from "./use-authoring-draft";
export function useAuthoringPreview(
  initial: Document,
  classroomId: number,
  draft: ReturnType<typeof useAuthoringDraft>,
) {
  const { data, setBusy, setError } = draft;
  const base = `/teacher/classrooms/${classroomId}/authoring`;
  const [previewOpen, setPreviewOpen] = useState(false),
    [previewStudent, setPreviewStudent] = useState(
      initial.students?.[0]?.id ?? 0,
    ),
    [previewEntry, setPreviewEntry] = useState(""),
    [preview, setPreview] = useState<Preview | null>(null);
  async function runPreview() {
    setBusy(true);
    setError("");
    setPreview(null);
    try {
      setPreview(
        await api.post<Preview>(`${base}/preview`, {
          ...authoringBody(data),
          student_id: previewStudent,
          entry: previewEntry,
        }),
      );
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return {
    previewOpen,
    setPreviewOpen,
    previewStudent,
    setPreviewStudent,
    previewEntry,
    setPreviewEntry,
    preview,
    setPreview,
    runPreview,
  };
}
