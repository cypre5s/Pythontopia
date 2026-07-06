import { describe, expect, it, vi } from 'vitest';
import { login, register } from '@/api/auth';
import { http } from '@/api/http';

vi.mock('@/api/http', () => ({
  http: {
    post: vi.fn(),
  },
}));

describe('auth api', () => {
  it('posts login request and returns typed response', async () => {
    vi.mocked(http.post).mockResolvedValueOnce({
      data: {
        token: 'token',
        expiresAt: '2099-01-01T00:00:00Z',
        user: { id: 1, username: 'stu01', displayName: '学生', role: 'STUDENT' },
      },
    });

    const response = await login({ username: 'stu01', password: 'P@ssw0rd1' });

    expect(http.post).toHaveBeenCalledWith('/api/auth/login', { username: 'stu01', password: 'P@ssw0rd1' });
    expect(response.user.role).toBe('STUDENT');
  });

  it('posts register request without exposing extra fields', async () => {
    vi.mocked(http.post).mockResolvedValueOnce({
      data: { id: 2, username: 'teacher01', displayName: '教师', role: 'TEACHER' },
    });

    const response = await register({
      username: 'teacher01',
      password: 'P@ssw0rd1',
      displayName: '教师',
      role: 'TEACHER',
      teacherInviteCode: 'code',
    });

    expect(http.post).toHaveBeenCalledWith('/api/auth/register', {
      username: 'teacher01',
      password: 'P@ssw0rd1',
      displayName: '教师',
      role: 'TEACHER',
      teacherInviteCode: 'code',
    });
    expect(response).not.toHaveProperty('passwordHash');
  });
});
