import type {
  FlowPort,
  Point,
  QuestNode,
  QuestNodeType,
  StoryNode,
} from "@/features/authoring/model";
import {
  BookOpen,
  ClipboardCheck,
  Code,
  Hand,
  MapPin,
  MessageCircle,
  MessagesSquare,
  Reply,
  type LucideIcon,
} from "lucide-react";
export const goalKinds = {
  TALK_TO_NPC: { label: "与角色交谈", icon: MessageCircle, tone: "dialogue" },
  PLAY_DIALOGUE: {
    label: "完成剧情对白",
    icon: MessagesSquare,
    tone: "dialogue",
  },
  REACH_LOCATION: { label: "到达地点", icon: MapPin, tone: "action" },
  INTERACT_OBJECT: { label: "交互地图对象", icon: Hand, tone: "action" },
  STUDY_LESSON: { label: "完成阅读活动", icon: BookOpen, tone: "activity" },
  COMPLETE_PRACTICE: { label: "完成练习", icon: Code, tone: "activity" },
  PASS_ASSESSMENT: {
    label: "通过测验",
    icon: ClipboardCheck,
    tone: "activity",
  },
  REPORT_BACK: { label: "回报角色", icon: Reply, tone: "dialogue" },
} satisfies Record<
  QuestNodeType,
  { label: string; icon: LucideIcon; tone: StoryNode["type"] }
>;

export const goalPalette = (Object.keys(goalKinds) as QuestNodeType[]).map(
  (kind) => ({
    kind,
    ...goalKinds[kind],
  }),
);

export const newDependency = "dependency:add";

export const dependencyPort = (target: string) => `dependency:to:${target}`;

export const emptyPositions: Record<string, Point> = {};

export const emptyGoals: QuestNode[] = [];

export function describeGoals(goals: QuestNode[]) {
  const nodes: StoryNode[] = goals.map(({ id }) => ({
    id,
    type: "random",
    next: [],
  }));
  const labels = Object.fromEntries(goals.map(({ id, title }) => [id, title]));
  const captions = Object.fromEntries(
    goals.map((goal) => [
      goal.id,
      `${goalKinds[goal.type].label}${goal.optional ? " · 可选" : ""}`,
    ]),
  );
  const nodeAppearances = Object.fromEntries(
    goals.map((goal) => [goal.id, goalKinds[goal.type]]),
  );
  const ports: Record<string, FlowPort[]> = Object.fromEntries(
    goals.map(({ id }) => [id, []]),
  );
  for (const goal of goals) {
    for (const id of goal.dependencies) {
      ports[id]?.push({
        id: dependencyPort(goal.id),
        to: goal.id,
        label: goal.title,
      });
    }
  }
  for (const { id } of goals)
    ports[id].push({ id: newDependency, to: "", label: "添加后续目标" });
  return { nodes, labels, captions, ports, nodeAppearances };
}

export function wouldCycle(goals: QuestNode[], parent: string, child: string) {
  const byId = new Map(goals.map((goal) => [goal.id, goal]));
  const pending = [parent];
  const visited = new Set<string>();
  while (pending.length) {
    const id = pending.pop()!;
    if (id === child) return true;
    if (visited.has(id)) continue;
    visited.add(id);
    pending.push(...(byId.get(id)?.dependencies ?? []));
  }
  return false;
}
