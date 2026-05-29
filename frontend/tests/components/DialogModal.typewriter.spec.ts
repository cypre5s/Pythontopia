/**
 * DialogModal.typewriter.spec.ts —— W2-dialog-upgrade 单元测试。
 *
 * 覆盖目标：
 *   1. 默认 30ms/字符的打字机推进，最终调用 store.markTypingDone；
 *   2. typingSpeedMs prop 自定义速率生效；
 *   3. 点击文本 / Enter / "E" 任一交互键中途跳过打字机；
 *   4. 左右立绘 props 渲染 + 自动从 store.npcKey 推断 portraitLeft 路径；
 *   5. 打字机结束后 store.hasBranches=true 时渲染选项按钮；
 *      Enter / 点击触发 store.chooseBranch(label)；
 *   6. v-if 守护：store.isOpen=false 时不渲染。
 *
 * 设计：
 *   - 使用 vi.useFakeTimers，配合 vi.advanceTimersByTimeAsync 精确推进 setTimeout 链；
 *   - 不 mock 任何 store 内部逻辑，验证组件 ⇄ store 真实契约；
 *   - 通过直接赋值 store 字段绕开 fetch，保持测试独立于 NPC 数据文件。
 */
import { mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import DialogModal from '@/components/game/DialogModal.vue';
import {
  useDialogStore,
  __resetChoiceListenersForTests,
  __resetFriendshipMemoryForTests,
  type NpcDialogTree,
} from '@/stores/dialog';

function makeTree(): NpcDialogTree {
  return {
    npcKey: 'mayor_py',
    rootNodeId: 'greet',
    nodes: {
      greet: {
        id: 'greet',
        text: 'hello',
        conditions: [],
        choices: [
          { label: 'A', nextNodeId: 'b' },
          { label: 'B', nextNodeId: null },
        ],
      },
      b: {
        id: 'b',
        text: 'world',
        conditions: [],
        choices: [{ label: '收到', nextNodeId: null }],
      },
    },
  };
}

/** 走旧 open() / lines 占位入口，最常见的兼容场景。 */
function primeLegacy(): ReturnType<typeof useDialogStore> {
  const store = useDialogStore();
  store.openNpcKey = 'test_npc';
  store.npcKey = 'test_npc';
  store.npcName = 'Test NPC';
  store.lines = ['hello'];
  return store;
}

/** 走 currentNode 路径，用于断言分支选项渲染。 */
function primeWithTree(): ReturnType<typeof useDialogStore> {
  const store = useDialogStore();
  store.openNpcKey = 'mayor_py';
  store.npcKey = 'mayor_py';
  store.npcName = 'Mayor Py';
  store.tree = makeTree();
  store.currentNodeId = 'greet';
  store.history = ['greet'];
  return store;
}

/** 去掉打字机光标字符（▏）后取得 dialog-text 真实文本。 */
function visibleText(wrapper: ReturnType<typeof mount>): string {
  return wrapper.find('[data-testid="dialog-text"]').text().replace(/▏/g, '').trim();
}

describe('DialogModal.vue typewriter (W2-dialog-upgrade)', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    __resetFriendshipMemoryForTests();
    __resetChoiceListenersForTests();
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  it('renders nothing when store.isOpen is false', () => {
    useDialogStore();
    const wrapper = mount(DialogModal);
    expect(wrapper.find('[data-testid="dialog-modal"]').exists()).toBe(false);
  });

  it('reveals text progressively at 30ms per char and marks typing done at the end', async () => {
    const store = primeLegacy();
    const wrapper = mount(DialogModal);
    expect(store.isTypingDone).toBe(false);
    expect(wrapper.find('.typing-cursor').exists()).toBe(true);
    expect(visibleText(wrapper)).toBe('');

    await vi.advanceTimersByTimeAsync(30);
    expect(visibleText(wrapper)).toBe('h');

    await vi.advanceTimersByTimeAsync(30 * 4);
    expect(visibleText(wrapper)).toBe('hello');
    expect(store.isTypingDone).toBe(true);
    expect(wrapper.find('.typing-cursor').exists()).toBe(false);
  });

  it('respects typingSpeedMs prop to control reveal cadence', async () => {
    primeLegacy();
    const wrapper = mount(DialogModal, { props: { typingSpeedMs: 10 } });
    await vi.advanceTimersByTimeAsync(10 * 5);
    expect(visibleText(wrapper)).toBe('hello');
  });

  it('clicking the dialog text mid-typewriter skips to full text and marks done', async () => {
    const store = primeLegacy();
    const wrapper = mount(DialogModal);
    await vi.advanceTimersByTimeAsync(30);
    expect(visibleText(wrapper)).toBe('h');

    await wrapper.find('[data-testid="dialog-text"]').trigger('click');

    expect(store.isTypingDone).toBe(true);
    expect(visibleText(wrapper)).toBe('hello');
  });

  it('Enter key mid-typewriter triggers markTypingDone and reveals full text', async () => {
    const store = primeLegacy();
    const wrapper = mount(DialogModal);
    await vi.advanceTimersByTimeAsync(30);
    expect(store.isTypingDone).toBe(false);

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    await wrapper.vm.$nextTick();

    expect(store.isTypingDone).toBe(true);
    expect(visibleText(wrapper)).toBe('hello');
  });

  it('"E" key (lowercase or uppercase) mid-typewriter also triggers markTypingDone', async () => {
    const store = primeLegacy();
    mount(DialogModal);
    await vi.advanceTimersByTimeAsync(30);
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'E' }));
    expect(store.isTypingDone).toBe(true);
  });

  it('renders portraitLeft and portraitRight from props when provided', () => {
    primeLegacy();
    const wrapper = mount(DialogModal, {
      props: { portraitLeft: '/custom-left.png', portraitRight: '/custom-right.png' },
    });
    const left = wrapper.find('[data-testid="dialog-portrait-left"] img');
    const right = wrapper.find('[data-testid="dialog-portrait-right"] img');
    expect(left.exists()).toBe(true);
    expect(left.attributes('src')).toBe('/custom-left.png');
    expect(right.exists()).toBe(true);
    expect(right.attributes('src')).toBe('/custom-right.png');
  });

  it('falls back to /assets/pixel/portraits/heads/portrait_<npcKey>_head.png when portraitLeft prop is omitted (R-13 §-1 B1 v3)', () => {
    primeLegacy();
    const wrapper = mount(DialogModal);
    const left = wrapper.find('[data-testid="dialog-portrait-left"] img');
    expect(left.exists()).toBe(true);
    // R-13 §-1 B1 续修 v3：源 portrait 是 LPC sprite sheet，sprite-sheet 整张直接 <img>
    // 会显示 9 个小头；scripts/crop-portrait-heads.py 已批量切单帧到 portraits/heads/
    // *_head.png，DialogModal 改走单帧 PNG 路径。
    expect(left.attributes('src')).toBe('/assets/pixel/portraits/heads/portrait_test_npc_head.png');
    expect(wrapper.find('[data-testid="dialog-portrait-right"]').exists()).toBe(false);
  });

  it('uses black ink for the npc name and dialog copy', () => {
    const store = primeLegacy();
    store.friendship = 2;
    const wrapper = mount(DialogModal);

    expect(wrapper.get('.dialog-head').attributes('style')).toContain('color: rgb(17, 17, 17)');
    expect(wrapper.get('.name-tag').attributes('style')).toContain('color: rgb(17, 17, 17)');
    expect(wrapper.get('[data-testid="dialog-text"]').attributes('style')).toContain(
      'color: rgb(17, 17, 17)',
    );
    expect(getComputedStyle(wrapper.get('[data-testid="dialog-friendship"]').element).color).toBe(
      'rgb(17, 17, 17)',
    );
  });

  it('renders branch buttons when hasBranches=true and clicking one calls store.chooseBranch', async () => {
    const store = primeWithTree();
    const spy = vi.spyOn(store, 'chooseBranch');
    const wrapper = mount(DialogModal);
    await vi.advanceTimersByTimeAsync(30 * 5);
    await wrapper.vm.$nextTick();

    expect(store.isTypingDone).toBe(true);
    expect(store.hasBranches).toBe(true);
    const buttons = wrapper.findAll('[data-testid^="dialog-choice-"]');
    expect(buttons).toHaveLength(2);
    expect(buttons[0]?.text()).toBe('A');
    expect(buttons[1]?.text()).toBe('B');

    await buttons[1]!.trigger('click');
    expect(spy).toHaveBeenCalledWith('B');
  });

  it('Enter on completed typewriter with branches triggers chooseBranch on highlighted choice', async () => {
    const store = primeWithTree();
    const spy = vi.spyOn(store, 'chooseBranch');
    const wrapper = mount(DialogModal);
    await vi.advanceTimersByTimeAsync(30 * 5);
    await wrapper.vm.$nextTick();
    expect(store.hasBranches).toBe(true);

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));

    expect(spy).toHaveBeenCalledWith('A');
  });

  it('ArrowDown moves the highlighted choice and updates the highlight class', async () => {
    primeWithTree();
    const wrapper = mount(DialogModal);
    await vi.advanceTimersByTimeAsync(30 * 5);
    await wrapper.vm.$nextTick();

    const buttons = () => wrapper.findAll('[data-testid^="dialog-choice-"]');
    expect(buttons()[0]?.classes()).toContain('choice-btn--highlight');

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
    await wrapper.vm.$nextTick();
    expect(buttons()[1]?.classes()).toContain('choice-btn--highlight');
    expect(buttons()[0]?.classes()).not.toContain('choice-btn--highlight');
  });

  it('Escape key closes the dialog via store.close', async () => {
    const store = primeLegacy();
    const closeSpy = vi.spyOn(store, 'close');
    mount(DialogModal);
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(closeSpy).toHaveBeenCalledTimes(1);
  });
});
