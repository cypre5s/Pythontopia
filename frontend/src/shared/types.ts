export interface User {
  id: number;
  email: string;
  name: string;
  role: string;
}
export interface Classroom {
  id: number;
  name: string;
  teacher_id: number;
  join_code?: string;
}
export interface Player {
  map_key: string;
  tile_x: number;
  tile_y: number;
  direction: string;
}
export interface Dialogue {
  id: string;
  text: string;
  speaker?: string;
  portrait?: string;
  next?: string;
  choices?: { id: string; text?: string; label?: string; next?: string }[];
}
export interface Npc {
  key: string;
  name: string;
  map_key: string;
  tile_x: number;
  tile_y: number;
  portrait: string;
  dialogue: Dialogue[];
}
export type NodeStatus = "ready" | "locked" | "complete" | "skipped";
export interface QuestNode {
  id: string;
  type: string;
  title: string;
  target: string;
  activity_id?: string;
  dialogue_id?: string;
  dependencies: string[];
  aggregation: "ALL" | "ANY";
  optional: boolean;
  status: NodeStatus;
  choices?: { id: string; text?: string; label?: string }[];
}
export interface Quest {
  kind: "main" | "side";
  id: string;
  title: string;
  chapter_id: number;
  version: number;
  status: string;
  nodes: QuestNode[];
}
export interface Chapter {
  id: number;
  title: string;
  order_index: number;
  available: boolean;
}
export interface World {
  player: Player;
  npcs: Npc[];
  quests: Quest[];
  chapters: Chapter[];
  flags: string[];
}
export interface Page {
  id: string | number;
  page_no: number;
  title: string;
  text: string;
}
export interface Material {
  id: number;
  title: string;
  kind: string;
  version: number;
  status: string;
  pages: Page[];
  media_url?: string;
  notice?: string;
  chapter_id?: number;
}
export interface Question {
  id: string;
  title: string;
  kind: "single_choice" | "boolean" | "fill" | "python";
  prompt: string;
  options: string[];
  samples: { input: string; output: string }[];
  version: number;
}
export interface Activity {
  id: string;
  chapter_id: number;
  title: string;
  kind: string;
  version: number;
  npc_key: string;
  assistant_allowed: boolean;
  material: Material | null;
  questions: Question[];
}
export type SubmissionStatus =
  | "prepared"
  | "dispatching"
  | "submission_unknown"
  | "queued"
  | "running"
  | "accepted"
  | "wrong_answer"
  | "time_limit"
  | "memory_limit"
  | "compile_error"
  | "runtime_error"
  | "infrastructure_error";
export interface PublicSampleResult {
  status: Exclude<
    SubmissionStatus,
    "prepared" | "dispatching" | "submission_unknown"
  >;
  stdout: string;
  stderr: string;
  compile_output: string;
}
export interface SubmissionFeedback {
  detail?: string;
  status?: SubmissionStatus;
  samples?: PublicSampleResult[];
}
export interface Submission {
  id: string;
  activity_id: string;
  question_id: string;
  version: number;
  mode: "run" | "submit";
  status: SubmissionStatus;
  feedback: SubmissionFeedback;
  created_at: string;
}
export interface EventEvidence {
  npc_key?: string;
  dialogue_id?: string;
  choice?: string;
  object_id?: string;
  map_key?: string;
  tile_x?: number;
  tile_y?: number;
  record_id?: string;
  submission_id?: string;
}
export type Workspace =
  | { kind: "lesson" | "practice"; activity_id: string; page_no?: number }
  | { kind: "material"; material_id: number; version: number; page_no?: number }
  | { kind: "journal" | "library" | "classroom" | "settings" }
  | null;
