import { request } from '../utils/request';
import { AuthUser, LoginRequest, LoginResponse, RegisterRequest } from './dto/auth';

export function login(body: LoginRequest): Promise<LoginResponse> {
  return request<LoginResponse, LoginRequest>('/auth/login', {
    method: 'POST',
    data: body,
    auth: false,
  });
}

export function register(body: RegisterRequest): Promise<AuthUser> {
  return request<AuthUser, RegisterRequest>('/auth/register', {
    method: 'POST',
    data: body,
    auth: false,
  });
}
