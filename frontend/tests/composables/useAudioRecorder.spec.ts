import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { useAudioRecorder } from '@/composables/useAudioRecorder';

class FakeMediaRecorder {
  ondataavailable: ((event: { data: Blob }) => void) | null = null;
  onstop: (() => void) | null = null;
  mimeType = 'audio/webm';
  state = 'inactive';

  constructor(public stream: unknown) {}

  start(): void {
    this.state = 'recording';
  }

  stop(): void {
    this.state = 'inactive';
    this.ondataavailable?.({ data: new Blob(['chunk'], { type: 'audio/webm' }) });
    this.onstop?.();
  }
}

const getUserMedia = vi.fn();

beforeEach(() => {
  getUserMedia.mockReset();
  vi.stubGlobal('MediaRecorder', FakeMediaRecorder);
  Object.defineProperty(globalThis.navigator, 'mediaDevices', {
    configurable: true,
    value: { getUserMedia },
  });
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('useAudioRecorder', () => {
  it('reports supported when MediaRecorder + getUserMedia exist', () => {
    const recorder = useAudioRecorder();
    expect(recorder.supported.value).toBe(true);
  });

  it('records and returns an audio blob on stop', async () => {
    const trackStop = vi.fn();
    getUserMedia.mockResolvedValueOnce({ getTracks: () => [{ stop: trackStop }] });

    const recorder = useAudioRecorder();
    const started = await recorder.start();
    expect(started).toBe(true);
    expect(recorder.recording.value).toBe(true);

    const blob = await recorder.stop();
    expect(blob).toBeInstanceOf(Blob);
    expect(recorder.recording.value).toBe(false);
    // 停止后释放麦克风轨道。
    expect(trackStop).toHaveBeenCalled();
  });

  it('returns false (and stays idle) when the mic permission is denied', async () => {
    getUserMedia.mockRejectedValueOnce(new Error('NotAllowedError'));
    const recorder = useAudioRecorder();
    expect(await recorder.start()).toBe(false);
    expect(recorder.recording.value).toBe(false);
  });
});
