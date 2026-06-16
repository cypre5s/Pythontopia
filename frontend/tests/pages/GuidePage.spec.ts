import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';
import { nextTick } from 'vue';

vi.mock('@/composables/useStardewFx', () => ({
  playUiClick: vi.fn(),
}));

import GuidePage from '@/pages/GuidePage.vue';
import PauseMenu from '@/components/hud/PauseMenu.vue';
import { GUIDE_SECTIONS, GUIDE_SCREENSHOT_TOTAL } from '@/content/guideSections';
import { useGameSettingsStore } from '@stores/gameSettings';

const APP_LAYOUT_STUB = { template: '<div data-testid="layout-stub"><slot /></div>' };

/** 任务要求覆盖的十大主题关键词（逐页核对页面文本）。 */
const TEN_THEME_KEYWORDS = [
  '小镇',
  '主线',
  'AI 助教',
  '图书馆',
  '百晓工坊',
  '训练场',
  'Bug 矿洞',
  '复盘大厅',
  '个人画像',
  '教师班级',
];

function createGuideRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', redirect: '/world' },
      { path: '/guide', name: 'guide', component: { template: '<div />' } },
      { path: '/world', name: 'world', component: { template: '<div />' } },
      { path: '/login', name: 'login', component: { template: '<div />' } },
    ],
  });
}

async function mountGuide(initial = '/guide'): Promise<{ wrapper: VueWrapper; router: Router }> {
  const router = createGuideRouter();
  await router.push(initial);
  await router.isReady();
  const wrapper = mount(GuidePage, {
    global: {
      plugins: [router],
      stubs: { AppLayout: APP_LAYOUT_STUB },
    },
  });
  await flushPromises();
  return { wrapper, router };
}

/** 通过侧栏点击切到指定主题页。 */
async function switchTopic(wrapper: VueWrapper, sectionId: string): Promise<void> {
  await wrapper.get(`[data-testid="guide-nav-link-${sectionId}"]`).trigger('click');
  await flushPromises();
}

beforeEach(() => {
  setActivePinia(createPinia());
  localStorage.clear();
});

describe('GuidePage 分页式文档布局', () => {
  it('渲染左侧主题导航、中间正文列、右侧本页大纲三个分区', async () => {
    const { wrapper } = await mountGuide();

    expect(wrapper.find('.guide-layout').exists()).toBe(true);

    const sidebar = wrapper.get('[data-testid="guide-sidebar"]');
    expect(sidebar.element.tagName).toBe('NAV');
    expect(sidebar.attributes('aria-label')).toBe('章节导航');

    const content = wrapper.get('[data-testid="guide-content"]');
    expect(content.element.tagName).toBe('ARTICLE');

    const outline = wrapper.get('[data-testid="guide-outline"]');
    expect(outline.element.tagName).toBe('NAV');
    expect(outline.attributes('aria-label')).toBe('本页大纲');

    // 左侧导航覆盖全部主题；正文为可读长文（段落数量充足，而非空壳）。
    expect(wrapper.findAll('[data-testid^="guide-nav-link-"]').length).toBe(GUIDE_SECTIONS.length);
    expect(wrapper.get('h1').text()).toContain('操作指南');
    expect(content.findAll('p').length).toBeGreaterThan(3);
  });

  it('一次只渲染一个主题页，默认是第一个主题', async () => {
    const { wrapper } = await mountGuide();
    const first = GUIDE_SECTIONS[0]!;
    const second = GUIDE_SECTIONS[1]!;

    expect(wrapper.find(`#${first.id}`).exists()).toBe(true);
    expect(wrapper.find(`#${second.id}`).exists()).toBe(false);
    // 页码徽标显示当前页位置
    expect(wrapper.text()).toContain(`第 1 / ${GUIDE_SECTIONS.length} 篇`);
  });

  it('?topic= 驱动当前页；无效 topic 回退第一个主题', async () => {
    const { wrapper } = await mountGuide('/guide?topic=training');
    expect(wrapper.find('#training').exists()).toBe(true);
    expect(wrapper.find(`#${GUIDE_SECTIONS[0]!.id}`).exists()).toBe(false);

    const { wrapper: fallbackWrapper } = await mountGuide('/guide?topic=no-such-topic');
    expect(fallbackWrapper.find(`#${GUIDE_SECTIONS[0]!.id}`).exists()).toBe(true);
  });

  it('右侧本页大纲只列当前主题的小节，不重复全站目录', async () => {
    const { wrapper } = await mountGuide('/guide?topic=workshop');
    const outline = wrapper.get('[data-testid="guide-outline"]');

    const workshop = GUIDE_SECTIONS.find((s) => s.id === 'workshop')!;
    for (const sub of workshop.subsections) {
      expect(
        outline.find(`a[href="#${sub.id}"]`).exists(),
        `大纲缺少当前页小节锚点 ${sub.id}`,
      ).toBe(true);
    }
    // 全站主题只由左侧目录负责，右栏不重复第二份全站导航。
    expect(outline.text()).not.toContain('其他主题');
    expect(outline.text()).not.toContain('训练场');
  });

  it('左侧目录按快速开始、看学练测、成长与教学分组，并覆盖全部主题', async () => {
    const { wrapper } = await mountGuide();
    const sidebar = wrapper.get('[data-testid="guide-sidebar"]');

    const expectedGroups = [
      ['quick-start', '快速开始'],
      ['learning-loop', '看学练测'],
      ['growth-teaching', '成长与教学'],
    ] as const;
    for (const [id, label] of expectedGroups) {
      const group = sidebar.get(`[data-testid="guide-nav-group-${id}"]`);
      expect(group.text()).toContain(label);
    }

    expect(sidebar.findAll('[data-testid^="guide-nav-link-"]').length)
      .toBe(GUIDE_SECTIONS.length);
  });

  it('左侧目录在当前主题下展开本页小节，移动端只打开一层目录也能定位', async () => {
    const { wrapper } = await mountGuide('/guide?topic=ai-guide');
    const section = GUIDE_SECTIONS.find((item) => item.id === 'ai-guide')!;

    for (const subsection of section.subsections) {
      const link = wrapper.get(`[data-testid="guide-nav-sub-${subsection.id}"]`);
      expect(link.attributes('href')).toBe(`#${subsection.id}`);
    }
  });
});

