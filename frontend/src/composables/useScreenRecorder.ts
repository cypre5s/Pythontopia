import { ref, onBeforeUnmount } from 'vue';

export interface ScreenRecorderOptions {
  targetElement: HTMLElement;
  audioSource?: MediaStream | null;
  maxDurationMs?: 30000 | 60000 | 90000;
}

const PREFERRED_MIME = 'video/webm; codecs=vp9';
const FALLBACK_MIME = 'video/webm';

function pickMimeType(): string {
  if (typeof MediaRecorder !== 'undefined' && MediaRecorder.isTypeSupported(PREFERRED_MIME)) {
    return PREFERRED_MIME;
  }
  return FALLBACK_MIME;
}

export function useScreenRecorder() {
  const isRecording = ref(false);
  const elapsed = ref(0);

  let recorder: MediaRecorder | null = null;
  let chunks: Blob[] = [];
  let canvas: HTMLCanvasElement | null = null;
  let animFrameId = 0;
  let startTime = 0;
  let maxTimer: ReturnType<typeof setTimeout> | null = null;
  let resolveStop: ((blob: Blob) => void) | null = null;

  function tickElapsed(): void {
    if (!isRecording.value) return;
    elapsed.value = Date.now() - startTime;
    animFrameId = requestAnimationFrame(tickElapsed);
  }

  function captureFrame(
    ctx: CanvasRenderingContext2D,
    target: HTMLElement,
    w: number,
    h: number,
  ): void {
    if (!isRecording.value) return;

    const rect = target.getBoundingClientRect();
    canvas!.width = rect.width * window.devicePixelRatio;
    canvas!.height = rect.height * window.devicePixelRatio;

    ctx.clearRect(0, 0, canvas!.width, canvas!.height);

    requestAnimationFrame(() => captureFrame(ctx, target, w, h));
  }

  async function start(options: ScreenRecorderOptions): Promise<void> {
    if (isRecording.value) return;

    const { targetElement, audioSource, maxDurationMs } = options;

    canvas = document.createElement('canvas');
    const rect = targetElement.getBoundingClientRect();
    canvas.width = Math.round(rect.width * window.devicePixelRatio);
    canvas.height = Math.round(rect.height * window.devicePixelRatio);

    const videoStream = canvas.captureStream(30);

    let combinedStream: MediaStream;
    if (audioSource && audioSource.getAudioTracks().length > 0) {
      combinedStream = new MediaStream([
        ...videoStream.getVideoTracks(),
        ...audioSource.getAudioTracks(),
      ]);
    } else {
      combinedStream = videoStream;
    }

    const mimeType = pickMimeType();
    recorder = new MediaRecorder(combinedStream, { mimeType });
    chunks = [];

    recorder.ondataavailable = (e: BlobEvent) => {
      if (e.data.size > 0) {
        chunks.push(e.data);
      }
    };

    recorder.onstop = () => {
      const blob = new Blob(chunks, { type: mimeType });
      chunks = [];
      resolveStop?.(blob);
      resolveStop = null;
    };

    recorder.start(1000);
    isRecording.value = true;
    elapsed.value = 0;
    startTime = Date.now();

    tickElapsed();
    renderLoop(targetElement);

    if (maxDurationMs) {
      maxTimer = setTimeout(() => {
        void stop();
      }, maxDurationMs);
    }
  }

  function renderLoop(target: HTMLElement): void {
    if (!canvas || !isRecording.value) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const draw = (): void => {
      if (!isRecording.value || !canvas) return;

      const rect = target.getBoundingClientRect();
      const w = Math.round(rect.width * window.devicePixelRatio);
      const h = Math.round(rect.height * window.devicePixelRatio);

      if (canvas.width !== w || canvas.height !== h) {
        canvas.width = w;
        canvas.height = h;
      }

      ctx.fillStyle = '#000';
      ctx.fillRect(0, 0, w, h);

      ctx.font = `${14 * window.devicePixelRatio}px monospace`;
      ctx.fillStyle = '#f1e5c8';
      ctx.textAlign = 'center';
      ctx.fillText(
        `Recording: ${(elapsed.value / 1000).toFixed(1)}s`,
        w / 2,
        h / 2,
      );

      requestAnimationFrame(draw);
    };

    requestAnimationFrame(draw);
  }

  async function stop(): Promise<Blob> {
    if (maxTimer !== null) {
      clearTimeout(maxTimer);
      maxTimer = null;
    }

    cancelAnimationFrame(animFrameId);
    isRecording.value = false;

    return new Promise<Blob>((resolve) => {
      if (!recorder || recorder.state === 'inactive') {
        resolve(new Blob(chunks, { type: FALLBACK_MIME }));
        return;
      }
      resolveStop = resolve;
      recorder.stop();
    });
  }

  function download(filename?: string): void {
    if (chunks.length === 0) return;
    const blob = new Blob(chunks, { type: pickMimeType() });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename ?? `pythontopia-recording-${Date.now()}.webm`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }

  onBeforeUnmount(() => {
    if (isRecording.value) {
      void stop();
    }
  });

  return { isRecording, elapsed, start, stop, download };
}
