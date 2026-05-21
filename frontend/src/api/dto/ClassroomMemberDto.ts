export interface ClassroomMemberDto {
  studentId: number;
  username: string;
  displayName: string;
  status: 'ACTIVE' | 'REMOVED';
}
