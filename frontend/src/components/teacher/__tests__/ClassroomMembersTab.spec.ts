import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import type { ClassroomMemberDto } from '@/api/dto/ClassroomMemberDto';
import ClassroomMembersTab from '../ClassroomMembersTab.vue';

describe('ClassroomMembersTab', () => {
  it('labels user IDs accurately and hides the unreachable removed status', () => {
    const member: ClassroomMemberDto = {
      studentId: 42,
      username: 'student42',
      displayName: '小明',
      status: 'ACTIVE',
    };

    const wrapper = mount(ClassroomMembersTab, { props: { members: [member] } });
    const headers = wrapper.findAll('th').map((header) => header.text());

    expect(headers).toEqual(['用户编号', '用户名', '显示名', '操作']);
    expect(wrapper.text()).not.toContain('已移除');
  });
});
