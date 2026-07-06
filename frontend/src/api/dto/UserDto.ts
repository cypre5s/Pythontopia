// 与后端 `users.role` enum 对齐。
export type UserRole = 'STUDENT' | 'TEACHER' | 'ADMIN';

export interface UserDto {
  id: number;
  username: string;
  displayName: string;
  role: UserRole;
}
