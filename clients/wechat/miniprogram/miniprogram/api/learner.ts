import { request } from '../utils/request';
import {
  DimensionUpdateResult,
  LearnerProfile,
  MasterySnapshot,
  ProfileDimension,
  SelfDimensionRequest,
} from './dto/learner';

/** GET /api/learner/profile —— 未初始化时后端返回 204，这里归一化为 null。 */
export async function getProfile(): Promise<LearnerProfile | null> {
  const profile = await request<LearnerProfile | null>('/learner/profile');
  return profile ?? null;
}

export function getDimensions(): Promise<ProfileDimension[]> {
  return request<ProfileDimension[]>('/learner/profile/dimensions');
}

export function updateDimension(
  key: string,
  body: SelfDimensionRequest,
): Promise<DimensionUpdateResult> {
  return request<DimensionUpdateResult, SelfDimensionRequest>(
    `/learner/profile/dimensions/${key}`,
    { method: 'PUT', data: body },
  );
}

export function getMastery(): Promise<MasterySnapshot[]> {
  return request<MasterySnapshot[]>('/learner/mastery');
}
