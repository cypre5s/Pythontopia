export interface VideoProgressPacket {
  classroom_id: number;
  material_id: number;
  playback_session_id: string;
  sequence: number;
  position_seconds: number;
  playing: boolean;
  playback_rate: number;
}

/** Serial evidence delivery. Failed packets keep their original identity/body for retries. */
export function createVideoProgressQueue<T>(options: {
  classroomId: number;
  materialId: number;
  active: () => boolean;
  send: (packet: VideoProgressPacket) => Promise<T>;
  saved: (progress: T) => void;
  failed: (error: unknown) => void;
}) {
  let sessionId = "", sequence = 0, running = false, disposed = false;
  let pending: VideoProgressPacket[] = [], task: Promise<void> = Promise.resolve();
  function flush() {
    if (running) return task;
    running = true;
    task = (async () => {
      while (pending.length && !disposed && options.active()) {
        const packet = pending[0]!;
        try {
          const progress = await options.send(packet);
          if (disposed || !options.active()) return;
          pending.shift();
          options.saved(progress);
        } catch (error) {
          if (!disposed && options.active()) options.failed(error);
          return;
        }
      }
    })().finally(() => { running = false; });
    return task;
  }
  function sample(position: number, playing: boolean, newSession = false, playbackRate = 1) {
    if (disposed || !options.active() || !Number.isFinite(position) || position < 0 || !Number.isFinite(playbackRate) || playbackRate < .25 || playbackRate > 4) return task;
    if (!sessionId || newSession) { sessionId = crypto.randomUUID(); sequence = 0; }
    // A stalled connection cannot grow an unbounded queue. Its oldest body must be retried first.
    if (pending.length < 4) pending.push({ classroom_id: options.classroomId, material_id: options.materialId, playback_session_id: sessionId, sequence: ++sequence, position_seconds: position, playing, playback_rate: playbackRate });
    if (!playing) { sessionId = ""; sequence = 0; }
    return flush();
  }
  return {
    sample, flush,
    hasPending: () => pending.length > 0,
    settle: () => task,
    dispose() { disposed = true; pending = []; return task; },
  };
}
