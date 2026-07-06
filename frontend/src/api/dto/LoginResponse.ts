import type { UserDto } from './UserDto';

export interface LoginResponse {
  token: string;
  expiresAt: string;
  user: UserDto;
}
