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

  it('removes executable markup from untrusted Markdown', () => {
    const wrapper = mount(MarkdownView, {
      props: {
        source: '<script>alert(1)</script><img src="/safe.png" onerror="alert(1)"><a href="javascript:alert(1)">链接</a>',
      },
    });

    expect(wrapper.find('script').exists()).toBe(false);
    expect(wrapper.find('img').attributes('onerror')).toBeUndefined();
    expect(wrapper.find('a').attributes('href')).toBeUndefined();
  });
});
