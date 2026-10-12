import { describe, it, expect, vi, beforeEach } from 'vitest';

// 后端 BrowseController 此刻可能未就绪：mock 掉 http（axios 实例），只验证前端访问层与纯函数。
vi.mock('@/api/http', () => ({
  http: { get: vi.fn(), post: vi.fn() },
}));

import { http } from '@/api/http';
import {
  createBrowseRun,
  getBrowseRun,
  browseStepActionLabel,
  browseRunStatusLabel,
  browseRunStatusTone,
  isSafeBrowseUrl,
  browseUrlLabel,
  BROWSE_EXTERNAL_NOTICE,
  type BrowseRunDto,
  type BrowseRunDetailDto,
} from '@/api/browse';

const RUN: BrowseRunDto = {
  id: 11,
  goal: '查 sorted 的稳定性',
  status: 'SUCCESS',
  finalSummary: 'sorted 是稳定排序。',
  truncated: false,
  error: '',
  createdAt: '2026-06-17T05:00:00Z',
};

const DETAIL: BrowseRunDetailDto = {
  ...RUN,
  steps: [
    {
      index: 0,
      action: 'navigate',
      url: 'https://docs.python.org/3/howto/sorting.html',
      summary: '打开官方排序指南',
      title: 'Sorting HOW TO',
      snippet: 'Sorts are guaranteed to be stable.',
    },
    {
      index: 1,
      action: 'read',
      url: '',
      summary: '读到 sorts are guaranteed to be stable',
      title: '',
      snippet: '读到 sorts are guaranteed to be stable',
    },
  ],
  citations: [
    {
      url: 'https://docs.python.org/3/howto/sorting.html',
      label: '外部内容，非课件',
    },
  ],
};

beforeEach(() => {
  vi.clearAllMocks();
});

describe('browse.ts · REST 访问层（DTO 类型与序列化）', () => {
  it('createBrowseRun：POST /api/browse/runs，body {goal, maxSteps} → BrowseRunDto', async () => {
    vi.mocked(http.post).mockResolvedValue({ data: RUN });

    const dto = await createBrowseRun({ goal: '查 sorted 的稳定性', maxSteps: 4 });

    expect(dto).toEqual(RUN);
    expect(http.post).toHaveBeenCalledTimes(1);
    expect(http.post).toHaveBeenCalledWith('/api/browse/runs', {
      goal: '查 sorted 的稳定性',
      maxSteps: 4,
    });
  });

  it('createBrowseRun：仅 goal（maxSteps 可选省略，不下发该字段）', async () => {
    vi.mocked(http.post).mockResolvedValue({ data: RUN });

    await createBrowseRun({ goal: '看看官方文档' });

    expect(http.post).toHaveBeenCalledWith('/api/browse/runs', { goal: '看看官方文档' });
  });

  it('getBrowseRun：GET /api/browse/runs/{id} → BrowseRunDetailDto（含 steps/citations）', async () => {
    vi.mocked(http.get).mockResolvedValue({ data: DETAIL });

    const dto = await getBrowseRun(11);

    expect(http.get).toHaveBeenCalledWith('/api/browse/runs/11');
    expect(dto.steps).toHaveLength(2);
    expect(dto.steps[0]?.action).toBe('navigate');
    expect(dto.steps[0]?.url).toBe('https://docs.python.org/3/howto/sorting.html');
    expect(dto.steps[0]?.title).toBe('Sorting HOW TO');
    expect(dto.steps[0]?.snippet).toBe('Sorts are guaranteed to be stable.');
    expect(dto.steps[1]?.url).toBe('');
    expect(dto.citations).toEqual([
      {
        url: 'https://docs.python.org/3/howto/sorting.html',
        label: '外部内容，非课件',
      },
    ]);
    expect(dto.truncated).toBe(false);
  });
});

describe('browse.ts · 纯函数（展示 / 安全）', () => {
  it('browseStepActionLabel：四种动作中文，未知动作回退原值', () => {
    expect(browseStepActionLabel('navigate')).toBe('打开');
    expect(browseStepActionLabel('read')).toBe('阅读');
    expect(browseStepActionLabel('search')).toBe('搜索');
    expect(browseStepActionLabel('stop')).toBe('结束');
    expect(browseStepActionLabel('weird')).toBe('weird');
  });

  it('browseRunStatusLabel / browseRunStatusTone：已知映射 + 未知优雅降级', () => {
    expect(browseRunStatusLabel('SUCCESS')).toBe('查证完成');
    expect(browseRunStatusLabel('FAILED')).toBe('查证失败');
    expect(browseRunStatusLabel('TRUNCATED')).toBe('已达步数上限');
    expect(browseRunStatusLabel('UNKNOWN_X')).toBe('UNKNOWN_X');

    expect(browseRunStatusTone('SUCCESS')).toBe('done');
    expect(browseRunStatusTone('FAILED')).toBe('failed');
    expect(browseRunStatusTone('TRUNCATED')).toBe('warn');
    expect(browseRunStatusTone('RUNNING')).toBe('active');
    expect(browseRunStatusTone('UNKNOWN_X')).toBe('pending');
  });

  it('isSafeBrowseUrl：仅 http(s) 安全；javascript:/空/垃圾 → false（防注入）', () => {
    expect(isSafeBrowseUrl('https://docs.python.org')).toBe(true);
    expect(isSafeBrowseUrl('http://pypi.org')).toBe(true);
    expect(isSafeBrowseUrl('javascript:alert(1)')).toBe(false);
    expect(isSafeBrowseUrl('data:text/html,<x>')).toBe(false);
    expect(isSafeBrowseUrl('')).toBe(false);
    expect(isSafeBrowseUrl('not a url')).toBe(false);
  });

  it('browseUrlLabel：host+path，超长截断；根路径去尾斜杠；非法回退', () => {
    expect(browseUrlLabel('https://docs.python.org/3/howto/sorting.html')).toBe(
      'docs.python.org/3/howto/sorting.html',
    );
    expect(browseUrlLabel('https://docs.python.org/')).toBe('docs.python.org');
    expect(browseUrlLabel('')).toBe('');
    // 非法 url（new URL 抛错）→ 回退原串。
    expect(browseUrlLabel('not a url')).toBe('not a url');

    const long = `https://example.com/${'a'.repeat(80)}`;
    const label = browseUrlLabel(long);
    expect(label.endsWith('…')).toBe(true);
    expect(label.length).toBeLessThanOrEqual(48);
  });

  it('BROWSE_EXTERNAL_NOTICE 文案稳定（红线：外部内容统一标注的单一事实源）', () => {
    expect(BROWSE_EXTERNAL_NOTICE).toBe('外部内容，非课件');
  });
});
