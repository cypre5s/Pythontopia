import { mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';

vi.mock('@/api/upload', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/upload')>();
  return { ...actual, uploadFile: vi.fn() };
});

import { uploadFile } from '@/api/upload';
import ChatComposer from '@/components/workshop/ChatComposer.vue';
import type { ComposerSubmitPayload } from '@/components/workshop/ChatComposer.vue';

describe('ChatComposer image attachments', () => {
  it('submits image data inline without sending it to document upload', async () => {
    const wrapper = mount(ChatComposer, { props: { mentionItems: [] } });
    const input = wrapper.get<HTMLInputElement>('[data-testid="composer-file-input"]');
    const file = new File([new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10])], 'code.png', {
      type: 'image/png',
    });
    Object.defineProperty(input.element, 'files', { configurable: true, value: [file] });

    await input.trigger('change');
    await vi.waitFor(() => {
      expect(wrapper.get('[data-testid="attachment-pill"]').attributes('data-status')).toBe('ready');
    });
    await wrapper.get('[data-testid="composer-input"]').setValue('请解释图片里的代码');
    expect(wrapper.get('[data-testid="composer-submit"]').attributes('disabled')).toBeUndefined();
    await wrapper.get('[data-testid="composer-submit"]').trigger('click');

    const payload = wrapper.emitted('submit')?.[0]?.[0] as ComposerSubmitPayload;
    expect(payload.images).toEqual(['data:image/png;base64,iVBORw0KGgo=']);
    expect(payload.attachments).toEqual([]);
    expect(uploadFile).not.toHaveBeenCalled();
  });
});
