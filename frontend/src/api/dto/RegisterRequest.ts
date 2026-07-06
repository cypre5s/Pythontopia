import type { UserDto } from './UserDto';

export interface RegisterRequest {
  username: string;
  password: string;
  displayName: string;
  role: UserDto['role'];
  email?: string;
  teacherInviteCode?: string;
}
