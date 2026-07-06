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

/** 用未过期的 access token 换发新 token（POST /api/auth/refresh），同时取回最新 user。 */
export async function refresh(token: string): Promise<LoginResponse> {
  const response = await http.post<LoginResponse>('/api/auth/refresh', { token });
  return response.data;
}
