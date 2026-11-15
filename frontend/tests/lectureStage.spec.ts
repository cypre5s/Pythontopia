import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import LectureStage from "../src/features/learning/LectureStage.vue";
import { createPinia, setActivePinia } from "pinia";
import { api } from "../src/shared/api";

vi.mock("../src/shared/api", () => ({ api: { get: vi.fn(), blob: vi.fn() } }));
vi.mock("../src/shared/session", () => ({ useSession: () => ({ classroomId: 7 }) }));
const wrappers: VueWrapper[] = [];
const pages = [{ page_no: 1, text: "for i in range(3):", audio: true, audio_seconds: 20 }, { page_no: 3, text: "第三页", audio: true, audio_seconds: 30 }];
beforeEach(() => {
  vi.clearAllMocks();
  setActivePinia(createPinia());
  vi.mocked(api.get).mockResolvedValue({ pages });
  vi.mocked(api.blob).mockResolvedValue(new Blob(["audio"]));
  vi.stubGlobal("URL", { createObjectURL: vi.fn(() => "blob:current"), revokeObjectURL: vi.fn() });
  vi.spyOn(HTMLMediaElement.prototype, "pause").mockImplementation(() => undefined);
});
afterEach(() => { wrappers.splice(0).forEach(w => w.unmount()); vi.restoreAllMocks(); vi.unstubAllGlobals(); });
function player(pageNo = 1) {
  const w = mount(LectureStage, { props: { materialId: 3, pageNo, totalPages: 3 } });
  wrappers.push(w);
  return w;
}
it("hides an entirely silent material and restores controls when switching to narrated material", async () => {
  vi.mocked(api.get).mockResolvedValueOnce({ pages: [{ page_no: 1, text: "仅文字", audio: false }] });
  const w = player();
  await flushPromises();
  expect(w.find('.lecture-stage').exists()).toBe(false);
  expect(w.emitted('availability')?.at(-1)).toEqual([false]);
  expect(api.blob).not.toHaveBeenCalled();
  await w.setProps({ materialId: 8 });
  await flushPromises();
  expect(w.find('.lecture-stage').exists()).toBe(true);
  expect(w.emitted('availability')?.at(-1)).toEqual([true]);
});
it("shows an honest load failure instead of claiming the document is silent", async () => {
  vi.mocked(api.get).mockRejectedValueOnce(new Error('offline'));
  const w = player();
  await flushPromises();
  expect(w.text()).toContain('讲解暂时无法加载');
});
it('seeks across the full narrated document and applies the offset after page audio loads', async () => {
  const w = player();
  await flushPromises();
  expect(w.get('.lecture-track').attributes('max')).toBe('50');
  expect(w.text()).toContain('00:00 / 00:50');
  await w.get('.lecture-track').setValue('25');
  expect(w.emitted('page')?.at(-1)).toEqual([3]);
  await w.setProps({ pageNo: 3 });
  await flushPromises();
  const element = w.get('audio').element as HTMLAudioElement;
  Object.defineProperty(element, 'duration', { value: 30 });
  await w.get('audio').trigger('loadedmetadata');
  expect(element.currentTime).toBe(5);
  expect(w.text()).toContain('00:25 / 00:50');
  expect(w.get('.lecture-track').attributes('style')).toContain('50%');
  expect(w.emitted('timeline')?.at(-1)).toEqual([[{ pageNo: 1, start: 0, seconds: 20 }, { pageNo: 3, start: 20, seconds: 30 }]]);
});
it("does not request server-rendered page images, even between narrated pages", async () => {
  const w = player(2);
  await flushPromises();
  expect(api.blob).not.toHaveBeenCalled();
  expect(w.text()).toContain("这一页还没有");
  expect(w.text()).not.toContain("就这页提问");
  expect(w.text()).not.toContain("教师已审校");
});
it("drops an old page's delayed audio and preserves Python whitespace", async () => {
  let finish!: (blob: Blob) => void;
  vi.mocked(api.blob).mockReturnValueOnce(new Promise(resolve => { finish = resolve; }));
  const w = player();
  await flushPromises();
  expect(w.text()).toContain("for i in range(3):");
  await w.setProps({ pageNo: 3 });
  await flushPromises();
  finish(new Blob(["old"]));
  await flushPromises();
  expect(URL.createObjectURL).toHaveBeenCalledTimes(1);
  expect(w.get("audio").attributes("src")).toBe("blob:current");
  await w.setProps({ pageNo: 2 });
  expect(w.find("audio").exists()).toBe(false);
  expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:current");
});
it("ignores a material response arriving after a switch or unmount", async () => {
  let finish!: (data: unknown) => void;
  vi.mocked(api.get).mockReturnValueOnce(new Promise(resolve => { finish = resolve; }));
  const w = player();
  await w.setProps({ materialId: 9 });
  await flushPromises();
  finish({ pages: [{ page_no: 1, text: "旧资料", audio: true }] });
  await flushPromises();
  expect(w.text()).not.toContain("旧资料");
  expect(api.blob).toHaveBeenCalledTimes(1);
  w.unmount();
  wrappers.splice(wrappers.indexOf(w), 1);
  expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:current");
});
