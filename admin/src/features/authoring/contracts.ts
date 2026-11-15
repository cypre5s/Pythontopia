import type { Authoring as AuthoringData } from "./model";
export type Document = AuthoringData & {
  students: { id: number; name: string }[];
};
export interface Validation {
  valid: boolean;
  diagnostics: Diagnostic[];
  reset_impact?: ResetImpact;
}
export type ResetImpact = {
  token: string;
  students: number;
  flows: {
    id: string;
    title: string;
    reason: "delete" | "edit" | "task_reset";
    sessions: number;
    active_sessions: number;
    waiting_sessions: number;
    students: number;
  }[];
  quests: {
    id: string;
    title: string;
    reason: "delete" | "edit";
    progress: number;
    students: number;
    events: number;
  }[];
};
export type DiagnosticTarget = {
  kind:
    | "flow"
    | "entry"
    | "node"
    | "quest"
    | "goal"
    | "interaction"
    | "building"
    | "npc"
    | "assistant";
  id?: string;
  flow_id?: string;
  quest_id?: string;
};
export type Diagnostic = {
  message: string;
  path: string;
  code?: string;
  suggestion?: string;
  target?: DiagnosticTarget;
};
export interface Preview {
  selected_flow_id: string | null;
  node_id: string | null;
  trace: { node_id: string; type: string }[];
  stopped_at: string;
}
