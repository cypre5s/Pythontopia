import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import AdminCourseResourcesPage from '../AdminCourseResourcesPage.vue';

const { searchAdminCourseResources, setAdminCourseResourceEnabled, getCoursePack } = vi.hoisted(() => ({
  searchAdminCourseResources: vi.fn(),
  setAdminCourseResourceEnabled: vi.fn(),
  getCoursePack: vi.fn(),
}));

vi.mock('@/api/adminCourseResources', () => ({ searchAdminCourseResources, setAdminCourseResourceEnabled }));
vi.mock('@/api/course', () => ({ getCoursePack }));

beforeEach(() => {
  vi.clearAllMocks();
  searchAdminCourseResources.mockResolvedValue({
    content: [{ resourceType: 'EXAMPLE', resourceId: 12, title: '列表遍历示例', category: '列表', status: 'COURSE_SEED', enabled: true, preview: '示例摘要', pageCount: null }],
    page: 0, size: 50, totalElements: 1, totalPages: 1,
  });
  setAdminCourseResourceEnabled.mockResolvedValue(undefined);
  getCoursePack.mockResolvedValue({ id: 43, name: 'Python语言基础', documentCount: 7, pageCount: 561, chapterCount: 7, kcCount: 65, exampleCount: 46, problemCount: 39 });
});

describe('AdminCourseResourcesPage', () => {
  it('shows stored resource availability and applies a reversible status change', async () => {
    const wrapper = mount(AdminCourseResourcesPage);
    await flushPromises();

    expect(wrapper.text()).toContain('可用');
    await wrapper.get('[data-testid="admin-course-resource-toggle-EXAMPLE-12"]').trigger('click');
    await flushPromises();

    expect(setAdminCourseResourceEnabled).toHaveBeenCalledWith('EXAMPLE', 12, false);
    expect(wrapper.text()).toContain('已停用');
  });
});
