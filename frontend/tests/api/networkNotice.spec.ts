import { describe, expect, it } from 'vitest';
import { shouldShowNetworkToast } from '@/api/networkNotice';

describe('network notice policy', () => {
  it('silences backend offline toast in local development demo mode', () => {
    expect(shouldShowNetworkToast('NETWORK_ERROR', { DEV: true })).toBe(false);
  });

  it('allows explicit opt-in for backend offline toast while debugging dev mode', () => {
    expect(
      shouldShowNetworkToast('NETWORK_ERROR', {
        DEV: true,
        VITE_SHOW_BACKEND_OFFLINE_TOAST: 'true',
      }),
    ).toBe(true);
  });

  it('still shows timeout notices and production network failures', () => {
    expect(shouldShowNetworkToast('TIMEOUT', { DEV: true })).toBe(true);
    expect(shouldShowNetworkToast('NETWORK_ERROR', { DEV: false })).toBe(true);
  });
});
