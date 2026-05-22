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

function primeStore(): ReturnType<typeof useDialogStore> {
  const store = useDialogStore();
  store.visible = true;
  store.npcKey = 'mayor_py';
  store.npcName = '镇长 Py';
  store.portrait = 'portrait_mayor_py.png';
  store.tree = makeTree();
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
      '/assets/pixel/portraits/portrait_mayor_py.png',
    );
    expect(wrapper.find('[data-testid="dialog-text"]').text().trim()).toBe('▏');
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
