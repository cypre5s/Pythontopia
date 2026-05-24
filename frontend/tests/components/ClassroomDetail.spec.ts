import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';
import ClassroomDetail from '@/pages/teacher/ClassroomDetail.vue';
import { useTeacherStore } from '@stores/teacher';

const apiMocks = vi.hoisted(() => ({
  listMyClassrooms: vi.fn(),
  createClassroom: vi.fn(),
  removeClassroomMember: vi.fn(),
  listClassroomMembersByStatus: vi.fn(),
  listClassroomQuestsForTeacher: vi.fn(),
  publishClassroomQuest: vi.fn(),
  archiveClassroomQuest: vi.fn(),
  fetchKcHeatmap: vi.fn(),
  fetchQuestSummary: vi.fn(),
  fetchErrorHotspots: vi.fn(),
  fetchResourceUsage: vi.fn(),
  fetchRecentActivity: vi.fn(),
  updateClassroom: vi.fn(),
  regenerateClassroomJoinCode: vi.fn(),
}));

vi.mock('@/api/classroom', () => ({
  listMyClassrooms: apiMocks.listMyClassrooms,
  createClassroom: apiMocks.createClassroom,
  removeClassroomMember: apiMocks.removeClassroomMember,
}));
vi.mock('@/api/teacher', () => ({
  listClassroomMembersByStatus: apiMocks.listClassroomMembersByStatus,
  listClassroomQuestsForTeacher: apiMocks.listClassroomQuestsForTeacher,
  publishClassroomQuest: apiMocks.publishClassroomQuest,
  archiveClassroomQuest: apiMocks.archiveClassroomQuest,
  fetchKcHeatmap: apiMocks.fetchKcHeatmap,
  fetchQuestSummary: apiMocks.fetchQuestSummary,
  fetchErrorHotspots: apiMocks.fetchErrorHotspots,
  fetchResourceUsage: apiMocks.fetchResourceUsage,
  fetchRecentActivity: apiMocks.fetchRecentActivity,
  updateClassroom: apiMocks.updateClassroom,
  regenerateClassroomJoinCode: apiMocks.regenerateClassroomJoinCode,
}));

let router: Router;

function setupRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/teacher', name: 'teacher', component: { template: '<div/>' } },
      {
        path: '/teacher/classroom/:id',
        name: 'teacher-classroom',
        component: ClassroomDetail,
      },
    ],
  });
}

describe('ClassroomDetail', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    router = setupRouter();
    apiMocks.listMyClassrooms.mockResolvedValue([
      {
        classroomId: 9,
        name: 'Stage12 Demo',
        description: '演示班级',
        teacherId: 42,
        packId: 43,
        status: 'ACTIVE',
        joinCode: 'ABC123',
      },
    ]);
    apiMocks.listClassroomMembersByStatus.mockResolvedValue([
      { studentId: 11, username: 'stu01', displayName: 'Stu 01', status: 'ACTIVE' },
    ]);
    apiMocks.listClassroomQuestsForTeacher.mockResolvedValue([]);
    apiMocks.fetchKcHeatmap.mockResolvedValue([]);
    apiMocks.fetchQuestSummary.mockResolvedValue([]);
    apiMocks.fetchErrorHotspots.mockResolvedValue([]);
    apiMocks.fetchResourceUsage.mockResolvedValue({
      periodStart: '2026-05-15',
      periodEnd: '2026-05-21',
      totals: [],
      daily: [],
    });
    apiMocks.fetchRecentActivity.mockResolvedValue([]);
    apiMocks.publishClassroomQuest.mockResolvedValue({
      classroomQuest: {},
      totalStudents: 1,
      createdQuests: 1,
      skippedDuplicates: 0,
    });
    apiMocks.regenerateClassroomJoinCode.mockResolvedValue({
      classroomId: 9,
      name: 'Stage12 Demo',
      description: '演示班级',
      teacherId: 42,
      packId: 43,
      status: 'ACTIVE',
      joinCode: 'NEW999',
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders tabs and switches active panel', async () => {
    await router.push('/teacher/classroom/9');
    const wrapper = mount(ClassroomDetail, { global: { plugins: [router] } });
    await flushPromises();

    expect(wrapper.text()).toContain('Stage12 Demo');
    const tabButtons = wrapper.findAll('.tabs button');
    expect(tabButtons.length).toBeGreaterThanOrEqual(7);
    await tabButtons[2]!.trigger('click');
    expect(wrapper.find('[data-testid="quest-tab"]').isVisible()).toBe(true);
  });

  it('regenerates join code via API', async () => {
    await router.push('/teacher/classroom/9');
    const wrapper = mount(ClassroomDetail, { global: { plugins: [router] } });
    await flushPromises();
    const button = wrapper.findAll('button').find((b) => b.text() === '重置加入码');
    expect(button).toBeTruthy();
    await button!.trigger('click');
    await flushPromises();
    const teacher = useTeacherStore();
    expect(apiMocks.regenerateClassroomJoinCode).toHaveBeenCalledWith(9);
    expect(teacher.current.classroom?.joinCode).toBe('NEW999');
  });
});
