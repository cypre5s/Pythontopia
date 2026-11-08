import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { getDocument, type PDFDocumentLoadingTask } from "pdfjs-dist";
import PdfReader from "../src/features/PdfReader.vue";
import { api } from "../src/shared/api";

vi.mock("pdfjs-dist", () => ({ getDocument: vi.fn(), GlobalWorkerOptions: { workerSrc: "" } }));
vi.mock("pdfjs-dist/build/pdf.worker.min.mjs?url", () => ({ default: "worker-url" }));
vi.mock("../src/shared/api", () => ({ api: { blob: vi.fn() } }));

const mounted: VueWrapper[] = [];
let width = 800, height = 500, resizeCallback: ResizeObserverCallback;
const renderPage = vi.fn(() => ({ promise: Promise.resolve(), cancel: vi.fn() }));
const page = { getViewport: ({ scale }: { scale: number }) => ({ width: 1200 * scale, height: 675 * scale }), render: renderPage };
const document = { numPages: 45, getPage: vi.fn(async () => page), destroy: vi.fn(async () => undefined) };
const blob = () => ({ arrayBuffer: async () => new ArrayBuffer(8) }) as Blob;

beforeEach(() => {
  vi.clearAllMocks();
  width = 800; height = 500;
  vi.mocked(api.blob).mockResolvedValue(blob());
  vi.mocked(getDocument).mockReturnValue({ promise: Promise.resolve(document), destroy: vi.fn(async () => undefined) } as unknown as PDFDocumentLoadingTask);
  document.getPage.mockResolvedValue(page);
  vi.spyOn(HTMLElement.prototype, "clientWidth", "get").mockImplementation(function (this: HTMLElement) { return this.classList.contains("pdf-page-stage") ? width : 0; });
  vi.spyOn(HTMLElement.prototype, "clientHeight", "get").mockImplementation(function (this: HTMLElement) { return this.classList.contains("pdf-page-stage") ? height : 0; });
  vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue({} as CanvasRenderingContext2D);
  vi.stubGlobal("ResizeObserver", class {
    constructor(callback: ResizeObserverCallback) { resizeCallback = callback; }
    observe() { /* Resize notifications are explicitly driven below. */ }
    disconnect() { /* Nothing to detach in jsdom. */ }
  });
  vi.stubGlobal("requestAnimationFrame", (callback: FrameRequestCallback) => { callback(0); return 1; });
  vi.stubGlobal("cancelAnimationFrame", vi.fn());
});
afterEach(() => {
  for (const wrapper of mounted.splice(0)) wrapper.unmount();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
async function reader() {
  const wrapper = mount(PdfReader, { props: { url: "/materials/186/file", page: 34, title: "Python 语言基础" } });
  mounted.push(wrapper);
  await flushPromises();
  return wrapper;
}
async function resize(nextWidth: number, nextHeight: number) {
  width = nextWidth; height = nextHeight;
  resizeCallback([{ contentRect: { width, height } } as ResizeObserverEntry], {} as ResizeObserver);
  await flushPromises();
}

describe("PDF reading surface", () => {
  it("fits both dimensions, rerenders on height-only changes and recovers after being hidden", async () => {
    const wrapper = await reader(), canvas = wrapper.get("canvas").element as HTMLCanvasElement;
    expect(canvas.style.width).toBe("800px");
    expect(canvas.style.height).toBe("450px");
    expect(wrapper.get(".reader-position").text()).toContain("Python 语言基础");
    expect(wrapper.get(".reader-position strong").text()).toBe("34");
    expect(wrapper.get('[aria-label="阅读位置"]').attributes("value")).toBe("34");
    expect(wrapper.get('[aria-label="阅读位置"]').attributes("max")).toBe("45");
    expect(wrapper.text()).not.toContain("提取文本");
    await resize(800, 270);
    expect(canvas.style.width).toBe("480px");
    expect(canvas.style.height).toBe("270px");
    const previousRenders = renderPage.mock.calls.length;
    await resize(0, 0);
    expect(renderPage).toHaveBeenCalledTimes(previousRenders);
    await resize(800, 500);
    expect(canvas.style.height).toBe("450px");
    expect(renderPage).toHaveBeenCalledTimes(previousRenders + 1);
    await wrapper.get('[aria-label="下一页 PDF"]').trigger("click");
    expect(wrapper.emitted("page")).toEqual([[35]]);
  });
  it("ignores a late file response after switching materials", async () => {
    let finish!: (value: Blob) => void;
    vi.mocked(api.blob).mockReturnValueOnce(new Promise(resolve => { finish = resolve; }));
    const wrapper = await reader(), firstSignal = vi.mocked(api.blob).mock.calls[0]![1];
    await wrapper.setProps({ url: "/materials/187/file", page: 1, title: "第二章" });
    await flushPromises();
    expect(firstSignal?.aborted).toBe(true);
    expect(getDocument).toHaveBeenCalledTimes(1);
    finish(blob()); await flushPromises();
    expect(getDocument).toHaveBeenCalledTimes(1);
    expect(wrapper.get(".reader-position").text()).toContain("第二章");
    expect(wrapper.attributes("aria-busy")).toBe("false");
  });
  it("shows page rendering failures and reloads through the visible retry action", async () => {
    document.getPage.mockRejectedValueOnce(new Error("课件无法读取"));
    const wrapper = await reader();
    expect(wrapper.get('[role="alert"]').text()).toContain("课件无法读取");
    expect(wrapper.get("canvas").isVisible()).toBe(false);
    await wrapper.get('[role="alert"] button').trigger("click");
    await vi.waitFor(async () => {
      await flushPromises();
      expect(wrapper.attributes("aria-busy")).toBe("false");
    });
    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    expect((wrapper.get("canvas").element as HTMLCanvasElement).style.display).not.toBe("none");
    expect((wrapper.get("canvas").element as HTMLCanvasElement).width).toBeGreaterThan(0);
    expect(wrapper.attributes("aria-busy")).toBe("false");
  });
});
