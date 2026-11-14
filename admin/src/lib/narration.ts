import { api } from "./api";

export type NarrationStatus = "draft" | "accepted" | "rejected" | "unavailable" | "stale";

export interface Narration {
  text: string;
  bridge_in: string;
  target_seconds: number;
  status: NarrationStatus;
  source: string;
  model: string;
  image_used: boolean;
  anchors: string[];
  edited_by_teacher: boolean;
  reviewed_at: string | null;
  stale_reason: string | null;
}

export interface NarrationPage {
  page_no: number;
  title: string;
  has_text: boolean;
  source_chars: number;
  source_text: string;
  narration: Narration | null;
}

export interface NarrationCoverage {
  pages: number;
  covered: number;
  missing: number;
  draft: number;
  accepted: number;
  rejected: number;
  unavailable: number;
  stale: number;
}

export interface NarrationList {
  material_id: number;
  title: string;
  coverage: NarrationCoverage;
  pages: NarrationPage[];
}

export interface NarrationAudioStatus {
  available: boolean;
  voice: string;
  model: string;
  pages: { page_no: number; status: string; ready: boolean; seconds: number | null }[];
}

export interface NarrationQuota {
  used_today: number;
  daily_limit: number;
  available: boolean;
}

export interface CalibrationIssue {
  page_no: number;
  kind: "flow" | "mismatch" | "term" | string;
  detail: string;
  status: "draft" | "accepted";
}

export interface CalibrationReport {
  id: number;
  windows: number;
  pages: number;
  summary: string;
  terms: string[];
  issues: CalibrationIssue[];
  generated_at?: string;
  applied_at?: string | null;
  applied_pages?: { regenerated: number[]; failed: { page_no: number; error: string }[]; awaiting_teacher: number[] } | null;
}

const base = (classroomId: number, materialId: number) =>
  `/teacher/classrooms/${classroomId}/materials/${materialId}/narrations`;

export const narrationApi = {
  list: (classroomId: number, materialId: number) => api.get<NarrationList>(base(classroomId, materialId)),
  quota: (classroomId: number, materialId: number) => api.get<NarrationQuota>(`${base(classroomId, materialId)}/quota`),
  generatePage: (classroomId: number, materialId: number, pageNo: number, targetSeconds?: number) =>
    api.post<{ page_no: number; status: string; reused: boolean; model: string }>(
      `${base(classroomId, materialId)}/${pageNo}/generate`,
      targetSeconds ? { target_seconds: targetSeconds } : {},
    ),
  generateRange: (classroomId: number, materialId: number, firstPage: number, lastPage: number) =>
    api.post<{ results: { page_no: number; status: string; error?: string }[] }>(`${base(classroomId, materialId)}/generate`, {
      first_page: firstPage,
      last_page: lastPage,
    }),
  save: (classroomId: number, materialId: number, pageNo: number, text: string) =>
    api.put<{ page_no: number; status: string; text: string }>(`${base(classroomId, materialId)}/${pageNo}`, { text }),
  accept: (classroomId: number, materialId: number, pageNo: number) =>
    api.post<{ page_no: number; status: string }>(`${base(classroomId, materialId)}/${pageNo}/accept`, {}),
  reject: (classroomId: number, materialId: number, pageNo: number) =>
    api.post<{ page_no: number; status: string }>(`${base(classroomId, materialId)}/${pageNo}/reject`, {}),
  remove: (classroomId: number, materialId: number, pageNo: number) =>
    api.delete(`${base(classroomId, materialId)}/${pageNo}`),
  calibrate: (classroomId: number, materialId: number) =>
    api.post<CalibrationReport>(`${base(classroomId, materialId)}/calibrate`, {}),
  calibration: (classroomId: number, materialId: number) =>
    api.get<CalibrationReport>(`${base(classroomId, materialId)}/calibrate`),
  applyCalibration: (classroomId: number, materialId: number) =>
    api.post<CalibrationReport["applied_pages"]>(`${base(classroomId, materialId)}/calibrate/apply`, {}),
  audioStatus: (classroomId: number, materialId: number) =>
    api.get<NarrationAudioStatus>(`${base(classroomId, materialId)}/audio`),
  synthesizeAudio: (classroomId: number, materialId: number, pageNo: number) =>
    api.post<{ page_no: number; reused: boolean; key: string; seconds: number }>(
      `${base(classroomId, materialId)}/${pageNo}/audio`,
      {},
    ),
  pageImage: (classroomId: number, materialId: number, pageNo: number) =>
    `${base(classroomId, materialId)}/${pageNo}/page-image`,
  audioFile: (classroomId: number, materialId: number, pageNo: number) =>
    `/teacher/classrooms/${classroomId}/materials/${materialId}/narrations/${pageNo}/audio/file`,
};
