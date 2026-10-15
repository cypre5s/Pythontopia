import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type {
  BossProblemReviewDto,
  BossProblemReviewPageDto,
} from '@/api/problemReview';
import TeacherReviewPage from '../TeacherReviewPage.vue';

const { searchProblemReviews, listProblemReviews, createProblemReview, decideProblemReview, toastShow } = vi.hoisted(() => ({
  searchProblemReviews: vi.fn(),
  listProblemReviews: vi.fn(),
  createProblemReview: vi.fn(),
  decideProblemReview: vi.fn(),
  toastShow: vi.fn(),
}));

vi.mock('@/api/problemReview', () => ({
  searchProblemReviews,
  listProblemReviews,
  createProblemReview,
  decideProblemReview,
}));
vi.mock('@/stores/session', () => ({ useSessionStore: () => ({ role: 'TEACHER' }) }));
vi.mock('@/stores/toast', () => ({ useToastStore: () => ({ show: toastShow }) }));
vi.mock('@/app/AppLayout.vue', () => ({ default: { template: '<div><slot /></div>' } }));
vi.mock('@/components/workshop/RuntimeOperationsPanel.vue', () => ({
  default: { template: '<div data-testid="runtime-operations" />' },
}));
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }));

const firstReview: BossProblemReviewDto = {
  id: 1,
  packId: 43,
  sourceProblemId: 'review-1',
  kind: 'CODE',
  problem: {
    problemId: 'review-1',
    chapter: 1,
    stage: 1,
    difficulty: 1,
    kcCode: 'PY43-KC-001',
    statement: '输出数字 1',
    hints: [],
    sampleInput: '',
    sampleOutput: '1',
    totalTestCases: 1,
    reviewed: true,
    aiGenerated: true,
  },
  decision: 'PENDING',
  reviewSource: 'TEACHER',
  requesterId: 3,
  requesterUsername: 'student3',
  requestedAt: '2026-10-15T00:00:00Z',
  deciderId: null,
  deciderUsername: null,
  decidedAt: null,
  decisionReason: null,
  version: 0,
};

function page(
  content: BossProblemReviewDto[],
  currentPage: number,
  totalElements: number,
): BossProblemReviewPageDto {
  return {
    content,
    page: currentPage,
    size: 100,
    totalElements,
    totalPages: Math.ceil(totalElements / 100),
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  listProblemReviews.mockResolvedValue([firstReview]);
  searchProblemReviews.mockResolvedValue(page([firstReview], 0, 1));
});

describe('TeacherReviewPage queue', () => {
  it('loads every server page instead of stopping at the latest 100 records', async () => {
    const secondReview = {
      ...firstReview,
      id: 101,
      sourceProblemId: 'review-101',
      problem: { ...firstReview.problem, problemId: 'review-101' },
    };
    searchProblemReviews
      .mockResolvedValueOnce(page([firstReview], 0, 101))
      .mockResolvedValueOnce(page([secondReview], 1, 101));

    const wrapper = mount(TeacherReviewPage);
    await flushPromises();

    expect(searchProblemReviews).toHaveBeenCalledWith({
      page: 0,
      size: 100,
      sortBy: 'REQUESTED_AT',
      sortDirection: 'DESC',
    });
    expect(listProblemReviews).not.toHaveBeenCalled();
    await wrapper.get('[data-testid="teacher-review-next-page"]').trigger('click');
    await flushPromises();

    expect(searchProblemReviews).toHaveBeenLastCalledWith({
      page: 1,
      size: 100,
      sortBy: 'REQUESTED_AT',
      sortDirection: 'DESC',
    });
    expect(wrapper.text()).toContain('review-101');
    expect(wrapper.find('[data-testid="teacher-review-select-1"]').exists()).toBe(false);
  });

  it('shows a retryable error instead of presenting a failed load as an empty queue', async () => {
    searchProblemReviews
      .mockRejectedValueOnce(new Error('network unavailable'))
      .mockResolvedValueOnce(page([firstReview], 0, 1));

    const wrapper = mount(TeacherReviewPage);
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain('network unavailable');
    expect(wrapper.text()).not.toContain('队列为空');
    await wrapper.get('[data-testid="teacher-review-retry"]').trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('review-1');
    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    expect(searchProblemReviews).toHaveBeenCalledTimes(2);
  });

  it('keeps a newer moderation decision when an older queue refresh finishes afterward', async () => {
    let resolveRefresh: (result: BossProblemReviewPageDto) => void = () => undefined;
    searchProblemReviews
      .mockResolvedValueOnce(page([firstReview], 0, 1))
      .mockReturnValueOnce(new Promise((resolve) => { resolveRefresh = resolve; }));
    decideProblemReview.mockResolvedValue({
      ...firstReview,
      decision: 'APPROVED',
      version: firstReview.version + 1,
    });

    const wrapper = mount(TeacherReviewPage);
    await flushPromises();
    await wrapper.get('[data-testid="teacher-review-refresh"]').trigger('click');
    await wrapper.get('[data-testid="teacher-review-decision-reason"]').setValue('复核通过');
    await wrapper.get('[data-testid="teacher-review-approve"]').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('已通过');

    resolveRefresh(page([firstReview], 0, 1));
    await flushPromises();

    expect(wrapper.text()).toContain('已通过');
  });

  it('retries the page that failed to load while keeping the current page visible', async () => {
    const secondReview = {
      ...firstReview,
      id: 101,
      sourceProblemId: 'review-101',
      problem: { ...firstReview.problem, problemId: 'review-101' },
    };
    searchProblemReviews
      .mockResolvedValueOnce(page([firstReview], 0, 101))
      .mockRejectedValueOnce(new Error('page load failed'))
      .mockResolvedValueOnce(page([secondReview], 1, 101));

    const wrapper = mount(TeacherReviewPage);
    await flushPromises();
    await wrapper.get('[data-testid="teacher-review-next-page"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('[role="alert"]').text()).toContain('page load failed');
    expect(wrapper.text()).toContain('review-1');

    await wrapper.get('[data-testid="teacher-review-retry"]').trigger('click');
    await flushPromises();

    expect(searchProblemReviews).toHaveBeenLastCalledWith({
      page: 1,
      size: 100,
      sortBy: 'REQUESTED_AT',
      sortDirection: 'DESC',
    });
    expect(wrapper.find('[data-testid="teacher-review-select-101"]').exists()).toBe(true);
  });
});
