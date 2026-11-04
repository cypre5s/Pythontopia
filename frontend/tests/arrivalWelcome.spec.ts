import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import ArrivalWelcome from '../src/features/ArrivalWelcome.vue';
describe('arrival input gate', () => {
  it('waits for the world and consumes the continue key before game input', async () => {
    const wrapper = mount(ArrivalWelcome, {props:{ready:false,name:'学生'},attachTo:document.body});
    try {
      window.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',cancelable:true}));
      expect(wrapper.emitted('continue')).toBeUndefined();
      await wrapper.setProps({ready:true});
      window.dispatchEvent(new KeyboardEvent('keydown',{key:'Tab',cancelable:true}));
      window.dispatchEvent(new KeyboardEvent('keydown',{key:'w',repeat:true,cancelable:true}));
      expect(wrapper.emitted('continue')).toBeUndefined();
      const event = new KeyboardEvent('keydown',{key:'w',cancelable:true});
      window.dispatchEvent(event);
      expect(event.defaultPrevented).toBe(true);
      expect(wrapper.emitted('continue')).toHaveLength(1);
    } finally {wrapper.unmount();}
  });
});
