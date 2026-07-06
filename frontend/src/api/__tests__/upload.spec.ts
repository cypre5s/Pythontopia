import { describe, it, expect, vi, beforeEach } from 'vitest';

// 后端端点此刻可能未就绪：mock 掉 http（axios 实例），只验证前端访问层 + 注入逻辑。
vi.mock('@/api/http', () => ({
  http: { get: vi.fn(), post: vi.fn(), delete: vi.fn() },
}));

import { http } from '@/api/http';
import {
  uploadFile,
  listUploads,
  getUpload,
  deleteUpload,
  retrieveUploads,
  extOf,
  attachmentIcon,
  formatFileSize,
  uploadRejectionReason,
  buildAttachmentContext,
  buildRetrievalContext,
  mergeUploadContext,
  buildUploadInjection,
  MAX_UPLOAD_BYTES,
  UPLOAD_CONTEXT_CHAR_LIMIT,
  type UploadDto,
  type UploadDetailDto,
  type UploadRetrieveResponse,
} from '@/api/upload';

const READY: UploadDto = {
  id: 7,
  filename: 'main.py',
  ext: '.py',
  byteSize: 24,
  charCount: 23,
  lineCount: 2,
  status: 'READY',
};

beforeEach(() => {
  vi.clearAllMocks();
});

describe('upload.ts · REST 访问层', () => {
  it('uploadFile：POST multipart，字段名 file（契约冻结）', async () => {
    vi.mocked(http.post).mockResolvedValue({ data: READY });
    const file = new File(['print(1)'], 'main.py', { type: 'text/x-python' });

    const dto = await uploadFile(file);

    expect(dto).toEqual(READY);
    expect(http.post).toHaveBeenCalledTimes(1);
    const call = vi.mocked(http.post).mock.calls[0];
    expect(call?.[0]).toBe('/api/workshop/uploads');
    const body = call?.[1] as FormData | undefined;
    expect(body).toBeInstanceOf(FormData);
    expect(body?.get('file')).toBeInstanceOf(File);
  });

  it('listUploads / getUpload / deleteUpload 命中对应端点', async () => {
    vi.mocked(http.get).mockResolvedValueOnce({ data: [READY] });
    expect(await listUploads()).toEqual([READY]);
    expect(http.get).toHaveBeenLastCalledWith('/api/workshop/uploads');

    const detail: UploadDetailDto = { ...READY, extractedText: '全文' };
    vi.mocked(http.get).mockResolvedValueOnce({ data: detail });
    expect(await getUpload(7)).toEqual(detail);
    expect(http.get).toHaveBeenLastCalledWith('/api/workshop/uploads/7');

    vi.mocked(http.delete).mockResolvedValueOnce({ data: undefined });
    await deleteUpload(7);
    expect(http.delete).toHaveBeenCalledWith('/api/workshop/uploads/7');
  });

  it('retrieveUploads：POST body {query,uploadIds,topK}', async () => {
    const resp: UploadRetrieveResponse = {
      hits: [{ uploadId: 2, filename: 'b.txt', chunkIndex: 0, text: 'SNIPPET', score: 0.9 }],
    };
    vi.mocked(http.post).mockResolvedValue({ data: resp });

    const out = await retrieveUploads({ query: 'q', uploadIds: [2], topK: 3 });

    expect(out).toEqual(resp);
    expect(http.post).toHaveBeenCalledWith('/api/workshop/uploads/retrieve', {
      query: 'q',
      uploadIds: [2],
      topK: 3,
    });
  });
});

