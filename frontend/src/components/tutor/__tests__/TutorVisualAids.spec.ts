import { flushPromises, mount } from '@vue/test-utils';
import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest';
import TutorVisualAids from '../TutorVisualAids.vue';

describe('TutorVisualAids', () => {
  beforeAll(() => {
    Object.defineProperty(SVGElement.prototype, 'getComputedTextLength', {
      configurable: true,
      value: () => 100,
    });
    Object.defineProperty(SVGElement.prototype, 'getBBox', {
      configurable: true,
      value: () => ({ x: 0, y: 0, width: 100, height: 20 }),
    });
  });

  afterAll(() => {
    delete (SVGElement.prototype as unknown as { getComputedTextLength?: () => number })
      .getComputedTextLength;
    delete (SVGElement.prototype as unknown as { getBBox?: () => DOMRect }).getBBox;
  });

  it('renders Mermaid labels as SVG text and removes unsafe generated CSS', async () => {
    const wrapper = mount(TutorVisualAids, {
      props: {
        aids: [{
          kind: 'mermaid',
          mermaid: [
            '%%{init: {"themeCSS": "body { position: fixed; background-image: url(//example.invalid/pixel); }"}}%%',
            'flowchart TD',
            '  A[Node]:::overlay',
            '  classDef overlay position:fixed,top:0,left:0,width:100vw,height:100vh,fill:red',
          ].join('\n'),
        }],
      },
    });
    await flushPromises();
    await vi.waitFor(() => expect(wrapper.find('[data-testid="tutor-visual-aids"]').exists()).toBe(true));

    const diagram = wrapper.get('[data-testid="tutor-visual-aids"] .aid-mermaid');
    expect(diagram.find('foreignObject').exists()).toBe(false);
    expect(diagram.text()).toContain('Node');
    expect(diagram.html()).not.toMatch(/position\s*:\s*fixed/i);
    expect(diagram.html()).not.toMatch(/url\s*\(/i);
  });
});
