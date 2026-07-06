import { request } from '../utils/request';
import {
  PublicProblem,
  Submission,
  SubmissionCreateRequest,
  SubmissionDetail,
} from './dto/submissions';

export function getPublicProblem(id: number): Promise<PublicProblem> {
  return request<PublicProblem>(`/problems/${id}/public`);
}

export function createSubmission(body: SubmissionCreateRequest): Promise<Submission> {
  return request<Submission, SubmissionCreateRequest>('/submissions', {
    method: 'POST',
    data: body,
  });
}

export function getSubmission(id: number): Promise<SubmissionDetail> {
  return request<SubmissionDetail>(`/submissions/${id}`);
}
