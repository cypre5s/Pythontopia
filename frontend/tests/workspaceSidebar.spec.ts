import { defineComponent, h, ref } from "vue";
import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import LearningSplitLayout from "../src/features/LearningSplitLayout.vue";
import LearningSupportPanel from "../src/features/LearningSupportPanel.vue";
import { createWorkspaceSidebar, provideWorkspaceSidebar } from "../src/shared/workspaceSidebar";

describe("workspace sidebar state", () => {
  it("collapses into a reachable header action and keeps mounted input state across restore", async () => {
    const Harness = defineComponent({ setup() {
      const sidebar = createWorkspaceSidebar(), draft = ref("保留草稿");
      provideWorkspaceSidebar(sidebar);
      return () => h("div", [
        sidebar.collapsed.value ? h("button", { class: "workspace-sidebar-expand", onClick: sidebar.expand }, "展开侧边栏") : null,
        h(LearningSplitLayout, {}, { default: () => [h("section", { class: "practice-content" }, "题目"), h(LearningSupportPanel, { allowed: false, roleName: "教练", goals: [] }, { "goals-footer": () => h("input", { value: draft.value, onInput: (event: Event) => { draft.value = (event.target as HTMLInputElement).value; } }) })] }),
      ]);
    } });
    const wrapper = mount(Harness, { attachTo: document.body });
    const input = wrapper.get("input").element;
    await wrapper.get("input").setValue("修改后的草稿");
    await wrapper.get('[aria-label="折叠侧边栏"]').trigger("click");
    expect(wrapper.get(".learning-support").attributes("style")).toContain("display: none");
    expect(wrapper.get(".learning-support").attributes("hidden")).toBeDefined();
    expect(wrapper.get(".learning-support").attributes("inert")).toBeDefined();
    expect(wrapper.get(".learning-layout").classes()).toContain("is-sidebar-collapsed");
    expect(document.activeElement).toBe(wrapper.get(".workspace-sidebar-expand").element);
    expect(wrapper.get("input").element).toBe(input);
    await wrapper.get(".workspace-sidebar-expand").trigger("click");
    expect(wrapper.get(".learning-support").attributes("style") ?? "").not.toContain("display: none");
    expect((wrapper.get("input").element as HTMLInputElement).value).toBe("修改后的草稿");
    expect(document.activeElement).toBe(wrapper.get(".sidebar-toggle").element);
    wrapper.unmount();
  });
  it("lets keyboard users collapse the learning divider without changing its saved width", async () => {
    localStorage.setItem("pythontopia-learning-layout", JSON.stringify({ width: 30 }));
    const wrapper = mount(LearningSplitLayout);
    await wrapper.get('[role="separator"]').trigger("keydown", { key: "End" });
    expect(wrapper.classes()).toContain("is-sidebar-collapsed");
    expect(wrapper.find('[role="separator"]').exists()).toBe(false);
    expect(JSON.parse(localStorage.getItem("pythontopia-learning-layout")!)).toEqual({ width: 30 });
    wrapper.unmount();
    localStorage.clear();
  });
});
