// 百晓工坊「学生上传文件」前端访问层（契约 §3.2 / §3.3 / §3.5）。
//
// 后端端点（WorkshopUploadController / UploadRetrievalController，前缀 /api，JWT，requireStudent）：
//   POST   /api/workshop/uploads            multipart 字段 `file` → UploadDto
//   GET    /api/workshop/uploads            → UploadDto[]
//   GET    /api/workshop/uploads/{id}       → UploadDetailDto（含 extractedText）
//   DELETE /api/workshop/uploads/{id}
//   POST   /api/workshop/uploads/retrieve   {query,uploadIds?,topK?} → {hits:[...]}（scoped 向量检索）
//
// 红线（契约 §0 / §3.5）：学生上传内容只进**带标签的 context 通道**（`【你上传的《…》】…`），
// 绝不混进 packId=43 课件 evidence / 引用。本模块仅负责取数与拼装带标签文本，不产出任何课件证据。

import { http } from './http';

// ============ DTO（camelCase JSON，契约 §3.3） ============

/** 上传解析状态：PENDING 解析中 / READY 就绪 / FAILED 解析失败。 */
export type UploadStatus = 'PENDING' | 'READY' | 'FAILED';

/** 上传元信息（创建 / 列表返回）。 */
export interface UploadDto {
  id: number;
  filename: string;
  ext: string;
  byteSize: number;
  charCount: number;
  lineCount: number;
  status: UploadStatus;
  /** status==='FAILED' 时的失败原因（其余状态可空）。 */
  error?: string;
}

/** 上传详情（含解析全文，A 通路 extractedText 来源）。 */
export interface UploadDetailDto extends UploadDto {
  extractedText: string;
}

/** scoped 检索请求（B 通路）。 */
export interface UploadRetrieveRequest {
  query: string;
  /** 限定只在这些上传内检索（缺省检索当前用户全部上传）。 */
  uploadIds?: number[];
  topK?: number;
}

/** 一条检索命中片段。 */
export interface UploadRetrieveHit {
  uploadId: number;
  filename: string;
  chunkIndex: number;
  text: string;
  score: number;
}

export interface UploadRetrieveResponse {
  hits: UploadRetrieveHit[];
}

// ============ REST 端点 ============

/** 上传单个文件（multipart，字段名 `file` 为契约冻结）。axios 自动设置 multipart 边界。 */
export async function uploadFile(file: File): Promise<UploadDto> {
  const form = new FormData();
  form.append('file', file, file.name);
  const res = await http.post<UploadDto>('/api/workshop/uploads', form);
  return res.data;
}

/** 列出当前学生的全部上传。 */
export async function listUploads(): Promise<UploadDto[]> {
  const res = await http.get<UploadDto[]>('/api/workshop/uploads');
  return res.data;
}

/** 取单个上传详情（含 extractedText 解析全文）。 */
export async function getUpload(id: number): Promise<UploadDetailDto> {
  const res = await http.get<UploadDetailDto>(`/api/workshop/uploads/${id}`);
  return res.data;
}

/** 删除某个上传。 */
export async function deleteUpload(id: number): Promise<void> {
  await http.delete(`/api/workshop/uploads/${id}`);
}

/** scoped 向量检索（后端 fail-soft：embedding 不可用时回退字面匹配）。 */
export async function retrieveUploads(
  request: UploadRetrieveRequest,
): Promise<UploadRetrieveResponse> {
  const res = await http.post<UploadRetrieveResponse>(
    '/api/workshop/uploads/retrieve',
    request,
  );
  return res.data;
}

// ============ 前端校验 / 展示（纯函数，无副作用，易测） ============

/** 允许扩展名白名单（契约 §3.4）。前端预校验只为即时反馈，后端仍为权威。 */
export const ALLOWED_UPLOAD_EXTS = ['.py', '.txt', '.md', '.csv', '.pdf', '.docx'] as const;

/** 单文件大小上限 5 MB（契约 §3.4）。 */
export const MAX_UPLOAD_BYTES = 5 * 1024 * 1024;

const EXT_ICONS: Record<string, string> = {
  '.py': '🐍',
  '.txt': '📄',
  '.md': '📝',
  '.csv': '📊',
  '.pdf': '📕',
  '.docx': '📘',
};

/** 取文件名小写扩展名（含点；无扩展名返回空串）。 */
export function extOf(filename: string): string {
  const dot = filename.lastIndexOf('.');
  return dot >= 0 ? filename.slice(dot).toLowerCase() : '';
}

/** 按扩展名取类型图标（未知类型回退 📎）。 */
export function attachmentIcon(ext: string): string {
  return EXT_ICONS[ext.toLowerCase()] ?? '📎';
}

