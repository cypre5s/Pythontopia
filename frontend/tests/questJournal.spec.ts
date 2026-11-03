import { describe, it, expect } from "vitest";
import { mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import QuestJournal from "../src/features/QuestJournal.vue";
import { useSession } from "../src/shared/session";
describe("quest journal main and side classification", () => {
  it("filters side quests separately and retains completed side history", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const store = useSession();
    store.world = {
      player: {
        map_key: "town_hub",
        tile_x: 22,
        tile_y: 36,
        direction: "down",
      },
      npcs: [],
      chapters: [],
      flags: [],
      quests: [
        {
          id: "main",
          kind: "main",
          title: "主线来信",
          chapter_id: 179,
          version: 1,
          status: "active",
          nodes: [],
        },
        {
          id: "side",
          kind: "side",
          title: "支线找书",
          chapter_id: 179,
          version: 1,
          status: "active",
          nodes: [],
        },
        {
          id: "finished-side",
          kind: "side",
          title: "已归还的书",
          chapter_id: 179,
          version: 1,
          status: "complete",
          nodes: [],
        },
      ],
    };
    const wrapper = mount(QuestJournal, { global: { plugins: [pinia] } });
    await wrapper
      .findAll("button")
      .find((b) => b.text() === "支线")!
      .trigger("click");
    expect(wrapper.findAll("h3").map((h) => h.text())).toEqual(["支线找书"]);
    expect(wrapper.find(".journal-entry .eyebrow").text()).toContain(
      "支线委托",
    );
    await wrapper
      .findAll("button")
      .find((b) => b.text() === "已完成")!
      .trigger("click");
    expect(wrapper.findAll("h3").map((h) => h.text())).toEqual(["已归还的书"]);
    expect(wrapper.find(".journal-entry .eyebrow").text()).toContain(
      "支线委托 · 已完成",
    );
  });
});
