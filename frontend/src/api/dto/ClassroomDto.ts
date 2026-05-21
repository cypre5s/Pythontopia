export interface ClassroomDto {
  classroomId: number;
  name: string;
  description: string;
  teacherId: number;
  packId: number;
  status: 'ACTIVE' | 'ARCHIVED';
  joinCode: string | null;
}
