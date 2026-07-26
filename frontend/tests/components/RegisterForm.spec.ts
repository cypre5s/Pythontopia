// RegisterForm 重复提交防护（与 LoginForm 同款 :disabled 契约）。
// 缺陷：「创建账号」按钮此前无任何禁用，双击 = 两次 register + 两次 login。
import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import RegisterForm from '@/components/auth/RegisterForm.vue';

async function fillValid(wrapper: ReturnType<typeof mount>): Promise<void> {
  const inputs = wrapper.findAll('input');
  await inputs[0]!.setValue('newbie');      // 用户名
  await inputs[1]!.setValue('小新');         // 显示名称
  await inputs[2]!.setValue('password123'); // 密码
}

describe('RegisterForm 提交防护', () => {
  it('连点「创建账号」只 emit 一次 submit', async () => {
    const wrapper = mount(RegisterForm);
    await fillValid(wrapper);

    const form = wrapper.get('form');
    await form.trigger('submit');
    await form.trigger('submit');

    expect(wrapper.emitted('submit')).toHaveLength(1);
    wrapper.unmount();
  });

  it('提交后按钮进入禁用态并显示「创建中...」', async () => {
    const wrapper = mount(RegisterForm);
    await fillValid(wrapper);

    const button = wrapper.get('[data-testid="register-submit"]');
    expect(button.attributes('disabled')).toBeUndefined();

    await wrapper.get('form').trigger('submit');

    expect(button.attributes('disabled')).toBeDefined();
    expect(button.text()).toContain('创建中');
    wrapper.unmount();
  });

  it('父页传 loading=true 时按钮禁用且不再 emit（与 LoginForm 契约一致）', async () => {
    const wrapper = mount(RegisterForm, { props: { loading: true } });
    await fillValid(wrapper);

    expect(wrapper.get('[data-testid="register-submit"]').attributes('disabled')).toBeDefined();
    await wrapper.get('form').trigger('submit');
    expect(wrapper.emitted('submit')).toBeUndefined();
    wrapper.unmount();
  });

  it('校验未通过时不锁定按钮（学生可立即改完再交）', async () => {
    const wrapper = mount(RegisterForm);
    await wrapper.get('form').trigger('submit');

    expect(wrapper.emitted('submit')).toBeUndefined();
    expect(wrapper.get('[data-testid="register-submit"]').attributes('disabled')).toBeUndefined();
    expect(wrapper.text()).toContain('用户名至少 3 个字符');
    wrapper.unmount();
  });
});
