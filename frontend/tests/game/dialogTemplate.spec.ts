/**
 * dialogTemplate 渲染器单元测试。
 *
 * 覆盖：
 *   1. {{playerName}} 替换 + 缺省兜底；
 *   2. {{stats.xxx}} 替换 + 未提供时 '—' 兜底，UI 不泄露字面量；
 *   3. {{profileAdaptive:A | B}} 按 profileType 选分支；
 *   4. 嵌套（profileAdaptive 内含 stats）正确解析；
 *   5. 残余无法解析的占位符兜底成 '—'，不漏到 UI；
 *   6. createDialogContext 默认值合理。
 */
import { describe, expect, it } from 'vitest';
import { createDialogContext, renderDialogText } from '@/game/dialogTemplate';

describe('renderDialogText', () => {
  it('replaces {{playerName}} with the supplied player name', () => {
    const ctx = createDialogContext({ playerName: '小七' });
    expect(renderDialogText('欢迎，{{playerName}}！', ctx)).toBe('欢迎，小七！');
  });

  it('falls back to "学习者" when playerName is empty', () => {
    const ctx = createDialogContext({ playerName: '' });
    expect(renderDialogText('{{playerName}}', ctx)).toBe('学习者');
  });

  it('substitutes stats from the context map', () => {
    const ctx = createDialogContext({
      stats: { exercisesThisWeek: '7', kcUnlockedThisWeek: '3' },
    });
    expect(
      renderDialogText(
        '本周你做了 {{stats.exercisesThisWeek}} 道题，解锁 {{stats.kcUnlockedThisWeek}} 个 KC。',
        ctx,
      ),
    ).toBe('本周你做了 7 道题，解锁 3 个 KC。');
  });

  it('renders missing stats as "—" instead of leaking the placeholder', () => {
    const ctx = createDialogContext({ stats: {} });
    expect(renderDialogText('最难的是 "{{stats.toughestKcNameThisWeek}}"。', ctx)).toBe(
      '最难的是 "—"。',
    );
  });

  it('chooses the practical branch when profileType is practical', () => {
    const ctx = createDialogContext({ profileType: 'practical' });
    expect(
      renderDialogText('{{profileAdaptive:先动手 | 先看课件}}', ctx),
    ).toBe('先动手');
  });

  it('chooses the cautious branch by default (cautious / unknown)', () => {
    const practical = createDialogContext({ profileType: 'cautious' });
    expect(
      renderDialogText('{{profileAdaptive:先动手 | 先看课件}}', practical),
    ).toBe('先看课件');
    const unknown = createDialogContext({ profileType: 'balanced' });
    expect(
      renderDialogText('{{profileAdaptive:先动手 | 先看课件}}', unknown),
    ).toBe('先看课件');
  });

  it('resolves nested placeholders (inner stats inside profileAdaptive)', () => {
    const ctx = createDialogContext({
      profileType: 'cautious',
      stats: { currentChapterOrder: '3' },
    });
    expect(
      renderDialogText(
        '{{profileAdaptive:你已经过了第 4 章入口 — 走吧。 | 你还在第 {{stats.currentChapterOrder}} 章}}',
        ctx,
      ),
    ).toBe('你还在第 3 章');
  });

  it('picks the visual branch of styleAdaptive when cognitiveStyle is visual', () => {
    const ctx = createDialogContext({ cognitiveStyle: 'visual' });
    expect(renderDialogText('{{styleAdaptive:看图解|读代码|跑例子}}', ctx)).toBe('看图解');
  });

  it('picks the logical branch of styleAdaptive when cognitiveStyle is logical', () => {
    const ctx = createDialogContext({ cognitiveStyle: 'logical' });
    expect(renderDialogText('{{styleAdaptive:看图解|读代码|跑例子}}', ctx)).toBe('读代码');
  });

  it('picks the trial branch of styleAdaptive when cognitiveStyle is trial', () => {
    const ctx = createDialogContext({ cognitiveStyle: 'trial' });
    expect(renderDialogText('{{styleAdaptive:看图解|读代码|跑例子}}', ctx)).toBe('跑例子');
  });

  it('falls back to the first (visual) styleAdaptive branch when cognitiveStyle is unknown', () => {
    const unknown = createDialogContext({ cognitiveStyle: 'unknown' });
    expect(renderDialogText('{{styleAdaptive:看图解|读代码|跑例子}}', unknown)).toBe('看图解');
    // 未提供 cognitiveStyle 时 createDialogContext 默认 'unknown'，同样回退首段。
    const omitted = createDialogContext({});
    expect(renderDialogText('{{styleAdaptive:看图解|读代码|跑例子}}', omitted)).toBe('看图解');
    // 非法风格值同样兜底首段，不泄露占位符。
    const weird = createDialogContext({ cognitiveStyle: 'kinesthetic' });
    expect(renderDialogText('{{styleAdaptive:看图解|读代码|跑例子}}', weird)).toBe('看图解');
  });

  it('falls back to the visual branch when the chosen styleAdaptive branch is empty', () => {
    const ctx = createDialogContext({ cognitiveStyle: 'logical' });
    expect(renderDialogText('{{styleAdaptive:看图解||跑例子}}', ctx)).toBe('看图解');
  });

  it('resolves nested stats inside a styleAdaptive branch', () => {
    const ctx = createDialogContext({
      cognitiveStyle: 'logical',
      stats: { currentChapterOrder: '2' },
    });
    expect(
      renderDialogText('{{styleAdaptive:先看图|顺着第 {{stats.currentChapterOrder}} 章代码读|先跑例子}}', ctx),
    ).toBe('顺着第 2 章代码读');
  });

  it('keeps profileAdaptive working when styleAdaptive is also present (back-compat)', () => {
    const ctx = createDialogContext({ profileType: 'practical', cognitiveStyle: 'trial' });
    expect(
      renderDialogText('{{profileAdaptive:先动手|先看课件}}，{{styleAdaptive:看图|读码|跑例}}', ctx),
    ).toBe('先动手，跑例');
  });

  it('returns empty string for empty input', () => {
    const ctx = createDialogContext({});
    expect(renderDialogText('', ctx)).toBe('');
  });

  it('replaces orphaned unrenderable placeholders with "—" instead of leaking braces', () => {
    const ctx = createDialogContext({});
    // 内层缺数据 + 外层非法 tag，应当兜底为 '—'，不留 {{
    const out = renderDialogText('开始 {{unknownTag:foo|bar}} 结束', ctx);
    expect(out).toBe('开始 — 结束');
    expect(out.includes('{{')).toBe(false);
  });

  it('handles multiple placeholders on the same line independently', () => {
    const ctx = createDialogContext({
      playerName: '小七',
      stats: { kcUnlocked: '5', masteryAvg: '62' },
    });
    expect(
      renderDialogText(
        '{{playerName}}，你解锁了 {{stats.kcUnlocked}} 个知识点，掌握度均值 {{stats.masteryAvg}}。',
        ctx,
      ),
    ).toBe('小七，你解锁了 5 个知识点，掌握度均值 62。');
  });
});

describe('createDialogContext', () => {
  it('fills sensible defaults when fields are omitted', () => {
    const ctx = createDialogContext({});
    expect(ctx.playerName).toBe('学习者');
    expect(ctx.profileType).toBe('cautious');
    expect(ctx.cognitiveStyle).toBe('unknown');
    expect(ctx.stats).toEqual({});
  });

  it('preserves explicit values', () => {
    const ctx = createDialogContext({
      playerName: '阿杰',
      profileType: 'practical',
      cognitiveStyle: 'logical',
      stats: { foo: 'bar' },
    });
    expect(ctx.playerName).toBe('阿杰');
    expect(ctx.profileType).toBe('practical');
    expect(ctx.cognitiveStyle).toBe('logical');
    expect(ctx.stats).toEqual({ foo: 'bar' });
  });
});
