import type { Authoring } from "./model";
export const authoringBody = (data: Authoring) => ({
  expected_revision: data.revision,
  npcs: data.npcs,
  flows: data.flows,
  quests: data.quests,
  configuration: data.configuration,
});
