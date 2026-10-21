import { flushPromises, mount } from '@vue/test-utils';
import { createMemoryHistory, createRouter } from 'vue-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import AdminStudentDetailPage from '@/pages/admin/AdminStudentDetailPage.vue';
import { fetchAdminStudentDetail } from '@/api/adminUsers';

vi.mock('@/api/adminUsers', () => ({ fetchAdminStudentDetail: vi.fn() }));

describe('AdminStudentDetailPage', () => {
  beforeEach(() => {
    vi.mocked(fetchAdminStudentDetail).mockResolvedValue({
      account: {
        id: 7,
        username: 'student7',
        displayName: '学生七号',
        role: 'STUDENT',
        status: 'ACTIVE',
        createdAt: '2026-10-01T00:00:00Z',
      },
      profileDimensions: {
        learning_goal: { score: 0.8, evidence_count: 3, tags: [], source: 'events' },
      },
      mastery: [{ kcName: 'for 循环与遍历', chapterOrder: 4, masteryScore: 0.75, confidence: 0.8, updatedAt: null }],
      activity: [{ id: 3, eventType: 'submission_passed', source: 'backend', occurredAt: '2026-10-20T00:00:00Z' }],
      submissions: [{ id: 9, problemTitle: '循环练习', status: 'PASSED', judgeVerdict: 'AC', passedCases: 4, totalCases: 4, submittedAt: '2026-10-20T00:00:00Z' }],
      classrooms: [{ classroomId: 5, classroomName: 'Python 1 班', teacherName: '王老师', status: 'ACTIVE', joinedAt: '2026-10-02T00:00:00Z' }],
    });
  });

  it('shows the real student profile, Chinese mastery names, Judge results and class membership', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/admin/users', name: 'admin-users', component: { template: '<div />' } },
        { path: '/admin/users/:userId', name: 'admin-student-detail', component: AdminStudentDetailPage },
      ],
    });
    await router.push('/admin/users/7');
    await router.isReady();

    const wrapper = mount(AdminStudentDetailPage, { global: { plugins: [router] } });
    await flushPromises();

    expect(fetchAdminStudentDetail).toHaveBeenCalledWith(7);
    expect(wrapper.text()).toContain('学生七号');
    expect(wrapper.text()).toContain('学习目标');
    expect(wrapper.text()).toContain('for 循环与遍历');
    expect(wrapper.text()).toContain('通过');
    expect(wrapper.text()).toContain('Python 1 班');
    expect(wrapper.text()).not.toContain('PY43-KC-031');
  });
});
