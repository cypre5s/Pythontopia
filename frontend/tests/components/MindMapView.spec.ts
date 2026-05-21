import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import MindMapView from '@/components/workshop/MindMapView.vue';

describe('MindMapView', () => {
  it('renders mermaid source when valid', () => {
    const wrapper = mount(MindMapView, {
      props: {
        content: {
          mermaid: 'graph TD\n  A[KC1] --> B[KC2]',
          kc_map: { 'PY43-KC-001': ['PY43-KC-002'] },
        },
      },
    });
    expect(wrapper.find('[data-testid="mermaid-source"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="mermaid-error"]').exists()).toBe(false);
  });

  it('shows error state when mermaid header invalid', () => {
    const wrapper = mount(MindMapView, {
      props: {
        content: { mermaid: 'this is not mermaid', kc_map: {} },
      },
    });
    expect(wrapper.find('[data-testid="mermaid-error"]').exists()).toBe(true);
  });
});
