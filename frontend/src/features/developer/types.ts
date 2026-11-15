import type { Workspace } from "../../shared/types";
import type { ExperienceClientState } from "../../shared/devTools";

export type DeveloperTab = "account" | "content" | "stage" | "checkpoints";

export interface ExperienceNode {
  id: string;
  title: string;
  status: string;
  dependencies?: string[];
  aggregation?: string;
  condition?: { node: string; choice: string };
  choices?: { id: string; label: string }[];
}

export interface ExperienceOverview {
  chapters: { id: number; title: string; available: boolean; status: string }[];
  materials: {
    id: number;
    title: string;
    kind: string;
    status: string;
    pages: number;
    chapters: number[];
  }[];
  activities: {
    id: string;
    chapter_id: number;
    title: string;
    kind: string;
    published: boolean;
    questions: number;
  }[];
  quests: { id: string; title: string; chapter_id: number; nodes: ExperienceNode[] }[];
  checkpoints: { id: string; name: string; created_at: string; valid: boolean }[];
}

export interface OpenContentInput {
  kind: "material" | "activity";
  target: string;
  page_no: number;
  question_no: number;
  video_time: number;
}

export interface StageInput {
  quest_id: string;
  node_id: string;
  choices: Record<string, string>;
  any_dependencies: Record<string, string>;
}

export interface StagePreview {
  expected: string;
  completed: { title: string }[];
  target: string;
}

export interface CheckpointInput {
  request_id: string;
  name: string;
  client_state: ExperienceClientState;
}

export interface ContentDestination {
  workspace: Workspace;
}