describe('GuidePage 分页导航', () => {
  it('侧栏点击切换主题页，URL 带上 ?topic=', async () => {
    const { wrapper, router } = await mountGuide();

    await switchTopic(wrapper, 'library');
    expect(router.currentRoute.value.query.topic).toBe('library');
    expect(wrapper.find('#library').exists()).toBe(true);
    expect(wrapper.find(`#${GUIDE_SECTIONS[0]!.id}`).exists()).toBe(false);
  });

  it('底部上一篇/下一篇翻页正确；首页无上一篇、末页无下一篇', async () => {
    const { wrapper, router } = await mountGuide();

    // 首页：只有下一篇
    expect(wrapper.find('[data-testid="guide-pager-prev"]').exists()).toBe(false);
    const next = wrapper.get('[data-testid="guide-pager-next"]');
    expect(next.text()).toContain(GUIDE_SECTIONS[1]!.navTitle);

    await next.trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.query.topic).toBe(GUIDE_SECTIONS[1]!.id);
    expect(wrapper.get('[data-testid="guide-pager-prev"]').text())
      .toContain(GUIDE_SECTIONS[0]!.navTitle);

    // 跳到最后一页：只有上一篇
    const last = GUIDE_SECTIONS[GUIDE_SECTIONS.length - 1]!;
    await switchTopic(wrapper, last.id);
    expect(wrapper.find('[data-testid="guide-pager-next"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="guide-pager-prev"]').exists()).toBe(true);
  });
});

describe('GuidePage 移动端导航折叠', () => {
  it('导航开关可折叠展开，且正文内容不被吞掉', async () => {
    const { wrapper } = await mountGuide();

    const toggle = wrapper.get('[data-testid="guide-nav-toggle"]');
    expect(toggle.element.tagName).toBe('BUTTON');
    expect(toggle.attributes('aria-expanded')).toBe('false');
    // a11y 接线：开关控制的就是章节导航区域
    expect(toggle.attributes('aria-controls')).toBe('guide-nav');
    expect(wrapper.get('[data-testid="guide-sidebar"]').attributes('id')).toBe('guide-nav');

    await toggle.trigger('click');
    expect(toggle.attributes('aria-expanded')).toBe('true');
    expect(wrapper.get('.guide-layout').classes()).toContain('guide-layout--nav-open');

    // 展开导航时正文仍然在文档流中（不被覆盖/移除）
    expect(wrapper.get('[data-testid="guide-content"]').text().length).toBeGreaterThan(50);

    // 选中一个章节后导航自动收起，回到阅读状态
    await wrapper.get('[data-testid="guide-nav-link-library"]').trigger('click');
    await flushPromises();
    expect(toggle.attributes('aria-expanded')).toBe('false');
  });
});

