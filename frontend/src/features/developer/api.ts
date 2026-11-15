import { api } from "../../shared/api";
import type {
  CheckpointInput,
  ContentDestination,
  ExperienceOverview,
  OpenContentInput,
  StageInput,
  StagePreview,
} from "./types";

/** Bind requests to the identity's class before any asynchronous work begins. */
export function experienceApi(classroomId: number, teacherPreview: boolean) {
  const base = `${teacherPreview ? "/teacher-town" : "/dev"}/experience`;
  const scope = { classroom_id: classroomId };
  const query = `?classroom_id=${classroomId}`;

  return {
    overview: () => api.get<ExperienceOverview>(`${base}${query}`),
    locateChapter: (chapterId: number) =>
      api.post(`${base}/chapter`, { ...scope, chapter_id: chapterId }),
    openContent: (body: OpenContentInput) =>
      api.post<ContentDestination>(`${base}/open`, { ...scope, ...body }),
    previewStage: (body: StageInput) =>
      api.post<StagePreview>(`${base}/stage/preview`, { ...scope, ...body }),
    saveCheckpoint: (body: CheckpointInput) =>
      api.post(`${base}/checkpoints`, { ...scope, ...body }),
    previewCheckpoint: (id: string) =>
      api.get<{ expected: string; name: string }>(`${base}/checkpoints/${id}/preview${query}`),
    deleteCheckpoint: (id: string) =>
      api.request(`${base}/checkpoints/${id}${query}`, { method: "DELETE" }),
    stageMutation: (body: StageInput, expected: string) => ({
      path: `${base}/stage`,
      body: { ...scope, ...body, expected },
    }),
    restoreMutation: (id: string, expected: string) => ({
      path: `${base}/checkpoints/${id}/restore`,
      body: { ...scope, expected },
      restore: true,
    }),
    spawnMutation: () => ({ path: `${base}/spawn`, body: scope }),
  };
}