/** 人类可读文件大小。 */
export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/** 校验文件：合规返回 null，否则返回中文拒绝原因（用于失败 pill 文案）。 */
export function uploadRejectionReason(file: File): string | null {
  const ext = extOf(file.name);
  if (!(ALLOWED_UPLOAD_EXTS as readonly string[]).includes(ext)) {
    return `不支持的类型（仅 ${ALLOWED_UPLOAD_EXTS.join(' ')}）`;
  }
  if (file.size > MAX_UPLOAD_BYTES) {
    return '文件超过 5 MB';
  }
  return null;
}

// ============ context 注入（红线：只进带标签 context，绝不当课件证据） ============

/** A 通路全文注入上限（约 6000 字，契约 §3.5）。 */
export const UPLOAD_CONTEXT_CHAR_LIMIT = 6000;

const TRUNCATED_SUFFIX = '（已截断）';

/** A 通路：单个附件全文 → 截断 + 「你上传的《…》」标签。 */
export function buildAttachmentContext(
  filename: string,
  text: string,
  limit = UPLOAD_CONTEXT_CHAR_LIMIT,
): string {
  const raw = text ?? '';
  const body = raw.length > limit ? `${raw.slice(0, limit)}${TRUNCATED_SUFFIX}` : raw;
  return `【你上传的《${filename}》】\n${body}`;
}

/** B 通路：检索命中片段 → 「你上传的《…》相关片段」标签。 */
export function buildRetrievalContext(filename: string, text: string): string {
  return `【你上传的《${filename}》相关片段】\n${text ?? ''}`;
}

/** 把上传注入文本并进既有 context（既有在前、附件在后；任一为空则取另一方）。 */
export function mergeUploadContext(base: string, injection: string): string {
  if (!injection) return base;
  return base ? `${base}\n\n${injection}` : injection;
}

/** buildUploadInjection 的附件引用（与 ComposerSubmitPayload.attachments 同形）。 */
export interface UploadAttachmentRef {
  id: number;
  filename: string;
  ext: string;
  charCount: number;
}

export interface UploadInjectionOptions {
  /** B 通路检索 topK（默认 4）。 */
  topK?: number;
  /** A 通路单附件截断字数（默认 UPLOAD_CONTEXT_CHAR_LIMIT）。 */
  charLimit?: number;
}

/**
 * 组装本轮提问要注入既有 context 的「上传」文本（红线：带标签、绝不当课件证据）：
 *   A 通路：对本轮附件逐个 GET extractedText，截断后以《…》标签拼入；
 *   B 通路（scoped）：仅检索「本会话早先上传」（排除本轮全文已注入者）。本轮无早先上传则不检索，
 *     满足「默认不每问都检索」。任一步骤失败 fail-soft（跳过该来源，不阻断答疑）。
 *
 * @param query             本轮提问原文（作为检索 query）。
 * @param attachments       本轮随发的附件（A 通路，已就绪、带后端 upload id）。
 * @param sessionUploadIds  本会话累计的全部上传 id（含本轮；函数内部排除本轮以实现 scoped B 通路）。
 */
export async function buildUploadInjection(
  query: string,
  attachments: UploadAttachmentRef[],
  sessionUploadIds: number[],
  opts: UploadInjectionOptions = {},
): Promise<string> {
  const limit = opts.charLimit ?? UPLOAD_CONTEXT_CHAR_LIMIT;
  const topK = opts.topK ?? 4;
  const parts: string[] = [];

  // A 通路：本轮附件全文（逐个取，单个失败不影响其余）。
  for (const a of attachments) {
    try {
      const detail = await getUpload(a.id);
      if (detail.status === 'READY' && detail.extractedText) {
        parts.push(buildAttachmentContext(detail.filename, detail.extractedText, limit));
      }
    } catch {
      /* 单附件取全文失败 fail-soft：跳过该附件 */
    }
  }

  // B 通路：scoped 检索本会话「早先」上传（排除本轮全文已注入者），默认不每问都检索。
  const currentIds = new Set(attachments.map((a) => a.id));
  const scopedIds = sessionUploadIds.filter((id) => !currentIds.has(id));
  const trimmedQuery = query.trim();
  if (scopedIds.length > 0 && trimmedQuery.length > 0) {
    try {
      const res = await retrieveUploads({ query: trimmedQuery, uploadIds: scopedIds, topK });
      for (const hit of res.hits) {
        parts.push(buildRetrievalContext(hit.filename, hit.text));
      }
    } catch {
      /* 检索失败 fail-soft：退回仅全文 / 无上传上下文 */
    }
  }

  return parts.join('\n\n');
}