describe('GuidePage 截图契约与优雅降级', () => {
  it('逐页累计渲染全部冻结 URL 截图，每张都有有意义的 alt 与图注', async () => {
    const { wrapper } = await mountGuide();

    const seen: string[] = [];
    for (const section of GUIDE_SECTIONS) {
      await switchTopic(wrapper, section.id);
      const figures = wrapper.findAll('[data-testid="guide-screenshot"]');
      expect(figures.length, `主题 ${section.id} 没有截图`).toBeGreaterThanOrEqual(1);

      for (const figure of figures) {
        const img = figure.get('img');
        const src = img.attributes('src') ?? '';
        // 两类合法来源：与截图基础设施约定的「NN-名称.webp」契约，或实拍「live-名称.png」。
        expect(src, `截图 URL 前缀错误：${src}`).toMatch(
          /^\/guide\/screenshots\/(\d{2}-[a-z-]+\.webp|live-[a-z-]+\.png)$/,
        );
        seen.push(src);

        const alt = img.attributes('alt') ?? '';
        expect(alt.length, `alt 过短：${alt}`).toBeGreaterThanOrEqual(8);
        expect(img.attributes('loading')).toBe('lazy');

        const caption = figure.get('figcaption');
        expect(caption.text().length, `图注过短：${caption.text()}`).toBeGreaterThanOrEqual(6);
      }
    }

    // 全站截图总数与内容数据一致，且无重复 URL
    expect(new Set(seen).size).toBe(seen.length);
    expect(seen.length).toBe(GUIDE_SCREENSHOT_TOTAL);
  });

  it('截图加载失败时降级为文字占位说明，图注保留，不出现假截图', async () => {
    const { wrapper } = await mountGuide();
    const firstFigure = wrapper.get('[data-testid="guide-screenshot"]');
    const img = firstFigure.get('img');
    const alt = img.attributes('alt') ?? '';

    await img.trigger('error');
    await nextTick();

    const fallback = firstFigure.get('[data-testid="guide-screenshot-fallback"]');
    expect(fallback.text()).toContain('截图准备中');
    expect(fallback.text()).toContain(alt);
    // 占位不是图片，不存在伪造截图
    expect(firstFigure.find('img').exists()).toBe(false);
    // 图注仍然保留
    expect(firstFigure.find('figcaption').exists()).toBe(true);
  });
});

describe('GuidePage 锚点与小节定位', () => {
  it('点击侧栏主题锚点后 URL 保留对应 hash', async () => {
    const { wrapper, router } = await mountGuide();
    const sectionId = GUIDE_SECTIONS[0]!.id;

    await wrapper.get(`[data-testid="guide-nav-link-${sectionId}"]`).trigger('click');
    await flushPromises();

    expect(router.currentRoute.value.hash).toBe(`#${sectionId}`);
  });

  it('当前页的主题与小节都渲染可直链的锚点元素与 # 链接', async () => {
    const { wrapper } = await mountGuide();

    for (const section of GUIDE_SECTIONS) {
      await switchTopic(wrapper, section.id);
      expect(wrapper.find(`#${section.id}`).exists(), `缺少主题锚点 #${section.id}`).toBe(true);
      expect(
        wrapper.find(`h2 a[href="#${section.id}"]`).exists(),
        `主题 ${section.id} 标题缺少 # 锚点链接`,
      ).toBe(true);
      for (const sub of section.subsections) {
        expect(wrapper.find(`#${sub.id}`).exists(), `缺少小节锚点 #${sub.id}`).toBe(true);
      }
    }
  });

  it('?topic= 直接进入时，对应主题在导航中高亮', async () => {
    const { wrapper } = await mountGuide('/guide?topic=training');
    const active = wrapper.get('[data-testid="guide-nav-link-training"]');
    expect(active.attributes('aria-current')).toBe('location');
  });
});

