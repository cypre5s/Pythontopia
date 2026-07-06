export type UserRole = 'STUDENT' | 'TEACHER' | 'ADMIN';

export interface AuthUser {
  id: number;
  username: string;
  displayName: string;
  role: UserRole;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  expiresAt: string;
  user: AuthUser;
}

export interface RegisterRequest {
  username: string;
  password: string;
  displayName: string;
  role: UserRole;
  email?: string;
  teacherInviteCode?: string;
}
