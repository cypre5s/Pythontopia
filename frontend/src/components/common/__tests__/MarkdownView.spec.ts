import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import MarkdownView from '../MarkdownView.vue';

describe('MarkdownView', () => {
  it('renders image syntax as text when images are disabled', () => {
    const wrapper = mount(MarkdownView, {
      props: {
        source: '![示意图](data:image/png;base64,YQ==)',
        allowImages: false,
      },
    });

    expect(wrapper.find('img').exists()).toBe(false);
    expect(wrapper.text()).toContain('示意图');
  });

  it('keeps inline images enabled for trusted content by default', () => {
    const wrapper = mount(MarkdownView, {
      props: { source: '![课件图](data:image/png;base64,YQ==)' },
    });

    expect(wrapper.find('img').exists()).toBe(true);
  });
});
