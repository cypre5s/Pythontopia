import { describe, it, expect } from "vitest";
import { mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import QuestJournal from "../src/features/QuestJournal.vue";
import { useSession } from "../src/shared/session";
import type { QuestNode } from "../src/shared/types";
describe("quest journal main and side classification", () => {
  it("shows a completed test-only branching quest at full progress without counting skipped or optional goals", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const store = useSession();
    const ids = ["start", "meet-mentor", "read", "choose", "path-a", "path-b-read", "path-b-practice", "parallel-one", "parallel-two", "finish"];
    const nodes: QuestNode[] = ids.map(id => ({ id, title: id, type: "TALK_TO_NPC", target: "professor", dependencies: [], aggregation: "ALL", optional: false, status: ["path-b-read", "path-b-practice"].includes(id) ? "skipped" : "complete" }));
    nodes.push({ id: "optional-reading", title: "可选阅读", type: "STUDY_LESSON", target: "", dependencies: [], aggregation: "ALL", optional: true, status: "ready" });
    store.world = { player: { map_key: "town_hub", tile_x: 22, tile_y: 36, direction: "down" }, npcs: [], chapters: [], flags: [], quests: [{ id: "branch-fixture", kind: "main", title: "分支计数测试", chapter_id: 179, status: "complete", nodes }] };
    const wrapper = mount(QuestJournal, { global: { plugins: [pinia] } });
    await wrapper.findAll("button").find(button => button.text() === "已完成")!.trigger("click");
    expect(wrapper.get(".journal-objectives span").text()).toBe("8 / 8");
    expect(wrapper.get("progress").attributes()).toMatchObject({ value: "8", max: "8" });
    expect(wrapper.findAll(".journal-entry li")).toHaveLength(11);
    expect(wrapper.findAll("li.skipped")).toHaveLength(2);
    expect(wrapper.text()).toContain("可选阅读");
    wrapper.unmount();
  });

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

          status: "active",
          nodes: [],
        },
        {
          id: "side",
          kind: "side",
          title: "支线找书",
          chapter_id: 179,

          status: "active",
          nodes: [],
        },
        {
          id: "finished-side",
          kind: "side",
          title: "已归还的书",
          chapter_id: 179,

          status: "complete",
          nodes: [],
        },
      ],
    };
    const wrapper = mount(QuestJournal, { global: { plugins: [pinia] } });
    await wrapper.get(".journal-kind-select select").setValue("side");
    expect(wrapper.findAll(".journal-entry h3").map((h) => h.text())).toEqual(["支线找书"]);
    expect(wrapper.find(".journal-entry .eyebrow").text()).toContain(
      "支线委托",
    );
    await wrapper
      .findAll("button")
      .find((b) => b.text() === "已完成")!
      .trigger("click");
    expect(wrapper.findAll(".journal-entry h3").map((h) => h.text())).toEqual(["已归还的书"]);
    expect(wrapper.find(".journal-entry .eyebrow").text()).toContain(
      "支线委托 · 已完成",
    );
    wrapper.unmount();
  });

  it("keeps filtering inside the directory and shows a centered, uncluttered empty state", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const store = useSession();
    store.world = { player: { map_key: "town_hub", tile_x: 22, tile_y: 36, direction: "down" }, npcs: [], chapters: [], flags: [], quests: [] };
    const wrapper = mount(QuestJournal, { global: { plugins: [pinia] } });
    expect(wrapper.find(".journal-controls").exists()).toBe(false);
    expect(wrapper.find(".journal-count").exists()).toBe(false);
    expect(wrapper.find(".journal-cover img").exists()).toBe(false);
    expect(wrapper.get(".journal-cover h3").text()).toBe("委托目录");
    expect(wrapper.get('.journal-filter-bar [aria-label="任务状态"]').findAll("button")).toHaveLength(3);
    expect(wrapper.get(".journal-filter-bar select").findAll("option")).toHaveLength(3);
    expect(wrapper.get(".quest-directory").classes()).toContain("is-empty");
    expect(wrapper.get(".quest-directory").text()).toBe("没有委托");
    expect(wrapper.get(".journal-empty").text()).toBe("没有委托");
    expect(wrapper.find(".directory-label").exists()).toBe(false);
    expect(wrapper.find(".journal-empty svg").exists()).toBe(false);
    await wrapper.get(".journal-kind-select select").setValue("side");
    await wrapper.get('.status-tabs button[aria-pressed="true"]').trigger("click");
    expect(wrapper.get(".quest-directory").text()).toBe("没有委托");
    wrapper.unmount();
  });
});
