import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import BilibiliVideoCards from '@/components/video/BilibiliVideoCards.vue';
import { searchBilibili, type BilibiliVideoDto } from '@/api/bilibiliVideo';

vi.mock('@/api/bilibiliVideo', () => ({
  searchBilibili: vi.fn(),
}));

const SAMPLE: BilibiliVideoDto[] = [
  {
    bvid: 'BV1xx411c7XD',
    title: 'for 循环讲解',
    author: 'UP主小明',
    coverUrl: 'https://i2.hdslb.com/x.jpg',
    durationSec: 754,
    play: 123456,
    danmaku: 678,
    pubdate: 1600000000,
    url: 'https://www.bilibili.com/video/BV1xx411c7XD',
  },
];

describe('BilibiliVideoCards', () => {
  beforeEach(() => {
    vi.mocked(searchBilibili).mockReset();
  });

  it('renders one external-resource card per result with correct link + badge + formatting', async () => {
    vi.mocked(searchBilibili).mockResolvedValueOnce(SAMPLE);
    const wrapper = mount(BilibiliVideoCards, { props: { keyword: 'for循环 Python' } });
    await flushPromises();

    expect(searchBilibili).toHaveBeenCalledWith('for循环 Python', 8);
    expect(wrapper.findAll('.bili-card')).toHaveLength(1);

    const link = wrapper.find('.bili-card__link');
    expect(link.attributes('href')).toBe('https://www.bilibili.com/video/BV1xx411c7XD');
    expect(link.attributes('target')).toBe('_blank');
    expect(link.attributes('rel')).toBe('noopener noreferrer');

    expect(wrapper.find('.bili-card__badge').text()).toContain('B站');
    expect(wrapper.find('.bili-card__img').attributes('referrerpolicy')).toBe('no-referrer');
    expect(wrapper.find('.bili-card__dur').text()).toBe('12:34');
    expect(wrapper.find('.bili-card__play').text()).toContain('万');
  });

  it('emits "consumed" when a card is opened', async () => {
    vi.mocked(searchBilibili).mockResolvedValueOnce(SAMPLE);
    const wrapper = mount(BilibiliVideoCards, { props: { keyword: 'for循环' } });
    await flushPromises();

    await wrapper.find('.bili-card__link').trigger('click');
    expect(wrapper.emitted('consumed')).toBeTruthy();
  });

  it('shows an empty placeholder (never a white screen) when there are no results', async () => {
    vi.mocked(searchBilibili).mockResolvedValueOnce([]);
    const wrapper = mount(BilibiliVideoCards, { props: { keyword: '没有结果的关键词' } });
    await flushPromises();

    expect(wrapper.find('[data-testid="bili-cards-empty"]').exists()).toBe(true);
    expect(wrapper.findAll('.bili-card')).toHaveLength(0);
  });

  it('falls back to a placeholder cover when the thumbnail errors (anti-hotlink)', async () => {
    vi.mocked(searchBilibili).mockResolvedValueOnce(SAMPLE);
    const wrapper = mount(BilibiliVideoCards, { props: { keyword: 'for循环' } });
    await flushPromises();

    expect(wrapper.find('.bili-card__img').exists()).toBe(true);
    await wrapper.find('.bili-card__img').trigger('error');
    expect(wrapper.find('.bili-card__img').exists()).toBe(false);
    expect(wrapper.find('.bili-card__cover-fallback').exists()).toBe(true);
  });
});
