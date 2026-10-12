import { beforeEach, describe, expect, it, vi } from 'vitest';
import { http } from '@/api/http';
import { searchAdminUsers } from '@/api/adminUsers';

vi.mock('@/api/http', () => ({
  http: { get: vi.fn() },
}));

describe('adminUsers API', () => {
  beforeEach(() => {
    vi.mocked(http.get).mockResolvedValue({
      data: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 },
    });
  });

  it('sends the selected role to the paged user endpoint', async () => {
    await searchAdminUsers({ page: 0, size: 20, role: 'TEACHER' });

    expect(http.get).toHaveBeenCalledWith('/api/admin/users', {
      params: { page: 0, size: 20, role: 'TEACHER' },
    });
  });
});
