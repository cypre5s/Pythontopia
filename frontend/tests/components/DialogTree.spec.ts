/**
 * DialogTree.spec.ts —— DialogTree 组件单元测试。
 *
 * 覆盖：
 *   1. 渲染：store.visible=false 时不渲染，true 时显示 NPC 名 / portrait / 文本框；
 *   2. 打字机：fake timers + 步进 setTimeout，断言 typedText 从空 → 完整；
 *   3. 选项点击：在打字机结束后点击 choice，调用 store.selectChoice(idx)；
 *   4. ESC 关闭：触发 keydown.esc，调用 store.close；
 *   5. loadError：错误态显示错误信息与"关闭"按钮。
 *
 * 依赖隔离：
 *   - mock `@/composables/useAudio` 避免 jsdom 加载 Phaser 抛错；
 *   - 直接用 setActivePinia + 真实 store，不 mock store 内部逻辑，保证组件与 store 接口契约。
 */
import { mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import DialogTree from '@/components/game/DialogTree.vue';
import { useDialogStore, type NpcDialogTree } from '@/stores/dialog';
import { useSessionStore } from '@/stores/session';

const sfxMocks = vi.hoisted(() => ({
  playSfx: vi.fn(),
  playBgm: vi.fn(),
  stopBgm: vi.fn(),
  setMuted: vi.fn(),
  useAudio: vi.fn(() => ({
    playSfx: vi.fn(),
    playBgm: vi.fn(),
    stopBgm: vi.fn(),
    setMuted: vi.fn(),
    isMuted: () => false,
    getCurrentBgmKey: () => null,
  })),
}));

vi.mock('@/composables/useAudio', () => sfxMocks);

function makeTree(): NpcDialogTree {
  return {
    npcKey: 'mayor_py',
    rootNodeId: 'greet',
    nodes: {
      greet: {
        id: 'greet',
        text: 'hi',
        conditions: [],
        choices: [
          { label: '继续', nextNodeId: 'more' },
          { label: '再见', nextNodeId: null },
        ],
      },
      more: {
        id: 'more',
        text: 'next',
        conditions: [],
        choices: [{ label: '收到', nextNodeId: null, effects: [{ type: 'friendship', delta: 2 }] }],
      },
    },
  };
}

function primeStore(text = 'hi'): ReturnType<typeof useDialogStore> {
  const store = useDialogStore();
  store.visible = true;
  store.npcKey = 'mayor_py';
  store.npcName = '镇长 Py';
  store.portrait = 'portrait_mayor_py.png';
  const tree = makeTree();
  tree.nodes.greet.text = text;
  store.tree = tree;
  store.currentNodeId = 'greet';
  store.history = ['greet'];
  store.loading = false;
  store.loadError = null;
  return store;
}

describe('DialogTree.vue', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  it('renders nothing when store.visible is false', () => {
    const store = useDialogStore();
    store.visible = false;
    const wrapper = mount(DialogTree);
    expect(wrapper.find('.dialog-overlay').exists()).toBe(false);
  });

  it('renders npc name, portrait, and typewriter cursor when visible', async () => {
    primeStore();
    const wrapper = mount(DialogTree);
    expect(wrapper.find('.dialog-overlay').exists()).toBe(true);
    expect(wrapper.text()).toContain('镇长 Py');
    expect(wrapper.find('img.pixel-portrait').attributes('src')).toBe(
      '/assets/pixel/portraits/heads/portrait_mayor_py_head.png',
    );
    expect(wrapper.find('[data-testid="dialog-text"]').text().trim()).toBe('▏');
  });

  it('falls back to the full portrait when the cropped head asset is missing', async () => {
    const store = primeStore();
    store.npcKey = 'workshop_master';
    store.npcName = '工坊师傅';
    store.portrait = 'portrait_workshop_master.png';
    const wrapper = mount(DialogTree);
    const img = wrapper.get('img.pixel-portrait');

    expect(img.attributes('src')).toBe(
      '/assets/pixel/portraits/heads/portrait_workshop_master_head.png',
    );
    await img.trigger('error');

    expect(wrapper.get('img.pixel-portrait').attributes('src')).toBe(
      '/assets/pixel/portraits/portrait_workshop_master.png',
    );
    expect((wrapper.get('img.pixel-portrait').element as HTMLImageElement).style.visibility).toBe('');
  });

  it('renders playerName template with the session display name', async () => {
    const session = useSessionStore();
    session.user = { id: 7, username: 'stu07', displayName: '小七', role: 'STUDENT' };
    primeStore('欢迎来到 Pythontopia，{{playerName}}！');
    const wrapper = mount(DialogTree);

    await vi.advanceTimersByTimeAsync(1000);

    const text = wrapper.find('[data-testid="dialog-text"]').text().replace(/▏/g, '').trim();
    expect(text).toBe('欢迎来到 Pythontopia，小七！');
  });

  it('substitutes stats / profileAdaptive / giftFavorite from store.context (R-12 W2-NPC)', async () => {
    const store = primeStore(
      '本周做了 {{stats.exercisesThisWeek}} 道题。{{profileAdaptive:实践型 | 谨慎型}}。{{giftFavorite:谢谢礼物 | 收下了}}',
    );
    store.context = {
      playerName: '小七',
      profileType: 'practical',
      giftJustGiven: true,
      stats: { exercisesThisWeek: '7' },
    };
    const wrapper = mount(DialogTree);

    await vi.advanceTimersByTimeAsync(1500);

    const text = wrapper.find('[data-testid="dialog-text"]').text().replace(/▏/g, '').trim();
    expect(text).toBe('本周做了 7 道题。实践型。谢谢礼物');
  });

  it('falls back to "—" for unresolved stats so the {{...}} literal never leaks to UI', async () => {
    const store = primeStore('最难的是 "{{stats.toughestKcNameThisWeek}}"。');
    store.context = null;
    const wrapper = mount(DialogTree);

    await vi.advanceTimersByTimeAsync(1500);

    const text = wrapper.find('[data-testid="dialog-text"]').text().replace(/▏/g, '').trim();
    expect(text).toBe('最难的是 "—"。');
    expect(text.includes('{{')).toBe(false);
  });

  it('uses black ink for npc name and dialog copy on the parchment panel', () => {
    const store = primeStore();
    store.friendship = 2;
    const wrapper = mount(DialogTree, { attachTo: document.body });

    expect(getComputedStyle(wrapper.get('.dialog-head').element).color).toBe('rgb(17, 17, 17)');
    expect(getComputedStyle(wrapper.get('[data-testid="dialog-text"]').element).color).toBe(
      'rgb(17, 17, 17)',
    );
    expect(getComputedStyle(wrapper.get('[data-testid="dialog-friendship"]').element).color).toBe(
      'rgb(17, 17, 17)',
    );

    wrapper.unmount();
  });

  it('typewriter reveals characters progressively then shows choices', async () => {
    primeStore();
    const wrapper = mount(DialogTree);
    await vi.advanceTimersByTimeAsync(24);
    expect(wrapper.find('[data-testid="dialog-text"]').text()).toContain('h');
    await vi.advanceTimersByTimeAsync(200);
    const textEl = wrapper.find('[data-testid="dialog-text"]');
    expect(textEl.text().replace(/▏/g, '').trim()).toBe('hi');
    const choices = wrapper.findAll('.choice-btn');
    expect(choices).toHaveLength(2);
    expect(choices[0]?.text()).toBe('继续');
    expect(choices[1]?.text()).toBe('再见');
  });

  it('clicking a choice calls store.selectChoice after typewriter completes', async () => {
    const store = primeStore();
    const spy = vi.spyOn(store, 'selectChoice');
    const wrapper = mount(DialogTree);
    await vi.advanceTimersByTimeAsync(500);
    const choices = wrapper.findAll('.choice-btn');
    expect(choices.length).toBeGreaterThan(0);
    await choices[0]!.trigger('click');
    expect(spy).toHaveBeenCalledWith(0);
  });

  it('route effect choices close the dialog and dispatch navigation', async () => {
    const store = primeStore();
    store.tree = {
      npcKey: 'workshop_master',
      rootNodeId: 'greet',
      nodes: {
        greet: {
          id: 'greet',
          text: '要进工坊吗？',
          conditions: [],
          choices: [
            {
              label: '打开资源工坊',
              nextNodeId: null,
              effects: [{ type: 'route', route: '/workshop' }],
            },
          ],
        },
      },
    };
    store.npcKey = 'workshop_master';
    store.currentNodeId = 'greet';
    store.history = ['greet'];

    const routes: string[] = [];
    const onNavigate = (event: Event): void => {
      const detail = (event as CustomEvent<{ route?: string }>).detail;
      if (detail?.route) routes.push(detail.route);
    };
    window.addEventListener('pythontopia:dialog-navigate', onNavigate);

    const wrapper = mount(DialogTree);
    await vi.advanceTimersByTimeAsync(500);
    await wrapper.get('.choice-btn').trigger('click');

    expect(store.visible).toBe(false);
    expect(routes).toEqual(['/workshop']);
    window.removeEventListener('pythontopia:dialog-navigate', onNavigate);
  });

  it('clicking text during typewriter finishes the typewriter instead of advancing', async () => {
    const store = primeStore();
    const selectSpy = vi.spyOn(store, 'selectChoice');
    const wrapper = mount(DialogTree);
    const textEl = wrapper.find('[data-testid="dialog-text"]');
    await textEl.trigger('click');
    expect(selectSpy).not.toHaveBeenCalled();
    expect(textEl.text().replace(/▏/g, '').trim()).toBe('hi');
  });

  it('pressing ESC closes the dialog via store.close', async () => {
    const store = primeStore();
    const closeSpy = vi.spyOn(store, 'close');
    const wrapper = mount(DialogTree);
    await wrapper.find('.dialog-overlay').trigger('keydown.esc');
    expect(closeSpy).toHaveBeenCalledTimes(1);
  });

  it('shows load error and a close button when store.loadError is set', async () => {
    const store = useDialogStore();
    store.visible = true;
    store.npcKey = 'broken';
    store.npcName = 'broken';
    store.loadError = 'NPC 数据加载失败：broken（HTTP 404）';
    const closeSpy = vi.spyOn(store, 'close');
    const wrapper = mount(DialogTree);
    expect(wrapper.find('[data-testid="dialog-error"]').text()).toContain('NPC 数据加载失败');
    const btn = wrapper.find('.choice-btn');
    expect(btn.exists()).toBe(true);
    await btn.trigger('click');
    expect(closeSpy).toHaveBeenCalledTimes(1);
  });
});