describe('GuidePage 键盘焦点与当前主题高亮', () => {
  it('主题导航是可聚焦的真实链接，且任何时刻只有一个 aria-current', async () => {
    const { wrapper, router } = await mountGuide();

    const links = wrapper.findAll('[data-testid^="guide-nav-link-"]');
    for (const link of links) {
      expect(link.element.tagName).toBe('A');
      // 不允许 tabindex=-1 把链接踢出键盘焦点序列
      expect(link.attributes('tabindex')).not.toBe('-1');
    }

    const currentOf = (): string[] =>
      wrapper
        .findAll('[data-testid^="guide-nav-link-"]')
        .filter((link) => link.attributes('aria-current') === 'location')
        .map((link) => link.attributes('data-testid') ?? '');

    expect(currentOf()).toEqual(['guide-nav-link-town-basics']);

    await switchTopic(wrapper, 'bug-mine');

    expect(currentOf()).toEqual(['guide-nav-link-bug-mine']);
    expect(router.currentRoute.value.query.topic).toBe('bug-mine');
  });
});

describe('GuidePage 内容覆盖十大主题', () => {
  it('逐页覆盖十大主题关键词，每页讲清与「看 → 学 → 练 → 测」闭环的关系', async () => {
    const { wrapper } = await mountGuide();

    let allText = '';
    for (const section of GUIDE_SECTIONS) {
      await switchTopic(wrapper, section.id);
      allText += wrapper.text();

      // 每个主题页都有一段「与学习闭环的关系」说明
      const loops = wrapper.findAll('[data-testid="guide-loop-relation"]');
      expect(loops.length, `主题 ${section.id} 缺少闭环说明`).toBe(1);
      expect(loops[0]!.text().length).toBeGreaterThanOrEqual(10);
    }

    for (const keyword of TEN_THEME_KEYWORDS) {
      expect(allText, `缺少主题关键词：${keyword}`).toContain(keyword);
    }
    expect(allText).toContain('看 → 学 → 练 → 测');
  });

  it('AI 导学助手讲清上下文、分阶段辅导、边界与工坊联动', async () => {
    const { wrapper } = await mountGuide('/guide?topic=ai-guide');
    const text = wrapper.get('[data-testid="guide-content"]').text();

    expect(text).toContain('当前视频、课件页、题目与知识点');
    expect(text).toContain('审题导读 → 思路分析 → 骨架代码 → 错因诊断 → AC 复盘');
    expect(text).toContain('不会直接给出可复制提交的完整答案');
    expect(text).toContain('把当前知识点和学习目标一起带进百晓工坊');
  });

  it('百晓工坊讲清输入、能力路由、多智能体审查、产物与复用', async () => {
    const { wrapper } = await mountGuide('/guide?topic=workshop');
    const text = wrapper.get('[data-testid="guide-content"]').text();

    expect(text).toContain('自然语言、@ 知识点和上传文件');
    expect(text).toContain('从 16 项能力中选择合适的处理方式');
    expect(text).toContain('课件证据、内容生成、引用审查、防幻觉与安全审查');
    expect(text).toContain('保存到「我的书房」继续复用');
  });
});

describe('PauseMenu 操作帮助入口', () => {
  it('「操作帮助」(id=help) 点击后跳转 /guide 并关闭菜单，不再展开内联帮助块', async () => {
    const router = createGuideRouter();
    await router.push('/world');
    await router.isReady();
    const pushSpy = vi.spyOn(router, 'push');

    const settings = useGameSettingsStore();
    settings.pauseMenuOpen = true;

    const wrapper = mount(PauseMenu, {
      global: { plugins: [router] },
    });
    await nextTick();

    const helpButton = wrapper.get('[data-testid="pause-help"]');
    expect(helpButton.text()).toContain('操作帮助');

    pushSpy.mockClear();
    await helpButton.trigger('click');
    await flushPromises();

    expect(pushSpy).toHaveBeenCalledWith('/guide');
    expect(settings.pauseMenuOpen).toBe(false);
    // 旧的内联帮助块（快捷键速查卡）不再出现
    expect(wrapper.find('.pause-help').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('快捷键速查');
  });
});
