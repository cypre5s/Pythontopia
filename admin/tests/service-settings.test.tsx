import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import userEvent from '@testing-library/user-event';
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
  await screen.findByText('请求预算');
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
  fireEvent.click(screen.getByRole('button', { name: '保存设置' }));
  await screen.findByText('版本冲突');
  expect(key).toHaveValue('fixture-key');
  fireEvent.click(screen.getByRole('button', { name: '保存设置' }));
  await waitFor(() => expect(key).toHaveValue(''));
  const section = key.closest('section')!;
  expect(within(section).getByRole('status')).toHaveTextContent('设置已保存');
  expect(api.post).not.toHaveBeenCalled();
});

it('uses one identical page-header action group and saves only the active tab', async () => {
  vi.mocked(api.put).mockResolvedValue({ ...config, revision: 2 });
  const { container } = render(<CourseSettings classroomId={1} isAdmin />);
  await screen.findByText('请求预算');
  for (const name of ['本班答疑', '学生助教', '讲稿生成', '语音合成']) {
    fireEvent.click(screen.getByRole('tab', { name }));
    const save = screen.getByRole('button', { name: '保存设置' });
    expect(save.closest('.page-heading')).not.toBeNull();
    expect(screen.getAllByRole('button', { name: '取消编辑' })).toHaveLength(1);
    expect(container.querySelector('.page-heading svg')).not.toBeNull();
  }
  fireEvent.click(screen.getByRole('tab', { name: '讲稿生成' }));
  fireEvent.change(within(screen.getByRole('tabpanel')).getByLabelText('模型名称'), { target: { value: 'draft-writer' } });
  fireEvent.click(screen.getByRole('button', { name: '保存设置' }));
  await waitFor(() => expect(api.put).toHaveBeenCalledTimes(1));
  expect(vi.mocked(api.put).mock.calls[0][0]).toBe('/teacher/services/authoring');
});

it.each([['https://example.invalid', 'https://example.invalid'], ['https://api.openai.com/v1/', 'https://api.deepseek.com']])('fills protocol defaults from %s without changing credentials', async (baseUrl, expectedUrl) => {
  vi.mocked(api.get).mockResolvedValue({ ...config, base_url: baseUrl, protocol: 'openai', voice: 'custom-voice', format: 'mp3', speed: 1.5 });
  render(<ServiceSettings service="speech" />);
  const key = await screen.findByLabelText('API Key （已配置）');
  fireEvent.change(key, { target: { value: 'unsaved-key' } });
  const user = userEvent.setup();
  await user.click(screen.getByLabelText('语音接口协议'));
  await user.click(await screen.findByRole('option', { name: 'DeepSeek 工具语音接口' }));
  expect(screen.queryByLabelText('模型名称')).toBeNull();
  expect(screen.queryByLabelText('合成语速')).toBeNull();
  expect(screen.getByLabelText('API 地址')).toHaveValue(expectedUrl);
  expect(screen.getByLabelText('音色名称 / ID')).toHaveTextContent('Stella');
  expect(key).toHaveValue('unsaved-key');
  expect(api.put).not.toHaveBeenCalled();
  expect(api.post).not.toHaveBeenCalled();
});
