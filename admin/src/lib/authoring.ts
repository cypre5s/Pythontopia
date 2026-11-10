export type Condition =
  | { always: true }
  | { all: Condition[] }
  | { any: Condition[] }
  | { not: Condition }
  | { all_quests_complete: true }
  | { chapter: { id: string; status: "available" | "complete" } }
  | { quest: { id: string; status: "active" | "complete" } }
  | {
      goal: {
        quest_id: string;
        id: string;
        status: "ready" | "complete" | "locked" | "skipped";
      };
    }
  | { goal_complete: string }
  | { goal_ready: string }
  | { activity_started: string }
  | { activity_completed: string }
  | { building_unlocked: string }
  | { assessment_score: { activity_id: string; min?: number; max?: number } };
export interface Route {
  when: Condition;
  node: string;
}
export interface Choice {
  id: string;
  label: string;
  next: string;
  when?: Condition;
}
export type StoryNode =
  | {
      id: string;
      type: "dialogue";
      text: string;
      speaker?: string;
      next?: string;
      choices?: Choice[];
      checkpoint?: string;
    }
  | {
      id: string;
      type: "activity";
      binding: string;
      returns: { completed: string; suspended: string; unavailable: string };
    }
  | { id: string; type: "branch"; routes: Route[] }
  | { id: string; type: "random"; next: string[] }
  | { id: string; type: "end" }
  | {
      id: string;
      type: "action";
      action: "open_workspace";
      workspace: "library" | "journal" | "classroom";
      text?: string;
      next: string;
    }
  | {
      id: string;
      type: "unlock_building";
      building_id: string;
      when: Condition;
      next: string;
    }
  | {
      id: string;
      type: "action";
      action: "enter_building";
      building_id: string;
      text?: string;
      next: string;
    };
export type QuestNodeType =
  | "TALK_TO_NPC"
  | "PLAY_DIALOGUE"
  | "REACH_LOCATION"
  | "INTERACT_OBJECT"
  | "STUDY_LESSON"
  | "COMPLETE_PRACTICE"
  | "PASS_ASSESSMENT"
  | "REPORT_BACK";
export interface QuestNode {
  id: string;
  type: QuestNodeType;
  title: string;
  target?: string;
  dependencies: string[];
  aggregation?: "ALL" | "ANY";
  optional?: boolean;
  activity_id?: string;
  condition?: { node: string; choice: string };
  choices?: { id: string; label: string }[];
  dialogue_id?: string;
  navigation_target?: string;
}
export interface Quest {
  id: string;
  title: string;
  chapter_id: string;
  definition: {
    id: string;
    title: string;
    chapter_id: string;
    kind?: "main" | "side";
    nodes: QuestNode[];
    effects: Record<string, unknown>[];
  };
}
export interface Flow {
  id: string;
  title: string;
  chapter_id: string | null;
  quest_id: string | null;
  content: {
    schema_version: 1;
    mode?: "public" | "ambient" | "task";
    priority?: number;
    when?: Condition;
    bindings: Record<string, { activity_id: string; goal_id?: string }>;
    entries: Record<string, Route[]>;
    nodes: StoryNode[];
    checkpoint_effects?: Record<string, unknown>;
  };
}
export interface Catalog {
  maps: {
    key: string;
    label: string;
    width: number;
    height: number;
    walk_bounds: unknown;
    objects: {
      id: string;
      label: string;
      points: { map_key: string; tile_x: number; tile_y: number }[];
    }[];
  }[];
  buildings: {
    id: string;
    label: string;
    map_key: string;
    has_interior: boolean;
  }[];
  chapters: { id: string; title: string }[];
  activities: { id: string; title: string; kind?: string }[];
  npcs: { id: string; name: string; map_key?: string }[];
  quests: {
    id: string;
    title: string;
    nodes?: { id: string; title?: string; type: string }[];
  }[];
}
export interface Interaction {
  id: string;
  label: string;
  prompt: string;
  trigger: "click" | "enter";
  map_key: string;
  target:
    | { object_id: string }
    | { region: { x: number; y: number; width: number; height: number } };
  when?: Condition;
}
export interface Authoring {
  revision: number;
  catalog: Catalog;
  flows: Flow[];
  quests: Quest[];
  configuration: {
    world: {
      interactions?: Interaction[];
      npcs?: Record<string, { when: Condition }>;
    };
    buildings: Record<
      string,
      {
        enabled: boolean;
        default_unlocked: boolean;
        when?: Condition;
        blocked_message: string;
      }
    >;
    editor: Record<string, unknown>;
    assistant: Record<string, unknown>;
  };
}
export interface Point {
  x: number;
  y: number;
}
export const workspaceNames = {
  library: "图书馆",
  journal: "任务手册",
  classroom: "班级",
};

export function nodeTitle(node: StoryNode, catalog?: Catalog, flow?: Flow) {
  switch (node.type) {
    case "dialogue":
      return node.text.slice(0, 36) || "新对白";
    case "branch":
      return "条件分支";
    case "activity":
      return catalog?.activities.find(
        (activity) => activity.id === flow?.content.bindings[node.binding]?.activity_id,
      )?.title ?? "选择学习活动";
    case "random":
      return "随机分支";
    case "action":
      return node.action === "open_workspace"
        ? `打开${workspaceNames[node.workspace]}`
        : `进入${catalog?.buildings.find((b) => b.id === node.building_id)?.label ?? "建筑"}`;
    case "unlock_building":
      return "解锁建筑";
    case "end":
      return "流程结束";
  }
}
export interface FlowPort {
  id: string;
  to: string;
  label: string;
}

const activityPorts = ["completed", "suspended", "unavailable"] as const;
const activityLabels = { completed: "真实完成", suspended: "暂停", unavailable: "不可用" };

export function routePorts(routes: Route[]): FlowPort[] {
  return routes.map((r, i) => ({
    id: `route-${i}`,
    to: r.node,
    label: "always" in r.when ? "否则" : `条件 ${i + 1}`,
  }));
}

export function nodeEdges(node: StoryNode): FlowPort[] {
  switch (node.type) {
    case "dialogue":
      return node.choices?.length
        ? node.choices.map((c) => ({ id: c.id, to: c.next, label: c.label }))
        : [{ id: "next", to: node.next ?? "", label: "下一步" }];
    case "branch":
      return routePorts(node.routes);
    case "activity":
      return activityPorts.map((key) => ({
        id: key,
        to: node.returns[key],
        label: activityLabels[key],
      }));
    case "random":
      return node.next.map((to, i) => ({
        id: `random-${i}`, to, label: `候选 ${i + 1}`,
      }));
    case "action":
    case "unlock_building":
      return [{ id: "next", to: node.next, label: "下一步" }];
    case "end":
      return [];
  }
}

export function setRouteTarget(routes: Route[], port: string, target: string) {
  return routes.map((r, i) => port === `route-${i}` ? { ...r, node: target } : r);
}

export function setNodeTarget(node: StoryNode, port: string, target: string): StoryNode {
  switch (node.type) {
    case "dialogue":
      return node.choices?.length
        ? { ...node, choices: node.choices.map((choice) =>
            choice.id === port ? { ...choice, next: target } : choice,
          ) }
        : { ...node, next: target };
    case "branch":
      return { ...node, routes: setRouteTarget(node.routes, port, target) };
    case "activity":
      return { ...node, returns: { ...node.returns, [port]: target } };
    case "random":
      return { ...node, next: node.next.map((to, i) => port === `random-${i}` ? target : to) };
    case "action":
    case "unlock_building":
      return { ...node, next: target };
    case "end":
      return node;
  }
}
