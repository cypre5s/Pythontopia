import { describe, expect, it } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import TutorVisualAids, { type TutorVisualAid } from '@/components/tutor/TutorVisualAids.vue';
import { sanitizeSvgMarkup } from '@/utils/sanitizeSvg';

const VALID_SVG =
  '<svg viewBox="0 0 100 60" xmlns="http://www.w3.org/2000/svg">' +
  '<rect x="4" y="4" width="40" height="20" fill="#f1c453"></rect>' +
  '<text x="8" y="18">lst[0]</text></svg>';

describe('TutorVisualAids · ascii / svg 图解', () => {
  it('ascii 字符画以等宽 pre 原样渲染', async () => {
    const art = '┌───┬───┐\n│ 0 │ 1 │\n└───┴───┘';
    const aids: TutorVisualAid[] = [{ kind: 'ascii', caption: '索引示意', ascii_art: art }];
    const wrapper = mount(TutorVisualAids, { props: { aids } });
    await flushPromises();

    const pre = wrapper.find('[data-testid="aid-ascii"]');
    expect(pre.exists()).toBe(true);
    expect(pre.text()).toContain('│ 0 │ 1 │');
    expect(wrapper.text()).toContain('索引示意');
  });

  it('合法 svg 经消毒后内联渲染', async () => {
    const aids: TutorVisualAid[] = [{ kind: 'svg', caption: '内存示意', svg: VALID_SVG }];
    const wrapper = mount(TutorVisualAids, { props: { aids } });
    await flushPromises();

    const host = wrapper.find('[data-testid="aid-svg"]');
    expect(host.exists()).toBe(true);
    expect(host.html()).toContain('<svg');
    expect(host.html()).toContain('lst[0]');
  });

  it('带脚本/事件的恶意 svg 被消毒（不残留 script 与 onload）', async () => {
    const evil =
      '<svg viewBox="0 0 10 10"><script>alert(1)</script>' +
      '<rect onload="alert(2)" width="5" height="5"></rect></svg>';
    const aids: TutorVisualAid[] = [{ kind: 'svg', svg: evil }];
    const wrapper = mount(TutorVisualAids, { props: { aids } });
    await flushPromises();

    expect(wrapper.html()).not.toContain('<script');
    expect(wrapper.html()).not.toContain('onload');
  });

  it('sanitizeSvgMarkup 移除 foreignObject 与外链 href', () => {
    const evil =
      '<svg viewBox="0 0 10 10"><foreignObject><iframe src="https://evil"></iframe>' +
      '</foreignObject><a href="https://evil.example"><text>x</text></a></svg>';
    const clean = sanitizeSvgMarkup(evil);
    expect(clean).not.toContain('foreignObject');
    expect(clean).not.toContain('iframe');
    expect(clean).not.toContain('href');
  });

  it('未知/空内容的 aid 不渲染（不出空卡片）', async () => {
    const aids: TutorVisualAid[] = [
      { kind: 'ascii', ascii_art: '' },
      { kind: 'svg', svg: '' },
    ];
    const wrapper = mount(TutorVisualAids, { props: { aids } });
    await flushPromises();

    expect(wrapper.find('[data-testid="tutor-visual-aids"]').exists()).toBe(false);
  });
});
