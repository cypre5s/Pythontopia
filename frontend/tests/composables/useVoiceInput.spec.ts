import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useVoiceInput } from '@/composables/useVoiceInput';

class FakeRecognition {
  lang = '';
  continuous = false;
  interimResults = false;
  onresult: ((event: { results: ArrayLike<ArrayLike<{ transcript: string }>> }) => void) | null = null;
  onend: (() => void) | null = null;
  start = vi.fn();
  stop = vi.fn(() => {
    this.onend?.();
  });
}

describe('useVoiceInput', () => {
  beforeEach(() => {
    // @ts-expect-error test-only browser API injection
    window.webkitSpeechRecognition = FakeRecognition;
    // @ts-expect-error clear the standards alias between tests
    delete window.SpeechRecognition;
  });

  it('reports supported when SpeechRecognition exists', () => {
    const voice = useVoiceInput();
    expect(voice.supported.value).toBe(true);
  });

  it('updates transcript on result and toggles listening', () => {
    const voice = useVoiceInput();
    voice.start();
    expect(voice.listening.value).toBe(true);
    voice._recognition?.onresult?.({ results: [[{ transcript: '什么是列表推导' }]] });
    expect(voice.transcript.value).toBe('什么是列表推导');
    voice.stop();
    expect(voice.listening.value).toBe(false);
  });

  it('reports unsupported gracefully', () => {
    // @ts-expect-error remove the vendor browser API
    delete window.webkitSpeechRecognition;
    // @ts-expect-error remove the standards browser API
    delete window.SpeechRecognition;
    const voice = useVoiceInput();
    expect(voice.supported.value).toBe(false);
    expect(() => voice.start()).not.toThrow();
  });
});
