import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useSessionStore } from '@/stores/session';

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
}));

describe('session store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    localStorage.clear();
  });

  it('stores token and expiry after login', async () => {
    const store = useSessionStore();

    await store.login({ username: 'stu01', password: 'P@ssw0rd1' });

    expect(store.token).toBe('jwt-token');
    expect(store.displayName).toBe('学生一号');
    expect(localStorage.getItem('pythontopia.token')).toBe('jwt-token');
    expect(localStorage.getItem('pythontopia.expiresAt')).toBe('2099-01-01T00:00:00Z');
  });

  it('clears token and expiry on logout', async () => {
    const store = useSessionStore();
    await store.login({ username: 'stu01', password: 'P@ssw0rd1' });

    store.logout();

    expect(store.token).toBeNull();
    expect(localStorage.getItem('pythontopia.token')).toBeNull();
  });

  it('logs out when hydrating an expired token', () => {
    localStorage.setItem('pythontopia.token', 'old');
    localStorage.setItem('pythontopia.expiresAt', '2000-01-01T00:00:00Z');
    const store = useSessionStore();

    store.hydrateFromStorage();

    expect(store.isAuthenticated).toBe(false);
    expect(localStorage.getItem('pythontopia.token')).toBeNull();
  });
});
