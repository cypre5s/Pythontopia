import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import { Synthesis } from '../src/pages/synthesis';
import { api } from '../src/lib/api';

vi.mock('../src/lib/drafts', () => ({ useDraft: vi.fn() }));
vi.mock('../src/lib/api', () => ({ api: { get: vi.fn(), post: vi.fn() }, errorMessage: (e: Error) => e.message }));
vi.mock('../src/components/narration-review', () => ({ NarrationReview: () => <div>审校对话框</div> }));
vi.mock('../src/components/narration-player', () => ({ NarrationPlayer: () => <div>试听对话框</div> }));
const task = { material_id: 11, title: '变量讲义', material_kind: 'pdf', kind: 'generate', status: 'failed',
  done: 2, total: 2, message: '有一页失败', started_at: '2026-11-15T00:00:00', failed: [{ page_no: 2, error: '服务繁忙' }], retryable: true, retry_note: '' };
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(api.get).mockResolvedValue({ items: [task], total: 1, counts: { running: 0, failed: 1, interrupted: 0 } });
});
it('loads class tasks without invoking services and requires confirmation before retry', async () => {
  vi.mocked(api.post).mockResolvedValue({ ...task, status: 'done' });
  render(<Synthesis classroomId={7} />);
  await screen.findByText('变量讲义');
  expect(vi.mocked(api.get).mock.calls[0][0]).toContain('/classrooms/7/synthesis?');
  expect(api.post).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: '重试' }));
  expect(api.post).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: '确认重试' }));
  await screen.findByText('重试已完成');
  expect(api.post).toHaveBeenCalledWith('/teacher/classrooms/7/synthesis/11/retry', { expected_started_at: task.started_at });
  fireEvent.click(screen.getByRole('button', { name: '审校' }));
  expect(screen.getByText('审校对话框')).toBeInTheDocument();
});
it('keeps refresh failures visible and aborts reads when leaving', async () => {
  const view = render(<Synthesis classroomId={7} />);
  await screen.findByText('变量讲义');
  vi.mocked(api.get).mockRejectedValue(new Error('连接中断'));
  fireEvent.click(screen.getByRole('button', { name: '刷新' }));
  await screen.findByRole('alert');
  expect(screen.getByText('变量讲义')).toBeInTheDocument();
  await waitFor(() => expect(api.get).toHaveBeenCalledTimes(2));
  const signal = vi.mocked(api.get).mock.calls[1][1];
  view.unmount();
  expect(signal?.aborted).toBe(true);
});
