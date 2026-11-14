import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import LectureStage from "../src/features/LectureStage.vue";
import { api } from "../src/shared/api";

vi.mock("../src/shared/api", () => ({ api: { get: vi.fn(), blob: vi.fn() } }));
vi.mock("../src/shared/session", () => ({ useSession: () => ({ classroomId: 7 }) }));
const wrappers: VueWrapper[] = [];
const pages = [{ page_no: 1, text: "for i in range(3):", audio: true }, { page_no: 3, text: "第三页", audio: true }];
beforeEach(() => {
  vi.clearAllMocks();
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
it("does not request server-rendered page images, even between narrated pages", async () => {
  const w = player(2);
  await flushPromises();
  expect(api.blob).not.toHaveBeenCalled();
  expect(w.text()).toContain("这一页还没有");
  await w.get(".town-secondary").trigger("click");
  expect(w.emitted("ask")).toHaveLength(1);
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
