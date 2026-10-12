import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createPinia } from 'pinia';
import StoryboardView from '../StoryboardView.vue';
import type { StoryboardScene } from '@/api/dto/Workshop';

vi.mock('gsap', () => ({
  default: { fromTo: vi.fn(), to: vi.fn(), set: vi.fn() },
}));

const sampleScenes: StoryboardScene[] = [
  { title: '变量', narration: '变量是存储数据的容器', visual_hint: '贴标签的盒子', kc_codes: ['KC01'], duration_seconds: 1, image_prompt: '一个贴标签的盒子', evidence_page_id: 12 },
  { title: '循环', narration: 'for 循环重复执行', visual_hint: '转动的齿轮', kc_codes: ['KC02'], duration_seconds: 1, image_prompt: '转动的齿轮', evidence_page_id: 13 },
];

function mountView() {
  return mount(StoryboardView, {
    props: { content: { scenes: sampleScenes } },
    global: {
      plugins: [createPinia()],
      stubs: { BilibiliVideoCards: true },
    },
  });
}

describe('StoryboardView · 静音文字讲解', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('uses the cited course page image without generating an AI illustration', async () => {
    const fetchSpy = vi.fn();
    vi.stubGlobal('fetch', fetchSpy);
    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.find('img.stage-image').attributes('src')).toBe('/api/course/pages/12/image');
    expect(wrapper.find('[data-testid="storyboard-ai-toggle"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="storyboard-compose"]').exists()).toBe(false);
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('shows scene narration as text and plays silently', async () => {
    const fetchSpy = vi.fn();
    const speakSpy = vi.fn();
    vi.stubGlobal('fetch', fetchSpy);
    Object.defineProperty(window, 'speechSynthesis', {
      configurable: true,
      value: { speak: speakSpy, cancel: vi.fn() },
    });
    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.text()).toContain('变量是存储数据的容器');
    await wrapper.find('.play-btn').trigger('click');
    await vi.advanceTimersByTimeAsync(3_000);

    expect(fetchSpy).not.toHaveBeenCalled();
    expect(speakSpy).not.toHaveBeenCalled();
    expect(wrapper.find('.scene-counter').text()).toBe('2 / 2');
  });
});
