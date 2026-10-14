import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import MarkdownView from '@/components/common/MarkdownView.vue';

describe('MarkdownView', () => {
  it('渲染基础 markdown（标题、段落）', () => {
    const wrapper = mount(MarkdownView, { props: { source: '# 标题\n\n正文段落' } });

    expect(wrapper.find('h1').exists()).toBe(true);
    expect(wrapper.text()).toContain('正文段落');
  });

  it('闭合的代码块渲染为 <pre><code>', () => {
    const wrapper = mount(MarkdownView, { props: { source: '```python\nprint(1)\n```' } });

    expect(wrapper.find('pre').exists()).toBe(true);
    expect(wrapper.find('code').exists()).toBe(true);
    expect(wrapper.text()).toContain('print(1)');
  });

  it('流式半截：未闭合代码块补全后稳定渲染为 <pre><code>（不闪烁/不吞后文）', () => {
    const wrapper = mount(MarkdownView, { props: { source: '看下面：\n```python\nprint(1)' } });

    expect(wrapper.find('pre').exists()).toBe(true);
    expect(wrapper.text()).toContain('print(1)');
  });

  it('多个完整代码块（偶数围栏）不被改动', () => {
    const src = '```js\na\n```\n\n中间\n\n```js\nb\n```';
    const wrapper = mount(MarkdownView, { props: { source: src } });

    expect(wrapper.findAll('pre')).toHaveLength(2);
    expect(wrapper.text()).toContain('中间');
  });

  it('inline 模式只渲染行内、不出块级元素，也不做围栏补全', () => {
    const wrapper = mount(MarkdownView, { props: { source: '**粗体**', inline: true } });

    expect(wrapper.find('strong').exists()).toBe(true);
    expect(wrapper.find('p').exists()).toBe(false);
  });

  it('不请求外站 Markdown 图片，并保留替代文本和同源图片', () => {
    const wrapper = mount(MarkdownView, {
      props: {
        source: '![外站图](https://tracker.example/pixel.png)\n\n![本地图](/assets/course.png)',
      },
    });

    expect(wrapper.find('img[src="https://tracker.example/pixel.png"]').exists()).toBe(false);
    expect(wrapper.text()).toContain('外站图');
    expect(wrapper.find('img[src="/assets/course.png"]').exists()).toBe(true);
  });
});
