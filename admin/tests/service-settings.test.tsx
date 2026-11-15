import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import { CourseSettings } from '../src/pages/course-settings';
import { ServiceSettings } from '../src/components/service-settings';
import { api } from '../src/lib/api';

vi.mock('../src/lib/drafts', () => ({ useDraft: vi.fn() }));
vi.mock('../src/lib/api', () => ({ api: { get: vi.fn(), put: vi.fn(), post: vi.fn() }, errorMessage: (e: Error) => e.message }));
const config = { revision: 0, base_url: 'https://example.invalid', model: 'writer', vision_model: 'vision', effort: 'none', max_tokens: 8000, timeout: 120, daily_pages: 300, key_configured: true, source: 'environment' };
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(api.get).mockImplementation(async path => path.includes('/course/settings') ? { revision: 1, assistant: { enabled: true }, roles: [] } : config);
});
it('retains service drafts across icon tabs and hides global configuration from teachers', async () => {
  const w = render(<CourseSettings classroomId={1} isAdmin />);
  await screen.findByText('本班设置');
  const tab = screen.getByRole('tab', { name: '讲稿生成' });
  expect(tab.querySelector('svg')).not.toBeNull();
  fireEvent.click(tab);
  const input = await screen.findByLabelText('页面图像模型');
  fireEvent.change(input, { target: { value: 'unsaved-vision' } });
  fireEvent.click(screen.getByRole('tab', { name: '本班答疑' }));
  fireEvent.click(tab);
  expect(screen.getByLabelText('页面图像模型')).toHaveValue('unsaved-vision');
  expect(api.put).not.toHaveBeenCalled(); expect(api.post).not.toHaveBeenCalled();
  w.unmount();
  render(<CourseSettings classroomId={1} />);
  expect(screen.queryByRole('tab', { name: '讲稿生成' })).toBeNull();
});
it('preserves keys after a conflict and clears transient key text only after save', async () => {
  vi.mocked(api.put).mockRejectedValueOnce(new Error('版本冲突')).mockResolvedValueOnce({ ...config, revision: 1 });
  render(<ServiceSettings service="authoring" />);
  const key = await screen.findByLabelText('API Key （已配置）');
  expect(key).toHaveValue('');
  fireEvent.change(key, { target: { value: 'fixture-key' } });
  fireEvent.click(screen.getByRole('button', { name: '保存服务设置' }));
  await screen.findByText('版本冲突');
  expect(key).toHaveValue('fixture-key');
  fireEvent.click(screen.getByRole('button', { name: '保存服务设置' }));
  await waitFor(() => expect(key).toHaveValue(''));
  const section = key.closest('section')!;
  expect(within(section).getByRole('status')).toHaveTextContent('讲稿生成设置已保存');
  expect(api.post).not.toHaveBeenCalled();
});
