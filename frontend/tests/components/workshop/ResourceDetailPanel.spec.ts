/**
 * ResourceDetailPanel × GlassBox 决策玻璃盒挂载单测（P7.1 验收）。
 *
 * 背景：GlassBox.vue 此前为孤儿组件（无人引用）。本测验证它已被挂载在「工坊资源详情」
 * 这一真实决策点上，使「任意决策点可展开为什么」成立，并消费既有 GET /api/learner/decisions。
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import type { WorkshopResourceDto } from '@/api/dto/Workshop';

const httpMocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delete: vi.fn(),
}));

vi.mock('@/api/http', () => ({
  http: httpMocks,
  setTokenProvider: vi.fn(),
  setUnauthorizedHandler: vi.fn(),
  setNetworkErrorHandler: vi.fn(),
  __resetNetworkErrorCooldownForTest: vi.fn(),
  ApiClientError: class ApiClientError extends Error {},
}));

import ResourceDetailPanel from '@/components/workshop/ResourceDetailPanel.vue';
import GlassBox from '@/components/companion/GlassBox.vue';
import { useLearningStore } from '@stores/learning';

const RESOURCE: WorkshopResourceDto = {
  id: 7,
  userId: 1,
  packId: 43,
  kcCodes: ['PY43-KC-001'],
  chapterId: null,
  type: 'HANDOUT',
  title: '测试讲义',
  contentFormat: 'STRUCT_JSON',
  content: { sections: [] },
  evidencePageIds: [],
  summary: '摘要',
  safetyStatus: 'PASSED',
  groundingStatus: 'PASSED',
  feedbackStatus: 'NONE',
  feedbackNote: null,
  createdAt: '2026-06-01T00:00:00Z',
};

// 内容子视图非本测关注点，桩掉以聚焦决策盒挂载；GlassBox 保持真实组件。
const CONTENT_STUBS = {
  HandoutView: true,
  MindMapView: true,
  PracticeList: true,
  CodeLabView: true,
  ReadingList: true,
  StoryboardView: true,
  PptView: true,
};

function mountPanel(): ReturnType<typeof mount> {
  const learning = useLearningStore();
  learning.resources.current = { ...RESOURCE };
  return mount(ResourceDetailPanel, {
    // resourceId=null：immediate watcher 不触发 openResource（直接用已注入的 current）。
    props: { resourceId: null },
    global: {
      stubs: {
        ...CONTENT_STUBS,
        RouterLink: { template: '<a><slot /></a>' },
      },
    },
  });
}

describe('ResourceDetailPanel · 决策玻璃盒挂载（P7.1）', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    httpMocks.get.mockReset().mockResolvedValue({ data: [] });
    httpMocks.post.mockReset().mockResolvedValue({ data: undefined });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('在资源详情内渲染 GlassBox 决策盒，并消费 GET /api/learner/decisions', async () => {
    const wrapper = mountPanel();
    await flushPromises();

    expect(wrapper.find('[data-testid="resource-detail"]').exists()).toBe(true);

    // 决策盒存在，且 GlassBox 渲染在其内部（真实组件，非桩）。
    const box = wrapper.find('[data-testid="resource-decision-box"]');
    expect(box.exists()).toBe(true);
    expect(box.text()).toContain('为什么这样生成');

    const glassBox = wrapper.findComponent(GlassBox);
    expect(glassBox.exists()).toBe(true);
    expect(box.findComponent(GlassBox).exists()).toBe(true);
    // GlassBox 实际渲染其根容器。
    expect(wrapper.find('.gb').exists()).toBe(true);

    // 使用既有决策端点（无需后端改动）。
    expect(httpMocks.get).toHaveBeenCalledWith('/api/learner/decisions');
  });

  it('GlassBox 把真实决策记录展示在决策盒内（证明挂载的是可工作的真实组件）', async () => {
    httpMocks.get.mockResolvedValueOnce({
      data: [
        {
          agent_name: 'ResourceDesignerAgent',
          scenario: '生成讲义',
          status: 'SUCCESS',
          evidence_page_ids: [12, 13],
          trace: '[]',
          ts: '2026-06-01T00:00:00Z',
        },
      ],
    });

    const wrapper = mountPanel();
    await flushPromises();

    const box = wrapper.find('[data-testid="resource-decision-box"]');
    expect(box.text()).toContain('ResourceDesignerAgent');
    expect(box.text()).toContain('生成讲义');
  });

  it('打开资源详情时记录消费，并展示 micro-check CTA', async () => {
    httpMocks.get.mockImplementation((url: string) => {
      if (url === '/api/workshop/resources/7') return Promise.resolve({ data: RESOURCE });
      return Promise.resolve({ data: [] });
    });
    httpMocks.post.mockResolvedValueOnce({
          data: {
            resourceId: 7,
            kcCodes: ['PY43-KC-001'],
            nextAction: 'micro_check',
            prompt: '用 1 分钟做个小检查：说出 input 的作用。',
            readRecorded: true,
            loopState: 'MICRO_CHECK_REQUIRED',
            evidenceRequired: 'micro_check_or_operation',
          },
        });

    const wrapper = mount(ResourceDetailPanel, {
      props: { resourceId: 7 },
      global: {
        stubs: {
          ...CONTENT_STUBS,
          RouterLink: { template: '<a><slot /></a>' },
        },
      },
    });
    await flushPromises();

    expect(httpMocks.post).toHaveBeenCalledWith('/api/workshop/resources/7/consumptions');
    expect(wrapper.find('[data-testid="resource-micro-check"]').text()).toContain('说出 input 的作用');
    const rail = wrapper.find('[data-testid="resource-loop-rail"]');
    expect(rail.exists()).toBe(true);
    expect(rail.text()).toContain('读');
    expect(rail.text()).toContain('测');
  });

  it('提交资源微测答案，并展示掌握度证据结果', async () => {
    httpMocks.get.mockImplementation((url: string) => {
      if (url === '/api/workshop/resources/7') return Promise.resolve({ data: RESOURCE });
      return Promise.resolve({ data: [] });
    });
    httpMocks.post
      .mockResolvedValueOnce({
          data: {
            resourceId: 7,
            kcCodes: ['PY43-KC-001'],
            nextAction: 'micro_check',
            prompt: '用 1 分钟做个小检查：说出 input 的作用。',
            readRecorded: true,
            loopState: 'MICRO_CHECK_REQUIRED',
            evidenceRequired: 'micro_check_or_operation',
          },
        })
      .mockResolvedValueOnce({
        data: {
          checkId: 31,
          resourceId: 7,
          kcCode: 'PY43-KC-001',
          status: 'PASSED',
          masteryDelta: 0.05,
          nextAction: 'pass',
          remediationType: null,
          feedback: '已命中资源关键点：input',
          loopState: 'PASSED',
          practiceSubmissionId: null,
          decisionReason: '微测通过，掌握度已写入。',
        },
      });

    const wrapper = mount(ResourceDetailPanel, {
      props: { resourceId: 7 },
      global: {
        stubs: {
          ...CONTENT_STUBS,
          RouterLink: { template: '<a><slot /></a>' },
        },
      },
    });
    await flushPromises();

    await wrapper.get('[data-testid="resource-check-answer"]').setValue('input 函数用于读取用户输入');
    await wrapper.get('[data-testid="resource-check-submit"]').trigger('click');
    await flushPromises();

    expect(httpMocks.post).toHaveBeenLastCalledWith('/api/workshop/resources/7/checks', {
      kcCode: 'PY43-KC-001',
      evidenceType: 'MICRO_QUIZ',
      answerText: 'input 函数用于读取用户输入',
    }, { headers: { 'Idempotency-Key': expect.stringMatching(/^[0-9a-f-]{36}$/) } });
    const result = wrapper.find('[data-testid="resource-check-result"]');
    expect(result.text()).toContain('PASSED');
    expect(result.text()).toContain('+0.05');
    expect(wrapper.find('[data-testid="resource-loop-state"]').text()).toContain('通过');
  });

  it('微测连续失败时用同一条行动轨显示换资源，不增加说明卡片', async () => {
    httpMocks.get.mockImplementation((url: string) => {
      if (url === '/api/workshop/resources/7') return Promise.resolve({ data: RESOURCE });
      return Promise.resolve({ data: [] });
    });
    httpMocks.post
      .mockResolvedValueOnce({
        data: {
          resourceId: 7,
          kcCodes: ['PY43-KC-001'],
          nextAction: 'micro_check',
          prompt: '说出 input 的作用。',
          readRecorded: true,
          loopState: 'MICRO_CHECK_REQUIRED',
          evidenceRequired: 'micro_check_or_operation',
        },
      })
      .mockResolvedValueOnce({
        data: {
          checkId: 32,
          resourceId: 7,
          kcCode: 'PY43-KC-001',
          status: 'FAILED',
          masteryDelta: -0.02,
          nextAction: 'swap_resource',
          remediationType: 'CODE_LAB',
          feedback: '还没抓住关键点。',
          loopState: 'NEED_RESOURCE_SWAP',
          practiceSubmissionId: null,
          decisionReason: '同一资源连续未过，换一种资源再学。',
        },
      });

    const wrapper = mount(ResourceDetailPanel, {
      props: { resourceId: 7 },
      global: {
        stubs: {
          ...CONTENT_STUBS,
          RouterLink: { template: '<a><slot /></a>' },
        },
      },
    });
    await flushPromises();

    await wrapper.get('[data-testid="resource-check-answer"]').setValue('我还是没看懂');
    await wrapper.get('[data-testid="resource-check-submit"]').trigger('click');
    await flushPromises();

    const rail = wrapper.find('[data-testid="resource-loop-rail"]');
    expect(rail.text()).toContain('换资源');
    expect(rail.text()).toContain('CODE_LAB');
    expect(wrapper.findAll('[data-testid="resource-loop-rail"]')).toHaveLength(1);
  });

  it('用最近训练提交作为 operation evidence 完成闭环', async () => {
    httpMocks.get.mockImplementation((url: string, config?: { params?: Record<string, unknown> }) => {
      if (url === '/api/workshop/resources/7') return Promise.resolve({ data: RESOURCE });
      if (url === '/api/submissions' && config?.params?.status === 'PASSED') {
        return Promise.resolve({
          data: [
            {
              id: 501,
              userId: 1,
              problemId: 1001,
              questId: null,
              source: 'TRAINING',
              language: 'PYTHON_3_11',
              code: 'print(input())',
              submittedAt: '2026-06-27T06:00:00Z',
              status: 'PASSED',
              runtimeMs: 10,
              memoryKb: 1024,
            },
          ],
        });
      }
      if (url === '/api/submissions' && config?.params?.status === 'FAILED') {
        return Promise.resolve({ data: [] });
      }
      return Promise.resolve({ data: [] });
    });
    httpMocks.post
      .mockResolvedValueOnce({
        data: {
          resourceId: 7,
          kcCodes: ['PY43-KC-001'],
          nextAction: 'micro_check',
          prompt: '说出 input 的作用。',
          readRecorded: true,
          loopState: 'MICRO_CHECK_REQUIRED',
          evidenceRequired: 'micro_check_or_operation',
        },
      })
      .mockResolvedValueOnce({
        data: {
          checkId: 33,
          resourceId: 7,
          kcCode: 'PY43-KC-001',
          status: 'PASSED',
          masteryDelta: 0.05,
          nextAction: 'pass',
          remediationType: null,
          feedback: '已收到通过判题的训练场提交。',
          loopState: 'PASSED',
          practiceSubmissionId: 501,
          decisionReason: '训练场提交已通过，闭环完成。',
        },
      });

    const wrapper = mount(ResourceDetailPanel, {
      props: { resourceId: 7 },
      global: {
        stubs: {
          ...CONTENT_STUBS,
          RouterLink: { template: '<a><slot /></a>' },
        },
      },
    });
    await flushPromises();

    await wrapper.get('[data-testid="resource-submission-check"]').trigger('click');
    await flushPromises();

    expect(httpMocks.get).toHaveBeenCalledWith('/api/submissions', {
      params: { status: 'PASSED', limit: 1 },
    });
    expect(httpMocks.post).toHaveBeenLastCalledWith('/api/workshop/resources/7/checks', {
      kcCode: 'PY43-KC-001',
      evidenceType: 'OPERATION',
      answerText: '训练场提交 #501',
      operationEvidence: {
        submissionId: 501,
        submission_id: 501,
        source: 'recent_submission',
        status: 'PASSED',
      },
    }, { headers: { 'Idempotency-Key': expect.stringMatching(/^[0-9a-f-]{36}$/) } });
    expect(wrapper.find('[data-testid="resource-loop-state"]').text()).toContain('通过');
    expect(wrapper.find('[data-testid="resource-check-result"]').text()).toContain('+0.05');
  });

  it('最近运行错误提交也作为失败 evidence 推进到重规划', async () => {
    httpMocks.get.mockImplementation((url: string, config?: { params?: Record<string, unknown> }) => {
      if (url === '/api/workshop/resources/7') return Promise.resolve({ data: RESOURCE });
      if (url === '/api/submissions' && config?.params?.status === 'PASSED') {
        return Promise.resolve({ data: [] });
      }
      if (url === '/api/submissions' && config?.params?.status === 'FAILED') {
        return Promise.resolve({ data: [] });
      }
      if (url === '/api/submissions' && config?.params?.status === 'ERROR') {
        return Promise.resolve({
          data: [
            {
              id: 502,
              userId: 1,
              problemId: 1001,
              questId: null,
              source: 'TRAINING',
              language: 'PYTHON_3_11',
              code: 'raise RuntimeError()',
              submittedAt: '2026-06-27T06:01:00Z',
              status: 'ERROR',
              runtimeMs: 10,
              memoryKb: 1024,
            },
          ],
        });
      }
      return Promise.resolve({ data: [] });
    });
    httpMocks.post
      .mockResolvedValueOnce({
        data: {
          resourceId: 7,
          kcCodes: ['PY43-KC-001'],
          nextAction: 'micro_check',
          prompt: '说出 input 的作用。',
          readRecorded: true,
          loopState: 'MICRO_CHECK_REQUIRED',
          evidenceRequired: 'micro_check_or_operation',
        },
      })
      .mockResolvedValueOnce({
        data: {
          checkId: 34,
          resourceId: 7,
          kcCode: 'PY43-KC-001',
          status: 'FAILED',
          masteryDelta: -0.02,
          nextAction: 'replan',
          remediationType: null,
          feedback: '这次训练场提交还没有通过，需要重新规划补救。',
          loopState: 'NEED_REPLAN',
          practiceSubmissionId: 502,
          decisionReason: '真实提交未通过，需要重规划学习路径。',
        },
      });

    const wrapper = mount(ResourceDetailPanel, {
      props: { resourceId: 7 },
      global: {
        stubs: {
          ...CONTENT_STUBS,
          RouterLink: { template: '<a><slot /></a>' },
        },
      },
    });
    await flushPromises();

    await wrapper.get('[data-testid="resource-submission-check"]').trigger('click');
    await flushPromises();

    expect(httpMocks.get).toHaveBeenCalledWith('/api/submissions', {
      params: { status: 'ERROR', limit: 1 },
    });
    expect(httpMocks.post).toHaveBeenLastCalledWith('/api/workshop/resources/7/checks', {
      kcCode: 'PY43-KC-001',
      evidenceType: 'OPERATION',
      answerText: '训练场提交 #502',
      operationEvidence: {
        submissionId: 502,
        submission_id: 502,
        source: 'recent_submission',
        status: 'ERROR',
      },
    }, { headers: { 'Idempotency-Key': expect.stringMatching(/^[0-9a-f-]{36}$/) } });
    expect(wrapper.find('[data-testid="resource-loop-state"]').text()).toContain('重规划');
  });
});
