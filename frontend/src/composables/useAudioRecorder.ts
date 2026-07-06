import { ref, type Ref } from 'vue';

/**
 * 极简录音 composable（MediaRecorder 包装）——随身助教语音输入在浏览器 Web Speech API 不可用时的回退：
 * 录一段音频上传后端讯飞语音听写转文本。仅负责采集，转写在 {@code api/media.ts::transcribeAudio}。
 */
export interface AudioRecorder {
  supported: Ref<boolean>;
  recording: Ref<boolean>;
  /** 申请麦克风并开始录音；成功返回 true，无权限 / 不支持返回 false。 */
  start: () => Promise<boolean>;
  /** 停止录音并返回音频 Blob（无数据返回 null），同时释放麦克风轨道。 */
  stop: () => Promise<Blob | null>;
}

function detectSupport(): boolean {
  return (
    typeof navigator !== 'undefined' &&
    typeof navigator.mediaDevices?.getUserMedia === 'function' &&
    typeof window !== 'undefined' &&
    typeof window.MediaRecorder !== 'undefined'
  );
}

export function useAudioRecorder(): AudioRecorder {
  const supported = ref(detectSupport());
  const recording = ref(false);
  let recorder: MediaRecorder | null = null;
  let stream: MediaStream | null = null;
  let chunks: Blob[] = [];

  function cleanup(): void {
    stream?.getTracks().forEach((track) => track.stop());
    stream = null;
    recorder = null;
    recording.value = false;
  }

  async function start(): Promise<boolean> {
    if (!supported.value || recording.value) return false;
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    } catch {
      // 用户拒绝授权 / 无麦克风。
      return false;
    }
    chunks = [];
    recorder = new MediaRecorder(stream);
    recorder.ondataavailable = (event: BlobEvent) => {
      if (event.data && event.data.size > 0) chunks.push(event.data);
    };
    recorder.start();
    recording.value = true;
    return true;
  }

  function stop(): Promise<Blob | null> {
    return new Promise((resolve) => {
      const active = recorder;
      if (!active || !recording.value) {
        cleanup();
        resolve(null);
        return;
      }
      active.onstop = () => {
        const blob =
          chunks.length > 0 ? new Blob(chunks, { type: active.mimeType || 'audio/webm' }) : null;
        cleanup();
        resolve(blob);
      };
      active.stop();
    });
  }

  return { supported, recording, start, stop };
}
