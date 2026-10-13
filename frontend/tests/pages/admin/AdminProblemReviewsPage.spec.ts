import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import type { BossProblemReviewDto } from '@/api/problemReview';
import AdminProblemReviewsPage from '@/pages/admin/AdminProblemReviewsPage.vue';

const api = vi.hoisted(() => ({
  listProblemReviews: vi.fn(),
  searchProblemReviews: vi.fn(),
  decideProblemReview: vi.fn(),
}));
const toast = vi.hoisted(() => ({ show: vi.fn() }));

vi.mock('@/api/problemReview', () => api);
vi.mock('@stores/toast', () => ({ useToastStore: () => toast }));

function review(overrides: Partial<BossProblemReviewDto> = {}): BossProblemReviewDto {
  return {
    id: 41,
    packId: 43,
    sourceProblemId: 'source-1',
    kind: 'debug',
    problem: {
      problemId: 'source-1',
      chapter: 1,
      stage: 1,
      difficulty: 2,
      kcCode: 'PY43-KC-001',
      statement: '读入两个整数 a b，输出 a+b。',
      hints: ['先读取两个整数。'],
      sampleInput: '2 3',
      sampleOutput: '5',
      totalTestCases: 3,
      reviewed: true,
      aiGenerated: true,
    },
    decision: 'PENDING',
    requesterId: 2,
    requesterUsername: 'teacher-1',
    requestedAt: '2026-08-02T08:00:00Z',
    deciderId: null,
    deciderUsername: null,
    decidedAt: null,
    decisionReason: null,
    version: 7,
    ...overrides,
  };
}

function page(content: BossProblemReviewDto[], totalElements = content.length, currentPage = 0) {
  const size = 20;
  return {
    content,
    page: currentPage,
    size,
    totalElements,
    totalPages: Math.ceil(totalElements / size),
  };
}

