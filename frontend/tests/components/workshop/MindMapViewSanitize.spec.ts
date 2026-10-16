import { flushPromises, mount } from '@vue/test-utils';
import { beforeAll, describe, expect, it, vi } from 'vitest';
import MindMapView from '@/components/workshop/MindMapView.vue';

// jsdom 没有 SVG 布局引擎，mermaid 会在 getBBox 上抛错；补最小 polyfill 让真实渲染跑完，
// 这样才能断言「进 innerHTML 的到底是什么」。
beforeAll(() => {
  const proto = (globalThis as unknown as { SVGElement: { prototype: Record<string, unknown> } })
    .SVGElement.prototype;
  proto.getBBox = () => ({ x: 0, y: 0, width: 60, height: 20 });
  proto.getComputedTextLength = () => 60;
  proto.getScreenCTM = () => ({ a: 1, b: 0, c: 0, d: 1, e: 0, f: 0 });
});

/** 挂载并等到 mermaid 真的把清洗后的 SVG 写进容器（动态 import + 异步渲染，不能只靠 flushPromises）。 */
async function renderMindMap(mermaidSource: string) {
  const wrapper = mount(MindMapView, {
    props: { content: { mermaid: mermaidSource, kc_map: {} } },
    attachTo: document.body,
  });
  await flushPromises();
  await vi.waitFor(
    () => {
      expect(wrapper.find('[data-testid="mermaid-render"]').element.innerHTML).not.toBe('');
    },
    { timeout: 5000, interval: 20 },
  );
  return wrapper;
}

describe('MindMapView · mermaid 产物消毒', () => {
  it('渲染结果不含 foreignObject，且节点文字仍在（htmlLabels:false + DOMPurify）', async () => {
    const wrapper = await renderMindMap('graph TD\n  A[KC1] --> B[KC2]');

    const host = wrapper.find('[data-testid="mermaid-render"]');
    expect(host.html()).toContain('<svg');
    expect(host.html()).not.toContain('foreignObject');
    expect(host.text()).toContain('KC1');
    expect(host.text()).toContain('KC2');
    wrapper.unmount();
  });

  it('保留 mermaid 主题着色（消毒没有把图洗成空骨架）', async () => {
    const wrapper = await renderMindMap('graph LR\n  A[KC1] --> B[KC2]');

    expect(wrapper.find('[data-testid="mermaid-render"]').html())
      .toMatch(/#fff7e6|rgb\(255,\s*247,\s*230\)/i);
    wrapper.unmount();
  });

  it('节点标签里的 HTML 不会变成可执行标记', async () => {
    const wrapper = await renderMindMap('graph TD\n  A["<img src=x onerror=alert(1)>"] --> B[safe]');

    const html = wrapper.find('[data-testid="mermaid-render"]').html();
    expect(html).not.toContain('onerror=alert');
    expect(html).not.toContain('<img');
    wrapper.unmount();
  });
});
