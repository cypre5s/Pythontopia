import type { PickerGroup } from "@/components/grouped-picker";
import type { Authoring, Catalog, Flow } from "@/lib/authoring";

export function flowGroups(flows: Flow[], chapters: Catalog["chapters"]): PickerGroup[] {
  const ids = new Set([null, ...chapters.map(({ id }) => id), ...flows.map(({ chapter_id }) => chapter_id)]);
  return [...ids].flatMap((id) => {
    const choices = flows.filter((flow) => flow.chapter_id === id).map(({ id, title }) => ({ value: id, label: title }));
    if (!choices.length) return [];
    const label = id === null ? "公共流程" : chapters.find((chapter) => chapter.id === id)?.title ?? "未找到章节";
    return [{ id: id === null ? "public" : `chapter:${id}`, label, choices }];
  });
}

export function mapPickerGroups(catalog: Catalog, options: { value: string; label: string; map_key?: string | null }[]): PickerGroup[] {
  const byMap = new Map<string, PickerGroup>();
  const unspecified = { id: "unknown", label: "未标记位置", choices: [] as PickerGroup["choices"] };
  for (const map of catalog.maps) byMap.set(map.key, { id: map.key, label: map.label, choices: [] });
  for (const { value, label, map_key } of options) {
    const group = (map_key && byMap.get(map_key)) || unspecified;
    group.choices.push({ value, label });
  }
  return [...byMap.values(), unspecified].filter((group) => group.choices.length);
}

export function npcGroups(catalog: Catalog) {
  return mapPickerGroups(catalog, catalog.npcs.map(({ id, name, map_key }) => ({ value: id, label: name, map_key })));
}

export function entryGroups(data: Authoring, flow: Flow) {
  return mapPickerGroups(data.catalog, [
    ...data.catalog.npcs.map(({ id, name, map_key }) => ({ value: id, label: name, map_key })),
    ...(data.configuration.world.interactions ?? []).map(({ id, label, map_key }) => ({ value: `interaction:${id}`, label: `交互 · ${label}`, map_key })),
  ].filter(({ value }) => !Object.hasOwn(flow.content.entries, value)));
}
