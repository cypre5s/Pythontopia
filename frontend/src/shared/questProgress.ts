import type { Quest } from "./types";

export function requiredQuestNodes(quest: Quest | undefined) {
  return quest?.nodes.filter(node => !node.optional && node.status !== "skipped") ?? [];
}
