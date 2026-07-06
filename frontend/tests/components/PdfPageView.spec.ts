import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';

// vi.hoisted 让 loadPdfPage / renderPdfPageToCanvas 在 vi.mock factory 提前到顶部时可见。
// 必须用 hoisted，否则 vi.mock 工厂引用未初始化的 const。
const mocks = vi.hoisted(() => ({
  loadPdfPage: vi.fn(),
  renderPdfPageToCanvas: vi.fn(),
}));

vi.mock('@/lib/pdfjsLoader', () => ({
  loadPdfPage: mocks.loadPdfPage,
  renderPdfPageToCanvas: mocks.renderPdfPageToCanvas,
  isPdfWorkerInitialisedForTest: vi.fn(() => false),
  resetPdfWorkerForTest: vi.fn(),
}));

import PdfPageView from '@/components/library/PdfPageView.vue';

const { loadPdfPage, renderPdfPageToCanvas } = mocks;

class StubResizeObserver {
  observe = vi.fn();
  unobserve = vi.fn();
  disconnect = vi.fn();
}

beforeEach(() => {
  loadPdfPage.mockReset();
  renderPdfPageToCanvas.mockReset();
  Object.defineProperty(globalThis, 'ResizeObserver', {
    writable: true,
    configurable: true,
    value: StubResizeObserver,
  });
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('PdfPageView', () => {
  it('mount 时按 docId/pageNo 拉 PDF 并渲染', async () => {
    const pdfStub = { numPages: 12 };
    const pageStub = { getViewport: vi.fn(() => ({ width: 600, height: 800 })), render: vi.fn() };
    loadPdfPage.mockResolvedValue({ pdf: pdfStub, page: pageStub });
    renderPdfPageToCanvas.mockResolvedValue({ renderedWidth: 600, renderedHeight: 800 });

    const wrapper = mount(PdfPageView, {
      props: { docId: 186, pageNo: 1 },
      attachTo: document.body,
    });
    await flushPromises();

    expect(loadPdfPage).toHaveBeenCalledWith({
      url: '/api/course/documents/186/pdf',
      pageNo: 1,
    });
    expect(renderPdfPageToCanvas).toHaveBeenCalled();
    expect(wrapper.find('[data-testid="library-pdf-canvas"]').exists()).toBe(true);
    expect(wrapper.text()).toContain('第 1 / 12 页 · 原版 PDF');
    wrapper.unmount();
  });

  it('切页时重新调 loadPdfPage', async () => {
    const pdfStub = { numPages: 5 };
    const pageStub = { getViewport: vi.fn(() => ({ width: 600, height: 800 })), render: vi.fn() };
    loadPdfPage.mockResolvedValue({ pdf: pdfStub, page: pageStub });
    renderPdfPageToCanvas.mockResolvedValue({ renderedWidth: 600, renderedHeight: 800 });

    const wrapper = mount(PdfPageView, {
      props: { docId: 187, pageNo: 1 },
      attachTo: document.body,
    });
    await flushPromises();

    expect(loadPdfPage).toHaveBeenCalledTimes(1);
    await wrapper.setProps({ pageNo: 4 });
    await flushPromises();
    expect(loadPdfPage).toHaveBeenLastCalledWith({
      url: '/api/course/documents/187/pdf',
      pageNo: 4,
    });
    wrapper.unmount();
  });

  it('load 失败时显示错误信息（不显示 canvas 文字）', async () => {
    loadPdfPage.mockRejectedValue(new Error('网络拒绝 / 404'));

    const wrapper = mount(PdfPageView, {
      props: { docId: 186, pageNo: 1 },
      attachTo: document.body,
    });
    await flushPromises();

    expect(wrapper.find('[data-testid="library-pdf-error"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="library-pdf-error"]').text()).toContain('网络拒绝 / 404');
    expect(renderPdfPageToCanvas).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('pdfBase 可被覆盖（便于 e2e 接 mock server）', async () => {
    const pdfStub = { numPages: 1 };
    const pageStub = { getViewport: vi.fn(() => ({ width: 100, height: 100 })), render: vi.fn() };
    loadPdfPage.mockResolvedValue({ pdf: pdfStub, page: pageStub });
    renderPdfPageToCanvas.mockResolvedValue({ renderedWidth: 100, renderedHeight: 100 });

    const wrapper = mount(PdfPageView, {
      props: { docId: 192, pageNo: 9, pdfBase: '/__mock__/course' },
      attachTo: document.body,
    });
    await flushPromises();

    expect(loadPdfPage).toHaveBeenCalledWith({
      url: '/__mock__/course/192/pdf',
      pageNo: 9,
    });
    wrapper.unmount();
  });
});
