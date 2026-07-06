import { ref, type Ref } from 'vue';

interface SpeechRecognitionResultLike {
  transcript: string;
}

interface SpeechRecognitionLike {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  onresult: ((event: { results: ArrayLike<ArrayLike<SpeechRecognitionResultLike>> }) => void) | null;
  onend: (() => void) | null;
  start(): void;
  stop(): void;
}

type SpeechRecognitionCtor = new () => SpeechRecognitionLike;

interface SpeechWindow {
  SpeechRecognition?: SpeechRecognitionCtor;
  webkitSpeechRecognition?: SpeechRecognitionCtor;
}

export interface VoiceInput {
  supported: Ref<boolean>;
  listening: Ref<boolean>;
  transcript: Ref<string>;
  start: () => void;
  stop: () => void;
  _recognition: SpeechRecognitionLike | null;
}

export function useVoiceInput(): VoiceInput {
  const speechWindow = window as Window & SpeechWindow;
  const Recognition = speechWindow.SpeechRecognition ?? speechWindow.webkitSpeechRecognition ?? null;
  const supported = ref(Recognition !== null);
  const listening = ref(false);
  const transcript = ref('');
  let recognition: SpeechRecognitionLike | null = null;

  const api: VoiceInput = {
    supported,
    listening,
    transcript,
    start,
    stop,
    _recognition: null,
  };

  function start(): void {
    if (!Recognition) return;
    recognition = new Recognition();
    recognition.lang = 'zh-CN';
    recognition.continuous = false;
    recognition.interimResults = false;
    recognition.onresult = (event) => {
      transcript.value = event.results[0]?.[0]?.transcript ?? '';
    };
    recognition.onend = () => {
      listening.value = false;
    };
    listening.value = true;
    recognition.start();
    api._recognition = recognition;
  }

  function stop(): void {
    recognition?.stop();
    listening.value = false;
  }

  return api;
}
