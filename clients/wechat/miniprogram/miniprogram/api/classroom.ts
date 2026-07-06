import { request } from '../utils/request';
import { Classroom, ClassroomQuest, JoinClassroomRequest } from './dto/classroom';

export function getMyClassrooms(): Promise<Classroom[]> {
  return request<Classroom[]>('/classrooms/mine');
}

export function joinClassroom(body: JoinClassroomRequest): Promise<Classroom> {
  return request<Classroom, JoinClassroomRequest>('/classrooms/join', {
    method: 'POST',
    data: body,
  });
}

export function getMyClassroomQuests(): Promise<ClassroomQuest[]> {
  return request<ClassroomQuest[]>('/classroom/quests/mine');
}