describe('upload.ts · 纯函数（校验 / 展示 / 标签）', () => {
  it('extOf / attachmentIcon / formatFileSize', () => {
    expect(extOf('a.PY')).toBe('.py');
    expect(extOf('noext')).toBe('');
    expect(attachmentIcon('.py')).toBe('🐍');
    expect(attachmentIcon('.zzz')).toBe('📎');
    expect(formatFileSize(512)).toBe('512 B');
    expect(formatFileSize(2048)).toBe('2.0 KB');
    expect(formatFileSize(3 * 1024 * 1024)).toBe('3.0 MB');
  });

  it('uploadRejectionReason：白名单 + 5MB 上限', () => {
    expect(uploadRejectionReason(new File(['x'], 'ok.py'))).toBeNull();
    expect(uploadRejectionReason(new File(['x'], 'bad.exe'))).toContain('不支持');
    const big = new File(['x'], 'big.py');
    Object.defineProperty(big, 'size', { value: MAX_UPLOAD_BYTES + 1 });
    expect(uploadRejectionReason(big)).toContain('5 MB');
  });

  it('buildAttachmentContext：带《》标签；超限截断加（已截断）', () => {
    const short = buildAttachmentContext('a.py', 'CODE');
    expect(short).toBe('【你上传的《a.py》】\nCODE');

    const long = 'x'.repeat(UPLOAD_CONTEXT_CHAR_LIMIT + 50);
    const out = buildAttachmentContext('big.txt', long);
    expect(out.startsWith('【你上传的《big.txt》】\n')).toBe(true);
    expect(out.endsWith('（已截断）')).toBe(true);
    // 标签 + 截断正文（恰好 limit 字）+ 后缀。
    const head = '【你上传的《big.txt》】\n';
    expect(out.length).toBe(head.length + UPLOAD_CONTEXT_CHAR_LIMIT + '（已截断）'.length);
  });

  it('buildRetrievalContext / mergeUploadContext', () => {
    expect(buildRetrievalContext('b.txt', 'SNIP')).toBe('【你上传的《b.txt》相关片段】\nSNIP');
    expect(mergeUploadContext('', 'INJ')).toBe('INJ');
    expect(mergeUploadContext('BASE', '')).toBe('BASE');
    expect(mergeUploadContext('BASE', 'INJ')).toBe('BASE\n\nINJ');
  });
});

describe('upload.ts · buildUploadInjection（A/B 通路注入，红线：带标签）', () => {
  it('A 通路：本轮附件 → GET 全文带标签注入；本轮即全部上传 → 不检索', async () => {
    const detail: UploadDetailDto = {
      ...READY,
      id: 1,
      filename: 'a.py',
      extractedText: 'X CONTENT',
    };
    vi.mocked(http.get).mockResolvedValue({ data: detail });

    const out = await buildUploadInjection(
      '讲讲',
      [{ id: 1, filename: 'a.py', ext: '.py', charCount: 5 }],
      [1],
    );

    expect(http.get).toHaveBeenCalledWith('/api/workshop/uploads/1');
    expect(out).toContain('【你上传的《a.py》】');
    expect(out).toContain('X CONTENT');
    // scopedIds 排除本轮附件 → 不应触发检索。
    expect(http.post).not.toHaveBeenCalled();
  });

  it('B 通路（scoped）：本会话早先上传 → retrieve hits 带标签注入', async () => {
    const resp: UploadRetrieveResponse = {
      hits: [{ uploadId: 2, filename: 'notes.md', chunkIndex: 3, text: 'SNIPPET', score: 0.8 }],
    };
    vi.mocked(http.post).mockResolvedValue({ data: resp });

    // 本轮无附件，但会话早先有上传 id=2 → 走 B 通路。
    const out = await buildUploadInjection('我的笔记里怎么说', [], [2]);

    expect(http.get).not.toHaveBeenCalled();
    expect(http.post).toHaveBeenCalledWith('/api/workshop/uploads/retrieve', {
      query: '我的笔记里怎么说',
      uploadIds: [2],
      topK: 4,
    });
    expect(out).toContain('【你上传的《notes.md》相关片段】');
    expect(out).toContain('SNIPPET');
  });

  it('附件 status 非 READY → 跳过该附件全文', async () => {
    vi.mocked(http.get).mockResolvedValue({
      data: { ...READY, id: 1, filename: 'a.py', status: 'PENDING', extractedText: '' },
    });
    const out = await buildUploadInjection('q', [{ id: 1, filename: 'a.py', ext: '.py', charCount: 0 }], [1]);
    expect(out).toBe('');
  });

  it('取全文 / 检索失败 → fail-soft（不抛，返回已得部分）', async () => {
    vi.mocked(http.get).mockRejectedValue(new Error('boom-get'));
    vi.mocked(http.post).mockRejectedValue(new Error('boom-post'));
    // 附件 1 全文失败；会话另有早先上传 9 → 检索也失败。两路皆 fail-soft。
    const out = await buildUploadInjection(
      'q',
      [{ id: 1, filename: 'a.py', ext: '.py', charCount: 0 }],
      [1, 9],
    );
    expect(out).toBe('');
  });

  it('空查询且无附件 → 不发任何请求', async () => {
    const out = await buildUploadInjection('   ', [], []);
    expect(out).toBe('');
    expect(http.get).not.toHaveBeenCalled();
    expect(http.post).not.toHaveBeenCalled();
  });
});
