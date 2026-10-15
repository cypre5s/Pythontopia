import { mount } from '@vue/test-utils';
import { defineComponent, ref } from 'vue';
import { describe, expect, it } from 'vitest';
import { useDraggable } from '@/composables/useDraggable';

const TestPanel = defineComponent({
  setup() {
    const panel = ref<HTMLElement | null>(null);
    const { onPointerDown } = useDraggable(panel, 'test-draggable', { top: 0, left: 0 });
    return { panel, onPointerDown };
  },
  template: '<aside ref="panel" @pointerdown="onPointerDown"><div role="button">Toggle</div></aside>',
});

describe('useDraggable', () => {
  it('does not prevent pointer clicks on elements with button semantics', () => {
    const wrapper = mount(TestPanel);
    const pointerDown = new MouseEvent('pointerdown', { bubbles: true, cancelable: true });

    wrapper.get('[role="button"]').element.dispatchEvent(pointerDown);

    expect(pointerDown.defaultPrevented).toBe(false);
    wrapper.unmount();
  });
});
