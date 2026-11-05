import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import TownBackdrop from "../src/features/TownBackdrop.vue";
import { paintMapPreview } from "../src/game/config/worldRender";
import { getWorldMap, mapRenderBounds, worldConfig } from "../src/game/config/worldConfig";
import { arrivalActive, arrivalCamera, arrivalFinished, backdropReady, resourceError, startArrival } from "../src/shared/arrival";

vi.mock("../src/game/config/worldRender", () => ({ paintMapPreview: vi.fn(async () => undefined) }));
const mounted: VueWrapper[] = [];
const originalAnimate = Object.getOwnPropertyDescriptor(HTMLElement.prototype, "animate");
let animate = vi.fn();
beforeEach(() => {
  setActivePinia(createPinia());
  startArrival(); backdropReady.value = false; resourceError.value = "";
  vi.mocked(paintMapPreview).mockReset().mockResolvedValue(undefined);
  animate = vi.fn(() => ({ finished: Promise.resolve(), cancel: vi.fn() }));
  Object.defineProperty(HTMLElement.prototype, "animate", { configurable: true, value: animate });
});
afterEach(() => {
  for (const wrapper of mounted.splice(0)) wrapper.unmount();
  vi.restoreAllMocks();
  if (originalAnimate) Object.defineProperty(HTMLElement.prototype, "animate", originalAnimate);
  else delete (HTMLElement.prototype as { animate?: unknown }).animate;
  arrivalActive.value = false; arrivalCamera.value = null; backdropReady.value = false;
});
async function backdrop() {
  const wrapper = mount(TownBackdrop);
  mounted.push(wrapper);
  await flushPromises();
  return wrapper;
}
describe("the arrival backdrop shares the real map composition", () => {
  it("waits for the shared painter and covers the whole exterior render bounds", async () => {
    let ready!: () => void;
    vi.mocked(paintMapPreview).mockReturnValue(new Promise<void>(resolve => { ready = resolve; }));
    const wrapper = await backdrop();
    const bounds = mapRenderBounds(getWorldMap(worldConfig.default_spawn.map_key));
    const canvas = wrapper.get("canvas");
    expect(canvas.attributes("width")).toBe(String(bounds.width * 16));
    expect(canvas.attributes("height")).toBe(String(bounds.height * 16));
    expect(paintMapPreview).toHaveBeenCalledWith(canvas.element, worldConfig.default_spawn.map_key, expect.any(AbortSignal));
    expect(backdropReady.value).toBe(false);
    ready(); await flushPromises();
    expect(backdropReady.value).toBe(true);
  });
  it("aligns negative render origins with the live camera before revealing the world", async () => {
    const wrapper = await backdrop();
    const bounds = mapRenderBounds(getWorldMap(worldConfig.default_spawn.map_key));
    vi.spyOn(wrapper.get("canvas").element, "getBoundingClientRect").mockReturnValue({ x: -60, y: -40, width: 900, height: 700 } as DOMRect);
    arrivalCamera.value = { x: 120, y: 240, zoom: 2, map: worldConfig.default_spawn.map_key };
    await flushPromises();
    expect(animate).toHaveBeenCalledTimes(2);
    expect(animate.mock.calls[0]?.[0]).toEqual([
      { width: "900px", height: "700px", transform: "translate(-60px, -40px)" },
      { width: `${bounds.width * 32}px`, height: `${bounds.height * 32}px`, transform: `translate(${(bounds.x * 16 - 120) * 2}px, ${(bounds.y * 16 - 240) * 2}px)` },
    ]);
    expect(arrivalFinished.value).toBe(true);
    expect(arrivalActive.value).toBe(false);
  });
  it("reveals indoor saves with a fade instead of flying the town background into another map", async () => {
    const wrapper = await backdrop();
    arrivalCamera.value = { x: -80, y: -60, zoom: 2.5, map: "library" };
    await flushPromises();
    expect(animate).toHaveBeenCalledTimes(1);
    expect(animate.mock.contexts[0]).toBe(wrapper.get(".town-backdrop").element);
    expect(arrivalFinished.value).toBe(true);
  });
  it("does not publish readiness or resource errors after an interrupted load", async () => {
    let reject!: (error: Error) => void;
    vi.mocked(paintMapPreview).mockReturnValue(new Promise<void>((_resolve, fail) => { reject = fail; }));
    const wrapper = await backdrop();
    wrapper.unmount();
    reject(new Error("interrupted")); await flushPromises();
    expect(backdropReady.value).toBe(false);
    expect(resourceError.value).toBe("");
  });
});
