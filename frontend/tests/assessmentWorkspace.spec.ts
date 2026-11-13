import { mount, flushPromises } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { afterEach, describe, expect, it, vi } from "vitest";
import PracticeWorkspace from "../src/features/PracticeWorkspace.vue";
import { useSession } from "../src/shared/session";
import { api } from "../src/shared/api";
import type { AssessmentAttempt, AssessmentAnswer, Submission } from "../src/shared/types";
function setup(kind = "assessment") {
  const pinia = createPinia(); setActivePinia(pinia); const session = useSession();
  session.user = { id: 1, name: "测试居民", student_id: "124639931179929", role: "student" }; session.classroomId = 2;
  session.world = { player: { map_key: "town_hub", tile_x: 22, tile_y: 36, direction: "down" }, npcs: [], quests: [], chapters: [], flags: [] };
  const questions = ["q1", "q2"].map(id => ({ id, title: id, kind: "boolean" as const, prompt: `判断 ${id}`, options: [], samples: [] }));
  vi.spyOn(session, "activity").mockResolvedValue({ id: "test", chapter_id: 179, title: "测验", kind, npc_key: "arena_guard", assistant_allowed: false, material: null, questions });
  vi.stubGlobal("matchMedia", vi.fn(() => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() })));
  return { pinia, session };
}
function paper(overrides: Partial<AssessmentAttempt> = {}): AssessmentAttempt { return { id: null, status: "draft", revision: 0, locked: false, question_ids: ["q1", "q2"], answers: [], results: [], correct_count: 0, total: 2, score: null, max_score: 100, completed: false, passed: false, legacy: false, ...overrides }; }
function result(id: string, status: Submission["status"]): Submission { return { id: `submission-${id}`, activity_id: "test", question_id: id, mode: "submit", status, feedback: { detail: status === "accepted" ? "回答正确" : "请重新思考" }, created_at: "2026-11-08T00:00:00Z" }; }
afterEach(async () => { await flushPromises(); vi.restoreAllMocks(); vi.unstubAllGlobals(); localStorage.clear(); sessionStorage.clear(); });
describe("assessment and practice presentation", () => {
  it("resumes server draft saves after a partial legacy assessment unlocks from grading", async () => {
    vi.useFakeTimers({ toFake: ["setTimeout", "clearTimeout"] });
    const { pinia } = setup();
    localStorage.setItem("draft:1:2:q2", JSON.stringify({ answer: "false", code: "" }));
    const priorAnswers: AssessmentAnswer[] = [{ question_id: "q1", answer: true }];
    vi.spyOn(api, "get").mockResolvedValueOnce(paper({ status: "grading", locked: true, legacy: true, answers: priorAnswers, results: [result("q1", "queued")] }))
      .mockResolvedValueOnce(paper({ id: "attempt", revision: 3, answers: priorAnswers }));
    const put = vi.spyOn(api, "put").mockImplementation(async (_path, body) => paper({ id: "attempt", revision: 4, answers: (body as { answers: AssessmentAnswer[] }).answers }) as never);
    const wrapper = mount(PracticeWorkspace, { props: { activityId: "test" }, global: { plugins: [pinia], stubs: { AssistantPanel: true } } });
    try {
      await flushPromises();
      expect(wrapper.get('input[value="true"]').attributes("disabled")).toBeDefined();
      expect(wrapper.find(".assessment-score").exists()).toBe(false);
      await vi.advanceTimersByTimeAsync(1500);
      await flushPromises();
      expect(wrapper.get('input[value="true"]').attributes("disabled")).toBeUndefined();
      await wrapper.findAll("button").find(button => button.text().includes("下一题"))!.trigger("click");
      expect(wrapper.get('input[value="false"]').element).toHaveProperty("checked", true);
      await wrapper.get('input[value="true"]').setValue();
      await vi.advanceTimersByTimeAsync(450);
      await flushPromises();
      expect(put).toHaveBeenCalledWith("/activities/test/assessment", { classroom_id: 2, revision: 3, answers: [{ question_id: "q1", answer: true }, { question_id: "q2", answer: true }] });
    } finally {
      wrapper.unmount(); await flushPromises(); vi.useRealTimers();
    }
  });
  it("keeps sealed question identities when the current teacher question list changes", async () => {
    const { pinia } = setup();
    vi.spyOn(api, "get").mockResolvedValue(paper({ status: "graded", locked: true, completed: true, question_ids: ["removed-question"], total: 1, score: 0, answers: [{ question_id: "removed-question", answer: "original answer" }], results: [result("removed-question", "wrong_answer")] }));
    const wrapper = mount(PracticeWorkspace, { props: { activityId: "test" }, global: { plugins: [pinia], stubs: { AssistantPanel: true } } });
    await flushPromises();
    expect(wrapper.findAll(".practice-question-card")).toHaveLength(1);
    expect(wrapper.get(".practice-question-card").attributes("data-question-id")).toBe("removed-question");
    expect(wrapper.get(".practice-question-card h3").text()).toContain("题目已调整");
    expect(wrapper.get('input[placeholder="输入你的答案"]').element).toHaveProperty("value", "original answer");
    expect(wrapper.get(".support-goal-progress").text()).toContain("已判定 1 / 1 题");
    wrapper.unmount(); await flushPromises();
  });
  it("shares answers between single and paper views, submits one paper and locks the marked result", async () => {
    const { pinia } = setup();
    vi.spyOn(api, "get").mockResolvedValue(paper());
    vi.spyOn(api, "put").mockImplementation(async (_path, body) => paper({ id: "attempt", revision: 1, answers: (body as { answers: AssessmentAnswer[] }).answers }) as never);
    const post = vi.spyOn(api, "post").mockImplementation(async (_path, body) => paper({ id: "attempt", status: "graded", revision: 2, locked: true, answers: (body as { answers: AssessmentAnswer[] }).answers, results: [result("q1", "wrong_answer"), result("q2", "accepted")], correct_count: 1, score: 50, completed: true }) as never);
    const wrapper = mount(PracticeWorkspace, { props: { activityId: "test", storySessionId: "story" }, global: { plugins: [pinia], stubs: { AssistantPanel: true } } });
    await flushPromises();
    expect(wrapper.findAll(".practice-question-card")).toHaveLength(1);
    expect(wrapper.find(".question-list,.question-position,.learning-meta,.answer-hint,.draft-note").exists()).toBe(false);
    await wrapper.get('input[name="answer-q1"][value="false"]').setValue();
    await wrapper.findAll("button").find(button => button.text() === "试卷模式")!.trigger("click");
    expect(wrapper.findAll(".practice-question-card")).toHaveLength(2);
    expect(wrapper.get('input[name="answer-q1"][value="false"]').element).toHaveProperty("checked", true);
    await wrapper.get('input[name="answer-q2"][value="true"]').setValue();
    expect(post).not.toHaveBeenCalled();
    await wrapper.findAll("button").find(button => button.text() === "快速跳转")!.trigger("click");
    await wrapper.findAll("button").find(button => button.text() === "提交试卷")!.trigger("click");
    await flushPromises();
    expect(post).toHaveBeenCalledOnce();
    expect(post).toHaveBeenCalledWith("/activities/test/assessment/submit", expect.objectContaining({ classroom_id: 2, answers: [{ question_id: "q1", answer: false }, { question_id: "q2", answer: true }] }));
    expect(wrapper.get(".assessment-score").text()).toBe("50 / 100 分");
    expect(wrapper.get(".assessment-actions .assessment-answer-count").text()).toBe("答对 1 / 2 题");
    expect(wrapper.findAll("input").every(input => input.attributes("disabled") !== undefined)).toBe(true);
    expect(wrapper.findAll(".submission-result")).toHaveLength(2);
    expect(wrapper.text()).not.toContain("submission-q1");
    expect(wrapper.emitted("complete")).toBeUndefined();
    await wrapper.findAll("button").find(button => button.text() === "完成测验")!.trigger("click");
    expect(wrapper.emitted("complete")).toEqual([[]]);
    wrapper.unmount(); await flushPromises();
  });
  it("restores a legacy locked paper without creating or modifying grades", async () => {
    const { pinia } = setup();
    vi.spyOn(api, "get").mockResolvedValue(paper({ status: "graded", locked: true, legacy: true, completed: true, correct_count: 1, score: 50, answers: [{ question_id: "q1", answer: true }, { question_id: "q2", answer: false }], results: [result("q1", "accepted"), result("q2", "wrong_answer")] }));
    const post = vi.spyOn(api, "post"), put = vi.spyOn(api, "put");
    const wrapper = mount(PracticeWorkspace, { props: { activityId: "test" }, global: { plugins: [pinia], stubs: { AssistantPanel: true } } });
    await flushPromises();
    expect(wrapper.get('input[value="true"]').attributes("disabled")).toBeDefined();
    expect(wrapper.findAll("button").some(button => button.text() === "提交试卷")).toBe(false);
    expect(post).not.toHaveBeenCalled(); expect(put).not.toHaveBeenCalled();
    wrapper.unmount(); await flushPromises();
  });
  it("keeps practice single-question only and preserves independent answers for navigation", async () => {
    const { pinia } = setup("practice");
    vi.spyOn(api, "get").mockResolvedValue({ accepted_question_ids: [], completed: false });
    const wrapper = mount(PracticeWorkspace, { props: { activityId: "test" }, global: { plugins: [pinia], stubs: { AssistantPanel: true } } });
    await flushPromises();
    expect(wrapper.find('[aria-label="答题模式"]').exists()).toBe(false);
    await wrapper.get('input[value="false"]').setValue();
    await wrapper.findAll("button").find(button => button.text().includes("下一题"))!.trigger("click");
    expect(wrapper.findAll('input:checked')).toHaveLength(0);
    await wrapper.get('input[value="true"]').setValue();
    await wrapper.findAll("button").find(button => button.text().includes("上一题"))!.trigger("click");
    expect(wrapper.get('input[value="false"]').element).toHaveProperty("checked", true);
    wrapper.unmount();
  });
  it("freezes an unconfirmed practice answer and retries the exact original submission", async () => {
    const { pinia } = setup("practice");
    const post = vi.spyOn(api, "post").mockRejectedValueOnce(new Error("网络中断，请重试")).mockResolvedValueOnce(result("q1", "wrong_answer"));
    const wrapper = mount(PracticeWorkspace, { props: { activityId: "test" }, global: { plugins: [pinia], stubs: { AssistantPanel: true } } });
    await flushPromises();
    await wrapper.get('input[value="false"]').setValue();
    await wrapper.findAll("button").find(button => button.text() === "提交答案")!.trigger("click");
    await flushPromises();
    expect(wrapper.get('input[value="true"]').attributes("disabled")).toBeDefined();
    const firstBody = post.mock.calls[0]![1];
    await wrapper.findAll("button").find(button => button.text() === "重试提交")!.trigger("click");
    await flushPromises();
    expect(post.mock.calls[1]![1]).toEqual(firstBody);
    expect(wrapper.get('input[value="true"]').attributes("disabled")).toBeUndefined();
    wrapper.unmount();
  });
  it("waits for an in-flight paper submission after leaving the workspace before development reset", async () => {
    const { pinia, session } = setup();
    session.user!.dev_tools_enabled = true;
    session.classes = [{ id: 2, name: "验收班级", teacher_id: 4 }];
    let resolveSubmission!: (attempt: AssessmentAttempt) => void;
    const submission = new Promise<AssessmentAttempt>(resolve => { resolveSubmission = resolve; });
    vi.spyOn(api, "get").mockResolvedValue(paper());
    vi.spyOn(api, "put").mockImplementation(async (_path, body) => paper({ revision: 1, answers: (body as { answers: AssessmentAnswer[] }).answers }) as never);
    const post = vi.spyOn(api, "post").mockImplementation(async path => path === "/dev/progress/reset" ? { ok: true, user_id: 1, classroom_id: 2 } : submission as never);
    const wrapper = mount(PracticeWorkspace, { props: { activityId: "test" }, global: { plugins: [pinia], stubs: { AssistantPanel: true } } });
    await flushPromises();
    await wrapper.get('input[value="false"]').setValue();
    await wrapper.findAll("button").find(button => button.text().includes("下一题"))!.trigger("click");
    await wrapper.get('input[value="true"]').setValue();
    await wrapper.findAll("button").find(button => button.text() === "提交试卷")!.trigger("click");
    await flushPromises();
    expect(post).toHaveBeenCalledOnce();
    wrapper.unmount();
    const restarting = session.restartCurrentClassroom();
    await flushPromises();
    expect(post.mock.calls.some(([path]) => path === "/dev/progress/reset")).toBe(false);
    resolveSubmission(paper({ status: "graded", locked: true, completed: true }));
    expect(await restarting).toBe(true);
    expect(post).toHaveBeenLastCalledWith("/dev/progress/reset", { classroom_id: 2, confirmed: true });
  });
});
import * as pythonRunner from '../src/shared/pythonRunner';
it('merges programming assessment actions while public samples are running', async () => {
  const {pinia,session}=setup();
  const activity=await session.activity('test');
  vi.mocked(session.activity).mockResolvedValue({...activity,questions:[{id:'q1',title:'编程题',kind:'python',prompt:'输出',options:[],starter_code:'print(1)',samples:[{input:'',output:'1'}]},activity.questions[1]!]});
  vi.spyOn(api,'get').mockResolvedValue(paper());
  let finish!: (value: {stdout:string;stderr:string}) => void;
  vi.spyOn(pythonRunner,'runPython').mockImplementation(()=>new Promise(resolve=>{finish=resolve;}));
  const wrapper=mount(PracticeWorkspace,{props:{activityId:'test'},global:{plugins:[pinia],stubs:{CodeEditor:true,AssistantPanel:true}}});
  await flushPromises();
  expect(wrapper.find('.assessment-top-submit').exists()).toBe(true);
  await wrapper.findAll('button').find(button=>button.text()==='运行公开样例')!.trigger('click');
  expect(wrapper.find('.assessment-top-submit').exists()).toBe(false);
  expect(wrapper.find('.judging-action').exists()).toBe(true);
  finish({stdout:'1',stderr:''});await flushPromises();
  expect(wrapper.find('.assessment-top-submit').exists()).toBe(true);
  wrapper.unmount();
});
