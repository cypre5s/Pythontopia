export interface User {
  id: number;
  student_id: string;
  name: string;
  role: "student" | "teacher" | "admin";
  must_change_password: boolean;
}
export interface Classroom {
  id: number;
  name: string;
  teacher_id: number;
  join_code?: string;
}
export interface Student {
  id: number;
  student_id: string;
  name: string;
  must_change_password: boolean;
}
export interface StudentPreview {
  student_id: string;
  name: string;
  action: "create" | "existing";
}
export interface AuthResult {
  access_token: string;
  user: User;
}
export interface CourseInstance {
  id: number;
  classroom_id: number;
  package_id: string;
  name: string;
  package_version: string;
  revision: number;
}
export interface Chapter {
  id: number;
  title: string;
  order_index: number;
  owner_id: number | null;
}
export interface Point {
  id: number;
  chapter_id: number;
  title: string;
  content: Record<string, unknown>;
  owner_id: number | null;
}
export interface Activity {
  id: string;
  chapter_id: number;
  title: string;
  kind: "lesson" | "reading" | "practice" | "assessment";
  material_id: number | null;
  npc_key: string | null;
  assistant_allowed: boolean;
  learning: Record<string, unknown>;
  knowledge_point_ids: number[];
  question_ids: string[];
  owner_id: number | null;
}
export interface Course {
  chapters: Chapter[];
  knowledge_points: Point[];
  activities: Activity[];
  npcs: { key: string; name: string }[];
}
export interface Material {
  id: number;
  title: string;
  kind: string;
  status: string;
  error?: string;
  chapter_ids: number[];
  owner_id: number | null;
  has_file?: boolean;
}
export interface Question {
  hints?: string[];
  starter_code?: string;
  id: string;
  title: string;
  kind: "single_choice" | "boolean" | "fill" | "python";
  prompt: string;
  options: string[];
  answer: unknown;
  hidden_cases: { input: string; expected_output?: string; output?:string }[];
  samples: { input: string; output?: string; expected_output?:string }[];
  source: Record<string, unknown>;
  source_type: "imported" | "ai_generated";
  status: "draft" | "ready" | "disabled";
  chapter_ids: number[];
  knowledge_point_ids: number[];
  owner_id: number | null;
}
