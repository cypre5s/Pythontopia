// R-13：与后端 V112 `users.role` enum（STUDENT / TEACHER / ADMIN）对齐。
export type UserRole = 'STUDENT' | 'TEACHER' | 'ADMIN';

export interface UserDto {
  id: number;
  username: string;
  displayName: string;
  role: UserRole;
}
