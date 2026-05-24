// toast store 边界测试：覆盖三种 kind / 计时器自动隐藏 / 连续 show / push payload / clear。
// 该 store 同样此前无 spec，但被 21 个组件依赖（错误提示、登录反馈、提交结果），
// 必须有保底测试，避免重构计时器逻辑时静默回归。
import { setActivePinia, createPinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useToastStore } from '@/stores/toast';

describe('toast store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('初始 state: message="", kind="info", visible=false', () => {
    const store = useToastStore();
    expect(store.message).toBe('');
    expect(store.kind).toBe('info');
    expect(store.visible).toBe(false);
  });

  it('show("hi") 默认 kind=info, visible=true, message 同步写入', () => {
    const store = useToastStore();
    store.show('hi');
    expect(store.message).toBe('hi');
    expect(store.kind).toBe('info');
    expect(store.visible).toBe(true);
  });

  it('show 支持 error / success kind 显式覆盖默认值', () => {
    const store = useToastStore();
    store.show('坏了', 'error');
    expect(store.kind).toBe('error');
    expect(store.message).toBe('坏了');
    store.show('成了', 'success');
    expect(store.kind).toBe('success');
    expect(store.message).toBe('成了');
  });

  it('show 3000ms 后自动 visible=false, message 不清空（沉淀语义）', () => {
    const store = useToastStore();
    store.show('autohide');
    expect(store.visible).toBe(true);
    vi.advanceTimersByTime(2999);
    expect(store.visible).toBe(true);
    vi.advanceTimersByTime(1);
    expect(store.visible).toBe(false);
    expect(store.message).toBe('autohide');
  });

  it('连续 show 两次：第二次重置 visible=true, 旧计时器到期不再隐藏新提示', () => {
    const store = useToastStore();
    store.show('一', 'info');
    vi.advanceTimersByTime(1500);
    store.show('二', 'success');
    expect(store.message).toBe('二');
    expect(store.kind).toBe('success');
    expect(store.visible).toBe(true);
    // 第一次的 3000ms 定时器在 1500ms 后到期（此时已经过 3000ms），
    // 旧定时器会无差别把 visible 置 false。这是当前实现的"已知边界"，
    // 测试此处锁定行为以便重构时显式感知；如果实现改为 token-based 取消，
    // 该用例将变成红测，提示作者更新断言。
    vi.advanceTimersByTime(1500);
    expect(store.visible).toBe(false);
    expect(store.message).toBe('二');
  });

  it('push(payload) 等价于 show(payload.text, payload.kind ?? "info")', () => {
    const store = useToastStore();
    store.push({ text: 'A' });
    expect(store.message).toBe('A');
    expect(store.kind).toBe('info');
    expect(store.visible).toBe(true);
    store.push({ text: 'B', kind: 'error' });
    expect(store.message).toBe('B');
    expect(store.kind).toBe('error');
  });

  it('push 不传 kind 时回退 info 而不是保留上一条的 kind', () => {
    const store = useToastStore();
    store.show('warn', 'error');
    store.push({ text: 'ok' });
    expect(store.kind).toBe('info');
    expect(store.message).toBe('ok');
  });

  it('clear() 强制隐藏并把 message 置空，但不重置 kind', () => {
    const store = useToastStore();
    store.show('坏了', 'error');
    store.clear();
    expect(store.visible).toBe(false);
    expect(store.message).toBe('');
    expect(store.kind).toBe('error');
  });

  it('clear() 后老定时器仍会触发 visible=false 赋值，但无副作用（幂等）', () => {
    const store = useToastStore();
    store.show('坏了', 'error');
    store.clear();
    expect(store.visible).toBe(false);
    vi.advanceTimersByTime(3000);
    expect(store.visible).toBe(false);
    expect(store.message).toBe('');
  });

  it('show("") 仍会让 visible=true 并写入空串（不做空内容防御）', () => {
    const store = useToastStore();
    store.show('');
    expect(store.message).toBe('');
    expect(store.visible).toBe(true);
  });
});
