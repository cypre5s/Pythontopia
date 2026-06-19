import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import LoginForm from '@/components/auth/LoginForm.vue';

describe('LoginForm', () => {
  it('validates short credentials before submit', async () => {
    const wrapper = mount(LoginForm);

    await wrapper.find('form').trigger('submit.prevent');

    expect(wrapper.text()).toContain('用户名至少 3 个字符');
    expect(wrapper.emitted('submit')).toBeUndefined();
  });

  it('emits typed login request', async () => {
    const wrapper = mount(LoginForm);
    const inputs = wrapper.findAll('input');
    await inputs[0]?.setValue('stu01');
    await inputs[1]?.setValue('P@ssw0rd1');

    await wrapper.find('form').trigger('submit.prevent');

    expect(wrapper.emitted('submit')?.[0]?.[0]).toEqual({
      username: 'stu01',
      password: 'P@ssw0rd1',
    });
  });

  it('links the validation error to the offending field for screen readers', async () => {
    const wrapper = mount(LoginForm);

    await wrapper.find('form').trigger('submit.prevent');

    const username = wrapper.findAll('input')[0];
    expect(username?.attributes('aria-invalid')).toBe('true');
    expect(username?.attributes('aria-describedby')).toBe('login-error');
    expect(wrapper.find('#login-error').exists()).toBe(true);
  });
});
