import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import AdminClassroomsPage from '../AdminClassroomsPage.vue';

const { listMyClassrooms, listClassroomMembers, removeClassroomMember, updateClassroom, fetchKcHeatmap, fetchQuestSummary, fetchResourceUsage } = vi.hoisted(() => ({
  listMyClassrooms: vi.fn(), listClassroomMembers: vi.fn(), removeClassroomMember: vi.fn(), updateClassroom: vi.fn(),
  fetchKcHeatmap: vi.fn(), fetchQuestSummary: vi.fn(), fetchResourceUsage: vi.fn(),
}));

vi.mock('@/api/classroom', () => ({ listMyClassrooms, listClassroomMembers, removeClassroomMember, updateClassroom }));
vi.mock('@/api/teacher', () => ({ fetchKcHeatmap, fetchQuestSummary, fetchResourceUsage }));

beforeEach(() => {
  vi.clearAllMocks();
  listMyClassrooms.mockResolvedValue([{ classroomId: 9, name: 'Python 1 班', description: '秋季班', teacherId: 3, packId: 43, status: 'ACTIVE', joinCode: 'ABCD23' }]);
  listClassroomMembers.mockResolvedValue([{ studentId: 7, username: 'student7', displayName: '小七', status: 'ACTIVE' }]);
  fetchKcHeatmap.mockResolvedValue([{ kcCode: 'PY43-KC-031', kcName: 'for 循环与遍历', chapterId: 1, chapterOrder: 1, avgMastery: 0.6, studentCount: 1, masteredCount: 0, learningCount: 1, strugglingCount: 0 }]);
  fetchQuestSummary.mockResolvedValue([]);
  fetchResourceUsage.mockResolvedValue({ periodStart: '2026-10-01', periodEnd: '2026-10-21', totals: [], daily: [] });
  removeClassroomMember.mockResolvedValue(undefined);
  updateClassroom.mockImplementation(async (id: number, request: { name: string; description: string }) => ({
    classroomId: id, ...request, teacherId: 3, packId: 43, status: 'ACTIVE', joinCode: 'ABCD23',
  }));
  vi.stubGlobal('confirm', vi.fn(() => true));
});

describe('AdminClassroomsPage', () => {
  it('loads actual classes, students and class learning evidence without exposing KC codes', async () => {
    const wrapper = mount(AdminClassroomsPage);
    await flushPromises();

    expect(wrapper.text()).toContain('Python 1 班');
    expect(wrapper.text()).toContain('小七');
    expect(wrapper.text()).toContain('for 循环与遍历');
    expect(wrapper.text()).not.toContain('PY43-KC-031');
    expect(fetchKcHeatmap).toHaveBeenCalledWith(9);
  });

  it('removes a member through the existing classroom API and refreshes membership', async () => {
    const wrapper = mount(AdminClassroomsPage);
    await flushPromises();
    await wrapper.get('[data-testid="admin-remove-class-member-7"]').trigger('click');
    await flushPromises();
    expect(removeClassroomMember).toHaveBeenCalledWith(9, 7);
    expect(listClassroomMembers).toHaveBeenCalledTimes(2);
  });

  it('lets an administrator update class name and description through the existing API', async () => {
    const wrapper = mount(AdminClassroomsPage);
    await flushPromises();
    await wrapper.get('[data-testid="admin-class-edit"]').trigger('click');
    await wrapper.get('[data-testid="admin-class-name"]').setValue('Python 秋季班');
    await wrapper.get('[data-testid="admin-class-description"]').setValue('晚间课程');
    await wrapper.get('.admin-classrooms__edit').trigger('submit');
    await flushPromises();

    expect(updateClassroom).toHaveBeenCalledWith(9, { name: 'Python 秋季班', description: '晚间课程' });
    expect(wrapper.text()).toContain('Python 秋季班');
  });
});
