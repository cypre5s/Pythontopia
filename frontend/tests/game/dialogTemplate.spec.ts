/**
 * dialogTemplate 渲染器单元测试。
 *
 * 覆盖：
 *   1. {{playerName}} 替换 + 缺省兜底；
 *   2. {{stats.xxx}} 替换 + 未提供时 '—' 兜底，UI 不泄露字面量；
 *   3. {{profileAdaptive:A | B}} 按 profileType 选分支；
 *   4. {{giftFavorite:A | B}} 按 giftJustGiven 选分支；
 *   5. 嵌套（profileAdaptive 内含 stats）正确解析；
 *   6. 残余无法解析的占位符兜底成 '—'，不漏到 UI；
 *   7. createDialogContext 默认值合理。
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

  it('honours giftJustGiven when picking giftFavorite branch', () => {
    const happy = createDialogContext({ giftJustGiven: true });
    expect(renderDialogText('{{giftFavorite:这茶味，是老味道。 | 收下了。}}', happy)).toBe(
      '这茶味，是老味道。',
    );
    const plain = createDialogContext({ giftJustGiven: false });
    expect(renderDialogText('{{giftFavorite:这茶味，是老味道。 | 收下了。}}', plain)).toBe(
      '收下了。',
    );
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
    expect(ctx.giftJustGiven).toBe(false);
    expect(ctx.stats).toEqual({});
  });

  it('preserves explicit values', () => {
    const ctx = createDialogContext({
      playerName: '阿杰',
      profileType: 'practical',
      giftJustGiven: true,
      stats: { foo: 'bar' },
    });
    expect(ctx.playerName).toBe('阿杰');
    expect(ctx.profileType).toBe('practical');
    expect(ctx.giftJustGiven).toBe(true);
    expect(ctx.stats).toEqual({ foo: 'bar' });
  });
});
