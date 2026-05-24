import { mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { computed, defineComponent, h, nextTick } from 'vue';
import { __internal__, useI18n } from '@/composables/useI18n';

describe('useI18n', () => {
  beforeEach(() => {
    // 每个用例从 zh-CN 起步：模块级 ref 跨测试共享，必须显式归位。
    useI18n().setLocale('zh-CN');
  });

  afterEach(() => {
    useI18n().setLocale('zh-CN');
  });

  it('返回默认 zh-CN 翻译并命中嵌套 key', () => {
    const { t, locale } = useI18n();
    expect(locale.value).toBe('zh-CN');
    expect(t('hud.btn.confirm')).toBe('确认');
    expect(t('hud.label.energy')).toBe('能量');
  });

  it('未知 key fallback 返回 key 本身（与底层 t 行为一致）', () => {
    const { t } = useI18n();
    expect(t('not.exist.key.path')).toBe('not.exist.key.path');
    expect(t('hud.btn.__none__')).toBe('hud.btn.__none__');
  });

  it('插值替换 `{name}` 单花括号占位符', () => {
    const { t } = useI18n();
    expect(t('toast.itemGet', { item: '种子袋' })).toBe('收到 种子袋，已放进背包');
    expect(t('toast.kcUp', { kcName: 'for 循环' })).toBe('KC「for 循环」掌握度提升');
  });

  it('多个占位符同时替换 + 缺失参数保留原字面量', () => {
    const { t } = useI18n();
    const full = t('tpl.classroom.read', {
      chapter: '第 4 章',
      pageStart: 124,
      pageEnd: 140,
      due: '周日 22:00',
    });
    expect(full).toBe('本周阅读：第 4 章（p124–p140），周日 22:00 截止');

    // 缺失 chapter / due 时占位符原样保留，便于调试发现哪条没传参。
    const partial = t('tpl.classroom.read', { pageStart: 1, pageEnd: 2 });
    expect(partial).toBe('本周阅读：{chapter}（p1–p2），{due} 截止');
  });

  it('zh-CN 下 tn() 不做任何复数变形，仅替换 {count}', () => {
    const { tn, setLocale } = useI18n();
    setLocale('zh-CN');
    // zh-CN.json 中的 itemGet 末尾是中文，复数不应影响。
    expect(tn('toast.itemGet', 5, { item: '金币' })).toBe('收到 金币，已放进背包');

    // 直接验证 {count} 注入：使用一个含 {count} 的英文 key，但当前是 zh-CN 透传。
    setLocale('en-US');
    expect(tn('toast.bgmStart', 1)).toBe('Music on.');
  });

  it('en-US 下 tn() count=1 走单数；count>1 末尾名词 +s', () => {
    const { tn, setLocale, t } = useI18n();
    setLocale('en-US');
    // en-US.json 中 itemGet = "Got {item}. Check your bag."
    // 末尾英文段是 "bag" → 加 s 应得 "bags"。
    expect(tn('toast.itemGet', 1, { item: 'seed' })).toBe('Got seed. Check your bag.');
    expect(tn('toast.itemGet', 5, { item: 'seed' })).toBe('Got seed. Check your bags.');

    // 普通 t() 不复数。
    expect(t('toast.itemGet', { item: 'seed' })).toBe('Got seed. Check your bag.');
  });

  it('英文复数尾词命中 sibilant / 辅音+y 规则（es / ies）', () => {
    const { pluralizeEnglishTail } = __internal__;
    expect(pluralizeEnglishTail('1 box')).toBe('1 boxes');
    expect(pluralizeEnglishTail('1 bus')).toBe('1 buses');
    expect(pluralizeEnglishTail('1 dish')).toBe('1 dishes');
    expect(pluralizeEnglishTail('1 watch')).toBe('1 watches');
    expect(pluralizeEnglishTail('1 quiz')).toBe('1 quizes');
    expect(pluralizeEnglishTail('1 city')).toBe('1 cities');
    // 元音 + y 不变 y → ys（如 day → days）。
    expect(pluralizeEnglishTail('2 day')).toBe('2 days');
    // 末尾带标点：插在标点之前。
    expect(pluralizeEnglishTail('Got coin.')).toBe('Got coins.');
    // 末尾无字母时退化为整串后缀 s。
    expect(pluralizeEnglishTail('123')).toBe('123s');
  });

  it('setLocale 同步触发组件响应式重渲染', async () => {
    const Probe = defineComponent({
      name: 'I18nProbe',
      setup() {
        const { t, locale } = useI18n();
        const label = computed(() => `${locale.value}|${t('hud.btn.confirm')}`);
        return () => h('span', { 'data-testid': 'label' }, label.value);
      },
    });

    const wrapper = mount(Probe);
    expect(wrapper.get('[data-testid="label"]').text()).toBe('zh-CN|确认');

    useI18n().setLocale('en-US');
    await nextTick();
    expect(wrapper.get('[data-testid="label"]').text()).toBe('en-US|Confirm');

    useI18n().setLocale('zh-CN');
    await nextTick();
    expect(wrapper.get('[data-testid="label"]').text()).toBe('zh-CN|确认');

    wrapper.unmount();
  });

  it('多个组件共享模块级 ref —— 单次 setLocale 全员刷新', async () => {
    const Probe = defineComponent({
      name: 'I18nMultiProbe',
      props: { tid: { type: String, required: true } },
      setup(props) {
        const { t, locale } = useI18n();
        return () =>
          h('span', { 'data-testid': props.tid }, `${locale.value}:${t('hud.btn.cancel')}`);
      },
    });

    const wrapperA = mount(Probe, { props: { tid: 'a' } });
    const wrapperB = mount(Probe, { props: { tid: 'b' } });
    expect(wrapperA.get('[data-testid="a"]').text()).toBe('zh-CN:取消');
    expect(wrapperB.get('[data-testid="b"]').text()).toBe('zh-CN:取消');

    useI18n().setLocale('en-US');
    await nextTick();
    expect(wrapperA.get('[data-testid="a"]').text()).toBe('en-US:Cancel');
    expect(wrapperB.get('[data-testid="b"]').text()).toBe('en-US:Cancel');

    wrapperA.unmount();
    wrapperB.unmount();
  });

  it('setLocale 拒绝不支持的 locale，保留原值', () => {
    const { setLocale, locale, t } = useI18n();
    setLocale('en-US');
    expect(locale.value).toBe('en-US');

    // ja-JP 未在 SUPPORTED_LOCALES 中，被忽略；locale 仍为 en-US。
    setLocale('ja-JP' as unknown as 'en-US');
    expect(locale.value).toBe('en-US');
    expect(t('hud.btn.confirm')).toBe('Confirm');
  });

  it('useI18n() 多次调用返回同一个 locale ref（单例稳定）', () => {
    const first = useI18n();
    const second = useI18n();
    expect(first.locale).toBe(second.locale);
    expect(first.locale).toBe(__internal__.localeRef);
  });

  it('interpolate 支持数字参数（转字符串）', () => {
    const { interpolate } = __internal__;
    expect(interpolate('count={count}', { count: 0 })).toBe('count=0');
    expect(interpolate('p{pageStart}-p{pageEnd}', { pageStart: 12, pageEnd: 34 })).toBe(
      'p12-p34'
    );
    expect(interpolate('no params here', {})).toBe('no params here');
  });
});
