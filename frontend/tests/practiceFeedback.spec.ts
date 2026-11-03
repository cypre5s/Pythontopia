import { describe, it, expect, vi, afterEach } from "vitest";
import { mount, flushPromises } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import PracticeWorkspace from "../src/features/PracticeWorkspace.vue";
import { useSession } from "../src/shared/session";
import { api } from "../src/shared/api";
import type { Submission } from "../src/shared/types";
afterEach(() => {
  vi.restoreAllMocks();
  localStorage.clear();
});
describe("public judge sample feedback", () => {
  it("renders public output and errors as text while excluding private extra fields", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const session = useSession();
    session.user = {
      id: 1,
      email: "fixture@example.test",
      name: "Fixture",
      role: "student",
    };
    session.classroomId = 1;
    session.world = {
      player: {
        map_key: "town_hub",
        tile_x: 22,
        tile_y: 36,
        direction: "down",
      },
      npcs: [],
      quests: [],
      chapters: [],
      flags: [],
    };
    vi.spyOn(session, "activity").mockResolvedValue({
      id: "code-test",
      chapter_id: 179,
      title: "公开反馈测试",
      kind: "practice",
      version: 1,
      npc_key: "mayor_py",
      assistant_allowed: false,
      material: null,
      questions: [
        {
          id: "q1",
          title: "测试题",
          kind: "python",
          prompt: "只验证公开样例反馈",
          options: [],
          samples: [{ input: "", output: "hello" }],
          version: 1,
        },
      ],
    });
    const result: Submission & {
      feedback: Submission["feedback"] & { hidden_cases: string[] };
    } = {
      id: "fixture-submission",
      activity_id: "code-test",
      question_id: "q1",
      version: 1,
      mode: "run",
      status: "runtime_error",
      created_at: "2026-11-03T00:00:00Z",
      feedback: {
        detail: "公开样例运行结束",
        status: "runtime_error",
        samples: [
          {
            status: "runtime_error",
            stdout: "<img src=x onerror=alert(1)>",
            stderr: "NameError: name wasd is not defined",
            compile_output: "Syntax diagnostics",
          },
        ],
        hidden_cases: ["PRIVATE_CASE_OUTPUT_DO_NOT_RENDER"],
      },
    };
    vi.spyOn(api, "post").mockResolvedValue(result);
    const wrapper = mount(PracticeWorkspace, {
      props: { activityId: "code-test" },
      global: {
        plugins: [pinia],
        stubs: {
          CodeEditor: {
            props: ["modelValue"],
            template:
              '<textarea aria-label="测试代码" :value="modelValue" @input="$emit(\'update:modelValue\',$event.target.value)" />',
          },
          AssistantPanel: true,
        },
      },
    });
    await flushPromises();
    await wrapper.find("textarea").setValue("wasd");
    await wrapper
      .findAll("button")
      .find((b) => b.text() === "运行公开样例")!
      .trigger("click");
    await flushPromises();
    const feedback = wrapper.find(".submission-result");
    expect(feedback.text()).toContain("公开样例 1 · 运行错误");
    expect(feedback.text()).toContain("<img src=x onerror=alert(1)>");
    expect(feedback.find("img").exists()).toBe(false);
    expect(feedback.text()).toContain("NameError: name wasd is not defined");
    expect(feedback.text()).toContain("Syntax diagnostics");
    expect(feedback.text()).not.toContain("PRIVATE_CASE_OUTPUT_DO_NOT_RENDER");
    wrapper.unmount();
  });
});
