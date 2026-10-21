import type { UserRole } from '@/api/dto/UserDto';

export function defaultLandingPath(role: UserRole | null): string {
  return role === 'ADMIN' ? '/admin' : '/world';
}

export function loginDestination(role: UserRole | null, requestedPath?: string): string {
  if (!requestedPath) return defaultLandingPath(role);
  const safePath = toSafeInternalPath(requestedPath);
  return role === 'ADMIN' && !/^\/admin(?:[/?#]|$)/.test(safePath)
    ? '/admin'
    : safePath;
}

/** Accept only single-slash site paths; protocol-relative URLs could escape via location.assign. */
export function toSafeInternalPath(targetPath: string): string {
  return /^\/(?!\/)/.test(targetPath) ? targetPath : '/';
}
