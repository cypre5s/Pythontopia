export interface UserDto {
  id: number;
  username: string;
  displayName: string;
  role: 'STUDENT' | 'TEACHER';
}
