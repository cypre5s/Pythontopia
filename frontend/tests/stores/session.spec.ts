import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useSessionStore } from '@/stores/session';
import { login, refresh } from '@/api/auth';

const historyMocks = vi.hoisted(() => ({
  clearWorkshopChatHistoryCache: vi.fn(),
}));

vi.mock('@/api/workshopHistory', () => ({
  clearWorkshopChatHistoryCache: historyMocks.clearWorkshopChatHistoryCache,
}));

vi.mock('@/api/auth', () => ({
  login: vi.fn(async () => ({
    token: 'jwt-token',
    expiresAt: '2099-01-01T00:00:00Z',
    user: { id: 1, username: 'stu01', displayName: '学生一号', role: 'STUDENT' },
  })),
  register: vi.fn(async (req: unknown) => ({
    id: 1,
    username: typeof req === 'object' && req !== null && 'username' in req ? String(req.username) : 'stu01',
    displayName: '学生一号',
    role: 'STUDENT',
  })),
  refresh: vi.fn(async () => ({
    token: 'jwt-token-renewed',
    expiresAt: '2099-06-01T00:00:00Z',
    user: { id: 2, username: 'tea01', displayName: '王老师', role: 'TEACHER' },
  })),
}));

const refreshMock = vi.mocked(refresh);
const loginMock = vi.mocked(login);

async function flushAsync(): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, 0));
}

describe('session store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    localStorage.clear();
    refreshMock.mockClear();
    loginMock.mockClear();
    historyMocks.clearWorkshopChatHistoryCache.mockClear();
  });

  it('stores token / expiry / user snapshot after login', async () => {
    const store = useSessionStore();

    await store.login({ username: 'stu01', password: 'P@ssw0rd1' });

    expect(store.token).toBe('jwt-token');
    expect(store.displayName).toBe('学生一号');
    expect(localStorage.getItem('pythontopia.token')).toBe('jwt-token');
    expect(localStorage.getItem('pythontopia.expiresAt')).toBe('2099-01-01T00:00:00Z');
    expect(JSON.parse(localStorage.getItem('pythontopia.user') ?? '{}')).toMatchObject({
      username: 'stu01',
      role: 'STUDENT',
    });
    expect(historyMocks.clearWorkshopChatHistoryCache).toHaveBeenCalledTimes(1);
  });

  it('clears token / expiry / user on logout', async () => {
    const store = useSessionStore();
    await store.login({ username: 'stu01', password: 'P@ssw0rd1' });
    historyMocks.clearWorkshopChatHistoryCache.mockClear();

    store.logout();

    expect(store.token).toBeNull();
    expect(store.user).toBeNull();
    expect(localStorage.getItem('pythontopia.token')).toBeNull();
    expect(localStorage.getItem('pythontopia.user')).toBeNull();
    expect(historyMocks.clearWorkshopChatHistoryCache).toHaveBeenCalledTimes(1);
  });

  it('does not clear the active account cache when login fails', async () => {
    loginMock.mockRejectedValueOnce(new Error('bad credentials'));
    const store = useSessionStore();

    await expect(store.login({ username: 'stu01', password: 'wrong-pass' })).rejects.toThrow(
      'bad credentials',
    );

    expect(historyMocks.clearWorkshopChatHistoryCache).not.toHaveBeenCalled();
  });

  it('logs out when hydrating an expired token', () => {
    localStorage.setItem('pythontopia.token', 'old');
    localStorage.setItem('pythontopia.expiresAt', '2000-01-01T00:00:00Z');
    const store = useSessionStore();

    store.hydrateFromStorage();

    expect(store.isAuthenticated).toBe(false);
    expect(localStorage.getItem('pythontopia.token')).toBeNull();
    expect(refreshMock).not.toHaveBeenCalled();
  });

  it('hydrate restores user/role synchronously from snapshot (teacher keeps role after F5)', () => {
    localStorage.setItem('pythontopia.token', 'valid');
    localStorage.setItem('pythontopia.expiresAt', '2099-01-01T00:00:00Z');
    localStorage.setItem(
      'pythontopia.user',
      JSON.stringify({ id: 2, username: 'tea01', displayName: '王老师', role: 'TEACHER' }),
    );
    const store = useSessionStore();

    store.hydrateFromStorage();

    expect(store.role).toBe('TEACHER');
    expect(store.displayName).toBe('王老师');
  });

  it('hydrate with valid token refreshes session and applies server-authoritative user', async () => {
    localStorage.setItem('pythontopia.token', 'valid');
    localStorage.setItem('pythontopia.expiresAt', '2099-01-01T00:00:00Z');
    const store = useSessionStore();

    store.hydrateFromStorage();
    await flushAsync();

    expect(refreshMock).toHaveBeenCalledWith('valid');
    expect(store.token).toBe('jwt-token-renewed');
    expect(store.role).toBe('TEACHER');
    expect(localStorage.getItem('pythontopia.token')).toBe('jwt-token-renewed');
  });

  it('hydrate keeps local session when refresh fails with a network error', async () => {
    refreshMock.mockRejectedValueOnce(new Error('network down'));
    localStorage.setItem('pythontopia.token', 'valid');
    localStorage.setItem('pythontopia.expiresAt', '2099-01-01T00:00:00Z');
    localStorage.setItem(
      'pythontopia.user',
      JSON.stringify({ id: 2, username: 'tea01', displayName: '王老师', role: 'TEACHER' }),
    );
    const store = useSessionStore();

    store.hydrateFromStorage();
    await flushAsync();

    expect(store.isAuthenticated).toBe(true);
    expect(store.role).toBe('TEACHER');
  });

  it('hydrate tolerates a corrupted user snapshot', () => {
    localStorage.setItem('pythontopia.token', 'valid');
    localStorage.setItem('pythontopia.expiresAt', '2099-01-01T00:00:00Z');
    localStorage.setItem('pythontopia.user', '{not-json');
    const store = useSessionStore();

    store.hydrateFromStorage();

    expect(store.user).toBeNull();
    expect(store.isAuthenticated).toBe(true);
  });
});
