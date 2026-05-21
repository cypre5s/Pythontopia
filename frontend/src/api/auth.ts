import { http } from './http';
import type { LoginRequest } from './dto/LoginRequest';
import type { LoginResponse } from './dto/LoginResponse';
import type { RegisterRequest } from './dto/RegisterRequest';
import type { UserDto } from './dto/UserDto';

export async function login(req: LoginRequest): Promise<LoginResponse> {
  const response = await http.post<LoginResponse>('/api/auth/login', req);
  return response.data;
}

export async function register(req: RegisterRequest): Promise<UserDto> {
  const response = await http.post<UserDto>('/api/auth/register', req);
  return response.data;
}
