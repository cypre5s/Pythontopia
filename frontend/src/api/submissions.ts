import { http } from './http';
import type {
  SubmissionCreateRequest,
  SubmissionDetailDto,
  SubmissionDto,
  SubmissionStatus,
} from './dto/Submission';

export async function createSubmission(
  request: SubmissionCreateRequest,
): Promise<SubmissionDto> {
  const response = await http.post<SubmissionDto>('/api/submissions', request);
  return response.data;
}

export async function getSubmission(id: number): Promise<SubmissionDetailDto> {
  const response = await http.get<SubmissionDetailDto>(`/api/submissions/${id}`);
  return response.data;
}

export interface SubmissionListQuery {
  problemId?: number;
  status?: SubmissionStatus;
  limit?: number;
  offset?: number;
}

export async function listSubmissions(
  query: SubmissionListQuery = {},
): Promise<SubmissionDto[]> {
  const params: Record<string, string | number> = {};
  if (query.problemId !== undefined) params.problemId = query.problemId;
  if (query.status) params.status = query.status;
  if (query.limit !== undefined) params.limit = query.limit;
  if (query.offset !== undefined) params.offset = query.offset;
  const response = await http.get<SubmissionDto[]>('/api/submissions', { params });
  return response.data;
}
