export interface User {
  id: number;
  student_id: string;
  name: string;
  role: string;
  dev_tools_enabled?: boolean;
  town_preview?: boolean;
  must_change_password?: boolean;
}
export interface Classroom {
  id: number;
  name: string;
  teacher_id: number;
  teacher_name?: string;
  join_code?: string;
}
export interface Player {
  map_key: string;
  tile_x: number;
  tile_y: number;
  direction: string;
}
export interface StoryDialogue {
  id: string;
  text: string;
  speaker?: string;
  portrait?: string;
  choices?: { id: string; label: string }[];
}
export interface Npc {
  key: string;
  stable_id?: string;
  name: string;
  map_key: string;
  tile_x: number;
  tile_y: number;
  portrait: string;
  direction: string;
  sprite: { id: string; url: string; frame_width: number; frame_height: number };
}
export type NodeStatus = "ready" | "locked" | "complete" | "skipped";
export interface QuestNode {
  id: string;
  type: string;
  title: string;
  target: string;
  activity_id?: string;
  dialogue_id?: string;
  navigation_target?: string;
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
  story_id?: string;
  status: string;
  nodes: QuestNode[];
}
export interface Chapter {
  id: number;
  title: string;
  order_index: number;
  available: boolean;
}
export interface CharacterIdentity { key: string; name: string; portrait: string; sprite: Npc["sprite"] }
export interface World {
  default_role: CharacterIdentity | null;
  story_available?: boolean;
  player: Player;
  npcs: Npc[];
  quests: Quest[];
  chapters: Chapter[];
  flags: string[];
  interactions?: CourseInteraction[];
  building_access?: Record<string, BuildingAccess>;
  building_unlock?: string[];
}
export interface BuildingAccess {
  enabled: boolean;
  unlocked: boolean;
  can_enter: boolean;
  blocked_message: string;
}
export interface CourseInteraction {
  id: string;
  label: string;
  prompt: string;
  trigger: "click" | "enter";
  map_key: string;
  target: { object_id: string } | { region: { x: number; y: number; width: number; height: number } };
  points: { map_key: string; tile_x: number; tile_y: number }[];
  radius: number;
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
  status: string;
  pages: Page[];
  media_url?: string;
  notice?: string;
  chapter_id?: number;
  chapter_ids?: number[];
  duration_seconds?: number | null;
}
export interface Question {
  hints?: string[];
  starter_code?: string;
  id: string;
  title: string;
  kind: "single_choice" | "boolean" | "fill" | "python";
  prompt: string;
  options: string[];
  samples: { input: string; output: string }[];
}
export interface Activity {
  role: CharacterIdentity | null;
  id: string;
  chapter_id: number;
  title: string;
  kind: string;
  npc_key: string | null;
  assistant_allowed: boolean;
  material: Material | null;
  questions: Question[];
  progress?: ActivityProgress;
  completion?: { required_page_ids: number[] } | null;
  learning?: LearningConfiguration | null;
}
export interface LearningConfiguration {
  reading?: { scope: "all" | "range"; start_page?: number; end_page?: number };
  video?: { minimum_seconds: number };
  highlights?: {
    pages?: { start_page: number; end_page: number; label: string }[];
    video?: { start_seconds: number; end_seconds: number; label: string }[];
  };
}
export interface ActivityProgress {
  page_no: number | null;
  visited_pages: number[];
  completed: boolean;
  accepted_question_ids?: string[];
  required_page_ids?: number[];
  read_count?: number;
  read_total?: number;
  watched_seconds?: number;
  required_seconds?: number;
  score?: number | null;
  max_score?: 100;
}
export interface StoryView {
  mode?: "task" | "ambient" | "public";
  id: string;
  revision: number;
  status: "active" | "waiting" | "ended";
  npc_key: string | null;
  interaction_id?: string | null;
  node: (StoryDialogue & { type?: "dialogue" | "action"; action?: "open_workspace" | "enter_building"; workspace?: "library" | "journal" | "classroom"; building_id?: string }) | null;
  activity: { id: string; kind: string; page_no?: number | null } | null;
  world?: World;
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
  mode: "run" | "submit";
  status: SubmissionStatus;
  feedback: SubmissionFeedback;
  created_at: string;
}
export interface AssessmentAnswer {
  question_id: string;
  answer?: string | boolean | Record<string, unknown> | unknown[] | null;
  code?: string | null;
}
export interface AssessmentAttempt {
  id: string | null;
  status: "draft" | "grading" | "graded" | "attention";
  revision: number;
  locked: boolean;
  question_ids: string[];
  answers: AssessmentAnswer[];
  results: Submission[];
  correct_count: number;
  total: number;
  score: number | null;
  max_score: 100;
  completed: boolean;
  passed: boolean;
  legacy: boolean;
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
  | { kind: "lesson" | "practice"; activity_id: string; page_no?: number; story_session_id?: string }
  | { kind: "material"; material_id: number; page_no?: number }
  | { kind: "journal" | "library" | "classroom" | "settings" | "map" | "developer" }
  | null;
