import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { defineComponent } from "vue";
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { api } from "../src/shared/api";
import type { Activity, ActivityProgress, Material } from "../src/shared/types";
import { useLessonSource } from "../src/features/learning/useLessonSource";

const material = (id: number, kind = "text"): Material => ({
  id,
  kind,
  title: `资料 ${id}`,
  status: "ready",
  pages: [{ id: id * 10, page_no: 1, title: "第一页", text: "正文" }],
});
const activity = (id: string): Activity => ({
  id,
  role: null,
  chapter_id: 1,
  title: id,
  kind: "lesson",
  npc_key: null,
  assistant_allowed: false,
  questions: [],
  material: material(1),
  completion: { required_page_ids: [10] },
});
const progress = (page: number): ActivityProgress => ({
  page_no: page,
  visited_pages: [],
  completed: false,
});
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: Error) => void;
  const promise = new Promise<T>((done, fail) => {
    resolve = done;
    reject = fail;
  });
  return { promise, resolve, reject };
}
const wrappers: VueWrapper[] = [];
function setup(useActivity = false) {
  const owner = {
    active: true,
    materialId: useActivity ? undefined : 1,
    activityId: "first",
  };
  let source!: ReturnType<typeof useLessonSource>;
  const ready = vi.fn();
  const fetchActivity = vi.fn(async (id: string) => activity(id));
  const wrapper = mount(
    defineComponent({
      setup() {
        source = useLessonSource({
          userId: 1,
          classroomId: 2,
          currentOwner: () => owner.active,
          activityId: () => owner.activityId,
          materialId: () => owner.materialId,
          initialPage: () => undefined,
          fetchActivity,
          beforeLoad: vi.fn(),
          prepareVideo: vi.fn(),
          ready,
        });
        return () => null;
      },
    }),
  );
  wrappers.push(wrapper);
  return { source, owner, ready, wrapper };
}

beforeEach(() => {
  vi.stubGlobal("URL", {
    createObjectURL: vi.fn(() => "blob:current"),
    revokeObjectURL: vi.fn(),
  });
});
afterEach(() => {
  wrappers.splice(0).forEach((wrapper) => wrapper.unmount());
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
  sessionStorage.clear();
});

describe("lesson source request ownership", () => {
  it("keeps the new activity progress when the preceding load finishes late", async () => {
    const previous = deferred<ActivityProgress>();
    vi.spyOn(api, "get").mockImplementation(
      async (path) =>
        (path.includes("/first/") ? previous.promise : progress(2)) as never,
    );
    const { source, owner, ready } = setup(true);
    const first = source.load();
    await flushPromises();
    owner.activityId = "second";
    await source.load();
    previous.resolve(progress(9));
    await first;
    expect(source.activity.value?.id).toBe("second");
    expect(source.progress.value?.page_no).toBe(2);
    expect(ready).toHaveBeenCalledTimes(1);
  });

  it("does not create a media URL after the reader has closed", async () => {
    const download = deferred<Blob>();
    vi.spyOn(api, "get").mockResolvedValue(material(1, "video"));
    const blob = vi.spyOn(api, "blob").mockReturnValue(download.promise);
    const { source, wrapper, ready } = setup();
    const pending = source.load();
    await flushPromises();
    const signal = blob.mock.calls[0]![1]!;
    wrapper.unmount();
    download.resolve(new Blob(["video"]));
    await pending;
    expect(signal.aborted).toBe(true);
    expect(URL.createObjectURL).not.toHaveBeenCalled();
    expect(ready).not.toHaveBeenCalled();
  });

  it("releases the current video URL when another source replaces it", async () => {
    vi.spyOn(api, "get")
      .mockResolvedValueOnce(material(1, "video"))
      .mockResolvedValueOnce(material(2));
    vi.spyOn(api, "blob").mockResolvedValue(new Blob(["video"]));
    const { source, owner } = setup();
    await source.load();
    expect(source.mediaBlob.value).toBe("blob:current");
    owner.materialId = 2;
    await source.load();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:current");
    expect(source.mediaBlob.value).toBe("");
    expect(source.material.value?.id).toBe(2);
  });

  it("ignores a previous account's failed source request", async () => {
    const download = deferred<Material>();
    vi.spyOn(api, "get").mockReturnValue(download.promise);
    const { source, owner, ready } = setup();
    const pending = source.load();
    owner.active = false;
    download.reject(new Error("已退出的账号"));
    await pending;
    expect(source.error.value).toBe("");
    expect(ready).not.toHaveBeenCalled();
  });

  it("keeps the last citation selected when requests complete in reverse order", async () => {
    const first = deferred<Material>();
    vi.spyOn(api, "get").mockImplementation(
      async (path) =>
        (path.startsWith("/materials/2?")
          ? first.promise
          : material(3)) as never,
    );
    const { source } = setup();
    const pending = source.showCitation(2, 1);
    expect(await source.showCitation(3, 1)).toBe(true);
    first.resolve(material(2));
    expect(await pending).toBe(false);
    expect(source.material.value?.id).toBe(3);
  });
});
