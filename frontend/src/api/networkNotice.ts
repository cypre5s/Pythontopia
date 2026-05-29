export type NetworkNoticeCode = 'NETWORK_ERROR' | 'TIMEOUT';

export interface NetworkNoticeEnv {
  readonly DEV?: boolean;
  readonly VITE_SHOW_BACKEND_OFFLINE_TOAST?: string;
}

export function shouldShowNetworkToast(
  code: NetworkNoticeCode,
  env: NetworkNoticeEnv,
): boolean {
  if (code === 'TIMEOUT') {
    return true;
  }
  if (env.DEV && env.VITE_SHOW_BACKEND_OFFLINE_TOAST !== 'true') {
    return false;
  }
  return true;
}
