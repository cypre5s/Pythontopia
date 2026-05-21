import { http } from './http';
import type { PublicProblemDto } from './dto/Submission';

export async function getPublicProblem(id: number): Promise<PublicProblemDto> {
  const response = await http.get<PublicProblemDto>(`/api/problems/${id}/public`);
  return response.data;
}