describe('AdminProblemReviewsPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
    if (!HTMLDialogElement.prototype.showModal) {
      Object.defineProperty(HTMLDialogElement.prototype, 'showModal', {
        configurable: true,
        value(this: HTMLDialogElement) { this.setAttribute('open', ''); },
      });
      Object.defineProperty(HTMLDialogElement.prototype, 'close', {
        configurable: true,
        value(this: HTMLDialogElement) { this.removeAttribute('open'); },
      });
    }
    api.searchProblemReviews.mockResolvedValue(page([review()]));
    api.decideProblemReview.mockImplementation(async (_id: number, request: { decision: string; reason: string }) =>
      review({
        decision: request.decision as 'APPROVED' | 'REJECTED',
        deciderId: 1,
        deciderUsername: 'admin-1',
        decidedAt: '2026-08-02T08:01:00Z',
        decisionReason: request.reason,
        version: 8,
      }));
  });

  it('loads real queue records and shows only public problem details', async () => {
    const wrapper = mount(AdminProblemReviewsPage);
    await flushPromises();

    expect(api.searchProblemReviews).toHaveBeenCalledWith({
      page: 0,
      size: 20,
      sortBy: "REQUESTED_AT",
      sortDirection: "DESC",
    });
    expect(wrapper.findAll('.review-table tbody tr')).toHaveLength(1);
    expect(wrapper.text()).toContain('读入两个整数 a b，输出 a+b。');
    expect(wrapper.text()).toContain('公开输入样例');
    expect(wrapper.text()).toContain('公开输出样例');
    expect(wrapper.text()).toContain('teacher-1');
  });

  it("searches and filters through the backend page endpoint", async () => {
    api.searchProblemReviews.mockResolvedValue(page([review()], 25));
    const wrapper = mount(AdminProblemReviewsPage);
    await flushPromises();

    await wrapper.get('[data-testid="admin-review-search"]').setValue(" loop ");
    await wrapper.get('[data-testid="admin-review-search-form"]').trigger("submit");
    await flushPromises();
    expect(api.searchProblemReviews).toHaveBeenLastCalledWith({
      page: 0,
      size: 20,
      sortBy: "REQUESTED_AT",
      sortDirection: "DESC",
      q: "loop",
    });

    await wrapper.get('[aria-label="按审核状态筛选"]').setValue("PENDING");
    await flushPromises();
    expect(api.searchProblemReviews).toHaveBeenLastCalledWith({
      page: 0,
      size: 20,
      sortBy: "REQUESTED_AT",
      sortDirection: "DESC",
      decision: "PENDING",
      q: "loop",
    });

    api.searchProblemReviews.mockResolvedValue(page([review({ id: 42 })], 25, 1));
    await wrapper.get('[data-testid="admin-pagination-next-page"]').trigger("click");
    await flushPromises();
    expect(api.searchProblemReviews).toHaveBeenLastCalledWith({
      page: 1,
      size: 20,
      sortBy: "REQUESTED_AT",
      sortDirection: "DESC",
      decision: "PENDING",
      q: "loop",
    });
  });

  it("sorts by a selectable column and direction and resets pagination", async () => {
    api.searchProblemReviews.mockResolvedValue(page([review()], 45));
    const wrapper = mount(AdminProblemReviewsPage);
    await flushPromises();

    expect(api.searchProblemReviews).toHaveBeenLastCalledWith({
      page: 0,
      size: 20,
      sortBy: "REQUESTED_AT",
      sortDirection: "DESC",
    });

    await wrapper.get('[data-testid="admin-pagination-next-page"]').trigger("click");
    await flushPromises();
    expect(api.searchProblemReviews).toHaveBeenLastCalledWith({
      page: 1,
      size: 20,
      sortBy: "REQUESTED_AT",
      sortDirection: "DESC",
    });

    await wrapper.get('[aria-label="审核队列排序字段"]').setValue("STATEMENT");
    await flushPromises();
    expect(api.searchProblemReviews).toHaveBeenLastCalledWith({
      page: 0,
      size: 20,
      sortBy: "STATEMENT",
      sortDirection: "DESC",
    });

    await wrapper.get('[aria-label="审核队列排序方向"]').setValue("ASC");
    await flushPromises();
    expect(api.searchProblemReviews).toHaveBeenLastCalledWith({
      page: 0,
      size: 20,
      sortBy: "STATEMENT",
      sortDirection: "ASC",
    });
  });

  it('supports direct page jumps and selectable page sizes', async () => {
    api.searchProblemReviews.mockResolvedValue(page([review()], 45));
    const wrapper = mount(AdminProblemReviewsPage);
    await flushPromises();

    await wrapper.get('[data-testid="admin-pagination-page-input"]').setValue('3');
    await wrapper.get('[data-testid="admin-pagination-jump-form"]').trigger('submit');
    await flushPromises();
    expect(api.searchProblemReviews).toHaveBeenLastCalledWith({
      page: 2, size: 20, sortBy: 'REQUESTED_AT', sortDirection: 'DESC',
    });

    await wrapper.get('[data-testid="admin-pagination-size"]').setValue('50');
    await flushPromises();
    expect(api.searchProblemReviews).toHaveBeenLastCalledWith({
      page: 0, size: 50, sortBy: 'REQUESTED_AT', sortDirection: 'DESC',
    });
  });

  it("shows a sorting request error and retries with the selected order", async () => {
    api.searchProblemReviews
      .mockResolvedValueOnce(page([review()], 45))
      .mockRejectedValueOnce(new Error("排序请求失败"))
      .mockResolvedValueOnce(page([review()], 45));
    const wrapper = mount(AdminProblemReviewsPage);
    await flushPromises();

    await wrapper.get('[aria-label="审核队列排序字段"]').setValue("REQUESTER_USERNAME");
    await flushPromises();
    expect(wrapper.get('[role="alert"]').text()).toContain("排序请求失败");
    expect(wrapper.find('.review-table tbody tr').exists()).toBe(false);
    expect(wrapper.find('.review-detail').exists()).toBe(false);
    expect(wrapper.find('.review-decision--approve').exists()).toBe(false);

    await wrapper.get(".review-error .admin-button").trigger("click");
    await flushPromises();
    expect(api.searchProblemReviews).toHaveBeenLastCalledWith({
      page: 0,
      size: 20,
      sortBy: "REQUESTER_USERNAME",
      sortDirection: "DESC",
    });
  });

  it('requires a reason and submits the exact optimistic version before updating the row', async () => {
    const wrapper = mount(AdminProblemReviewsPage, { attachTo: document.body });
    await flushPromises();

    api.searchProblemReviews.mockResolvedValue(page([review({
      decision: 'APPROVED',
      deciderId: 1,
      deciderUsername: 'admin-1',
      decidedAt: '2026-08-02T08:01:00Z',
      decisionReason: '题面与公开样例已复核',
      version: 8,
    })]));
    await wrapper.get('.review-decision--approve').trigger('click');
    await wrapper.get('[data-testid="admin-review-confirm"]').trigger('click');
    expect(wrapper.get('[role="alert"]').text()).toContain('请填写审核意见');
    expect(api.decideProblemReview).not.toHaveBeenCalled();

    await wrapper.get('[data-testid="admin-review-reason"]').setValue('题面与公开样例已复核');
    await wrapper.get('[data-testid="admin-review-confirm"]').trigger('click');
    await flushPromises();

    expect(api.decideProblemReview).toHaveBeenCalledWith(41, {
      decision: 'APPROVED',
      reason: '题面与公开样例已复核',
      version: 7,
    });
    expect(wrapper.text()).toContain('已通过');
    expect(wrapper.text()).toContain('admin-1');
    expect(toast.show).toHaveBeenCalledWith('题目审核已通过。', 'success');
    expect(document.activeElement).toBe(wrapper.get('[data-testid="admin-problem-reviews"]').element);
    wrapper.unmount();
  });

  it('reports an empty backend queue without inventing a row', async () => {
    api.searchProblemReviews.mockResolvedValue(page([]));
    const wrapper = mount(AdminProblemReviewsPage);
    await flushPromises();

    expect(wrapper.text()).toContain('后端审核队列为空');
    expect(wrapper.findAll('.review-table tbody tr')).toHaveLength(0);
  });

  it('closes the native confirmation dialog on Escape and restores focus to its opener', async () => {
    const wrapper = mount(AdminProblemReviewsPage, { attachTo: document.body });
    await flushPromises();
    const opener = wrapper.get('.review-decision--approve').element as HTMLButtonElement;
    const focus = vi.spyOn(opener, 'focus');

    await wrapper.get('.review-decision--approve').trigger('click');
    await flushPromises();
    const dialog = wrapper.get('dialog.review-dialog');
    expect(document.activeElement).toBe(wrapper.get('[data-testid="admin-review-reason"]').element);

    await dialog.trigger('cancel');
    await flushPromises();

    expect(wrapper.find('dialog.review-dialog').exists()).toBe(false);
    expect(focus).toHaveBeenCalledOnce();
    wrapper.unmount();
  });
});
