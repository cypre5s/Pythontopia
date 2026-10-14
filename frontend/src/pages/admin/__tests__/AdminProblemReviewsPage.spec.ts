import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type {
  BossProblemReviewDto,
  BossProblemReviewPageDto,
} from '@/api/problemReview';
import AdminProblemReviewsPage from '../AdminProblemReviewsPage.vue';

const { searchProblemReviews, decideProblemReview, toastShow } = vi.hoisted(() => ({
  searchProblemReviews: vi.fn(),
  decideProblemReview: vi.fn(),
  toastShow: vi.fn(),
}));

vi.mock('@/api/problemReview', () => ({ searchProblemReviews, decideProblemReview }));
vi.mock('@stores/toast', () => ({ useToastStore: () => ({ show: toastShow }) }));

const pendingReview: BossProblemReviewDto = {
  id: 7,
  packId: 43,
  sourceProblemId: 'ai-7',
  kind: 'CODE',
  problem: {
    problemId: 'ai-7',
    chapter: 1,
    stage: 1,
    difficulty: 2,
    kcCode: 'PY43-KC-001',
    statement: '输出循环结果',
    hints: [],
    sampleInput: '',
    sampleOutput: '3',
    totalTestCases: 2,
    reviewed: true,
    aiGenerated: true,
  },
  decision: 'PENDING',
  requesterId: 12,
  requesterUsername: 'student12',
  requestedAt: '2026-10-14T00:00:00Z',
  deciderId: null,
  deciderUsername: null,
  decidedAt: null,
  decisionReason: null,
  version: 3,
};

function reviewPage(content: BossProblemReviewDto[]): BossProblemReviewPageDto {
  return { content, page: 0, size: 20, totalElements: content.length ? 21 : 0, totalPages: content.length ? 2 : 0 };
}

beforeEach(() => {
  vi.clearAllMocks();
  searchProblemReviews.mockResolvedValue(reviewPage([pendingReview]));
  decideProblemReview.mockResolvedValue({ ...pendingReview, decision: 'APPROVED' });
  Object.defineProperty(HTMLDialogElement.prototype, 'showModal', {
    configurable: true,
    value(this: HTMLDialogElement) { this.setAttribute('open', ''); },
  });
  Object.defineProperty(HTMLDialogElement.prototype, 'close', {
    configurable: true,
    value(this: HTMLDialogElement) { this.removeAttribute('open'); },
  });
});

describe('AdminProblemReviewsPage', () => {
  it('searches and filters through the backend queue API', async () => {
    const wrapper = mount(AdminProblemReviewsPage);
    await flushPromises();

    await wrapper.get('[data-testid="admin-review-search"]').setValue('  循环  ');
    await wrapper.get('[data-testid="admin-review-search-form"]').trigger('submit');
    await flushPromises();
    expect(searchProblemReviews).toHaveBeenLastCalledWith(expect.objectContaining({ q: '循环', page: 0 }));

    await wrapper.get('[aria-label="按审核状态筛选"]').setValue('PENDING');
    await flushPromises();
    expect(searchProblemReviews).toHaveBeenLastCalledWith(
      expect.objectContaining({ decision: 'PENDING', q: '循环', page: 0 }),
    );

    await wrapper.get('[aria-label="审核队列排序字段"]').setValue('STATEMENT');
    await flushPromises();
    await wrapper.get('[aria-label="审核队列排序方向"]').setValue('ASC');
    await flushPromises();
    expect(searchProblemReviews).toHaveBeenLastCalledWith(
      expect.objectContaining({ sortBy: 'STATEMENT', sortDirection: 'ASC', q: '循环' }),
    );

    await wrapper.get('[data-testid="admin-pagination-size"]').setValue('50');
    await flushPromises();
    expect(searchProblemReviews).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 0, size: 50, decision: 'PENDING' }),
    );
    await wrapper.get('[data-testid="admin-pagination-page-2"]').trigger('click');
    await flushPromises();
    expect(searchProblemReviews).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 1, size: 50, sortBy: 'STATEMENT', sortDirection: 'ASC' }),
    );
  });

  it('requires an audit reason and submits the selected decision with its version', async () => {
    const wrapper = mount(AdminProblemReviewsPage);
    await flushPromises();
    await wrapper.get('button.review-decision--approve').trigger('click');
    await flushPromises();
    await wrapper.get('[data-testid="admin-review-confirm"]').trigger('click');

    expect(wrapper.get('[role="alert"]').text()).toContain('请填写审核意见');
    expect(decideProblemReview).not.toHaveBeenCalled();

    await wrapper.get('[data-testid="admin-review-reason"]').setValue('题目与课程知识点一致');
    await wrapper.get('[data-testid="admin-review-confirm"]').trigger('click');
    await flushPromises();

    expect(decideProblemReview).toHaveBeenCalledWith(7, {
      decision: 'APPROVED',
      reason: '题目与课程知识点一致',
      version: 3,
    });
    expect(toastShow).toHaveBeenCalledWith('题目审核已通过。', 'success');
    expect(searchProblemReviews).toHaveBeenCalledTimes(2);
  });

  it('clears stale review actions when a filtered reload fails', async () => {
    const wrapper = mount(AdminProblemReviewsPage);
    await flushPromises();
    searchProblemReviews.mockRejectedValueOnce(new Error('network error'));

    await wrapper.get('[aria-label="按审核状态筛选"]').setValue('REJECTED');
    await flushPromises();

    expect(wrapper.text()).not.toContain('输出循环结果');
    expect(wrapper.get('[role="alert"]').text()).toContain('network error');
    expect(wrapper.find('button.review-decision--approve').exists()).toBe(false);
  });
});
