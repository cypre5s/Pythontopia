import type { Chapter, Material, Npc, Question } from "./types";
import type { KnowledgePoint } from "./curriculum";

export interface TeacherActivity {
  id: string;
  title: string;
  chapter_id: number;
  kind: string;
  owner_id: number | null;
  question_ids?: string[];
}
export interface TeacherCourses {
  chapters: Chapter[];
  activities: TeacherActivity[];
  knowledge_points: KnowledgePoint[];
  npcs: Npc[];
}
export interface TeacherMaterial extends Material {
  chapter_ids: number[];
  owner_id: number | null;
  error?: string;
}
export type QuestionSourceType = "ai_generated" | "imported";
export type QuestionStatus = "draft" | "ready" | "disabled";
export interface TeacherQuestion extends Question {
  owner_id: number | null;
  chapter_ids: number[];
  knowledge_point_ids: number[];
  source_type: QuestionSourceType;
  status: QuestionStatus;
  answer: unknown;
  hidden_cases: { input: string; expected_output?: string; output?: string }[];
  source: Record<string, unknown>;
  activity_ids?: string[];
  activities?: { id: string; title: string }[];
}
export const questionKinds: Record<string, string> = {
  single_choice: "单选题",
  boolean: "判断题",
  fill: "填空题",
  python: "编程题",
};
export const sourceTypes: Record<QuestionSourceType, string> = {
  ai_generated: "AI 生成",
  imported: "导入",
};
export const questionStatuses: Record<QuestionStatus, string> = {
  draft: "待审核",
  ready: "可用",
  disabled: "停用",
};
export function chapterNames(ids: number[], chapters: Chapter[]) {
  return (
    ids
      .map(
        (id) =>
          chapters.find((chapter) => chapter.id === id)?.title ?? `章节 #${id}`,
      )
      .join("、") || "未分类"
  );
}
export function matchesChapter(ids: number[], filter: string) {
  return (
    !filter ||
    (filter === "unassigned" ? !ids.length : ids.includes(Number(filter)))
  );
}
