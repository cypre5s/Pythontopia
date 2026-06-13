import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import AttachmentPills, { type AttachmentDraft } from '../AttachmentPills.vue';

function draft(patch: Partial<AttachmentDraft> = {}): AttachmentDraft {
  return {
    localId: 'l1',
    filename: 'main.py',
    ext: '.py',
    byteSize: 2048,
    status: 'uploading',
    ...patch,
  };
}

function mountPills(attachments: AttachmentDraft[]) {
  return mount(AttachmentPills, { props: { attachments } });
}

describe('AttachmentPills · pill 状态机渲染', () => {
  it('uploading：spinner + 解析中…，data-status=uploading', () => {
    const w = mountPills([draft({ status: 'uploading' })]);
    expect(w.find('[data-testid="pill-spinner"]').exists()).toBe(true);
    expect(w.find('[data-testid="attachment-pill"]').attributes('data-status')).toBe('uploading');
    expect(w.text()).toContain('解析中');
    expect(w.text()).toContain('🐍');
    expect(w.text()).toContain('main.py');
  });

  it('ready：显示行数·字数，无 spinner', () => {
    const w = mountPills([
      draft({ status: 'ready', uploadId: 7, lineCount: 12, charCount: 345 }),
    ]);
    expect(w.find('[data-testid="pill-spinner"]').exists()).toBe(false);
    expect(w.find('[data-testid="attachment-pill"]').attributes('data-status')).toBe('ready');
    expect(w.text()).toContain('12 行');
    expect(w.text()).toContain('345 字');
  });

  it('error：错误信息 + 重试按钮', () => {
    const w = mountPills([draft({ status: 'error', error: '解析失败：坏文件' })]);
    expect(w.find('[data-testid="pill-error"]').text()).toContain('坏文件');
    expect(w.find('[data-testid="pill-retry"]').exists()).toBe(true);
  });

  it('remove / retry 事件携带 localId', async () => {
    const w = mountPills([draft({ localId: 'abc', status: 'error', error: 'x' })]);
    await w.find('[data-testid="pill-retry"]').trigger('click');
    await w.find('[data-testid="pill-remove"]').trigger('click');
    expect(w.emitted('retry')?.[0]).toEqual(['abc']);
    expect(w.emitted('remove')?.[0]).toEqual(['abc']);
  });

  it('多文件渲染多枚 pill；空数组不渲染容器', () => {
    const w = mountPills([
      draft({ localId: 'a' }),
      draft({ localId: 'b', status: 'ready', uploadId: 1, lineCount: 1, charCount: 1 }),
    ]);
    expect(w.findAll('[data-testid="attachment-pill"]').length).toBe(2);

    const empty = mountPills([]);
    expect(empty.find('[data-testid="attachment-pills"]').exists()).toBe(false);
  });
});
