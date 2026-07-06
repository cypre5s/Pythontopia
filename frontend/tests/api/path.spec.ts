// getActiveLearningPath 的 404 语义回归：后端「尚未生成学习路径」是正常空态，
// 必须映射为 null（而不是把 ApiClientError 当真错误抛给 store 显示报错）。
import { afterEach, describe, expect, it, vi } from 'vitest';
import { getActiveLearningPath } from '@/api/path';
import { ApiClientError, http } from '@/api/http';

describe('api/path getActiveLearningPath', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('http 拦截器包装的 404 ApiClientError → 返回 null', async () => {
    vi.spyOn(http, 'get').mockRejectedValueOnce(
      new ApiClientError(
        {
          code: 'PATH_NOT_FOUND',
          message: '尚未生成学习路径',
          details: [],
          traceId: 't',
          timestamp: new Date().toISOString(),
        },
        404,
      ),
    );

    await expect(getActiveLearningPath()).resolves.toBeNull();
  });

  it('非 404 错误原样抛出', async () => {
    vi.spyOn(http, 'get').mockRejectedValueOnce(
      new ApiClientError(
        {
          code: 'INTERNAL_ERROR',
          message: 'boom',
          details: [],
          traceId: 't',
          timestamp: new Date().toISOString(),
        },
        500,
      ),
    );

    await expect(getActiveLearningPath()).rejects.toBeInstanceOf(ApiClientError);
  });

  it('裸 AxiosError 形态的 404（response.status）也映射为 null', async () => {
    vi.spyOn(http, 'get').mockRejectedValueOnce({ response: { status: 404 } });

    await expect(getActiveLearningPath()).resolves.toBeNull();
  });
});
