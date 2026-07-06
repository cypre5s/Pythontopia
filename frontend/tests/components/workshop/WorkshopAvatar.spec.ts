import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import WorkshopAvatar from '@/components/workshop/WorkshopAvatar.vue';

describe('WorkshopAvatar', () => {
  it('渲染百晓像素头像（真实已审计资产路径）+ 名牌「百晓」', () => {
    const wrapper = mount(WorkshopAvatar);
    const img = wrapper.get('[data-testid="workshop-avatar-img"]');
    expect(img.attributes('src')).toBe(
      '/assets/pixel/portraits/heads/portrait_workshop_master_head.png',
    );
    expect(img.attributes('alt')).toBe('百晓');
    expect(wrapper.text()).toContain('百晓');
  });

  it('默认无 streaming 指示点；streaming=true 时亮 ● 并打 streaming class', () => {
    const idle = mount(WorkshopAvatar);
    expect(idle.find('[data-testid="workshop-avatar-dot"]').exists()).toBe(false);
    expect(idle.classes()).not.toContain('avatar--streaming');

    const busy = mount(WorkshopAvatar, { props: { streaming: true } });
    expect(busy.find('[data-testid="workshop-avatar-dot"]').exists()).toBe(true);
    expect(busy.get('[data-testid="workshop-avatar-dot"]').text()).toBe('●');
    expect(busy.classes()).toContain('avatar--streaming');
  });

  it('头像加载失败 @error → 回退「百」字方块（同尺寸像素边框，不白屏）', async () => {
    const wrapper = mount(WorkshopAvatar);
    await wrapper.get('[data-testid="workshop-avatar-img"]').trigger('error');

    expect(wrapper.find('[data-testid="workshop-avatar-img"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="workshop-avatar-fallback"]').text()).toBe('百');
  });

  it('streaming 下头像缺图 → 「百」方块与 ● 指示点并存', async () => {
    const wrapper = mount(WorkshopAvatar, { props: { streaming: true } });
    await wrapper.get('[data-testid="workshop-avatar-img"]').trigger('error');

    expect(wrapper.find('[data-testid="workshop-avatar-fallback"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="workshop-avatar-dot"]').exists()).toBe(true);
  });
});
