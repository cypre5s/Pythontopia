import { afterEach, describe, expect, it, vi } from "vitest";
import { createVideoProgressQueue } from "../src/shared/videoProgress";

afterEach(() => vi.restoreAllMocks());
describe("video participation evidence delivery", () => {
  it("serializes samples and retries the identical failed packet before later positions", async () => {
    const saved = vi.fn(), failed = vi.fn();
    const send = vi.fn().mockRejectedValueOnce(new Error("offline")).mockResolvedValue({ watched_seconds: 0 });
    const queue = createVideoProgressQueue({ classroomId: 2, materialId: 193, active: () => true, send, saved, failed });
    await queue.sample(0, true, true);
    const bootstrap = send.mock.calls[0]![0];
    expect(failed).toHaveBeenCalledOnce();
    expect(queue.hasPending()).toBe(true);
    await queue.sample(5, true);
    expect(send.mock.calls[1]![0]).toEqual(bootstrap);
    expect(send.mock.calls[2]![0]).toEqual({ ...bootstrap, sequence: 2, position_seconds: 5 });
    expect(saved).toHaveBeenCalledTimes(2);
    expect(queue.hasPending()).toBe(false);
  });
  it("starts a new bootstrap after pause or seek and never claims watched time in a packet", async () => {
    const send = vi.fn().mockResolvedValue({ watched_seconds: 0 });
    const queue = createVideoProgressQueue({ classroomId: 2, materialId: 193, active: () => true, send, saved: vi.fn(), failed: vi.fn() });
    await queue.sample(0, true, true);
    await queue.sample(400, false);
    await queue.sample(400, true, true);
    const [first, boundary, restart] = send.mock.calls.map(call => call[0]);
    expect(boundary).toMatchObject({ playback_session_id: first.playback_session_id, sequence: 2, position_seconds: 400, playing: false });
    expect(restart).toMatchObject({ sequence: 1, position_seconds: 400, playing: true });
    expect(restart.playback_session_id).not.toBe(first.playback_session_id);
    expect(Object.keys(restart).sort()).toEqual(["classroom_id", "material_id", "playback_rate", "playback_session_id", "playing", "position_seconds", "sequence"]);
    expect(restart.playback_rate).toBe(1);
  });
  it("preserves playback rate in retry bodies and starts faster playback with a fresh bootstrap", async () => {
    const send = vi.fn().mockResolvedValue({ watched_seconds: 0 });
    const queue = createVideoProgressQueue({ classroomId: 2, materialId: 193, active: () => true, send, saved: vi.fn(), failed: vi.fn() });
    await queue.sample(0, true, true, 1);
    await queue.sample(5, false, false, 1);
    await queue.sample(5, true, true, 2);
    expect(send.mock.calls[1]![0]).toMatchObject({ playback_rate: 1, playing: false, sequence: 2 });
    expect(send.mock.calls[2]![0]).toMatchObject({ playback_rate: 2, playing: true, sequence: 1 });
    expect(send.mock.calls[2]![0].playback_session_id).not.toBe(send.mock.calls[0]![0].playback_session_id);
    await queue.sample(10, true, false, Infinity);
    await queue.sample(10, true, false, 8);
    expect(send).toHaveBeenCalledTimes(3);
  });
  it("holds a single request in flight and ignores its late response after disposal", async () => {
    let finish!: (value: { watched_seconds: number }) => void;
    const send = vi.fn(() => new Promise<{ watched_seconds: number }>(resolve => { finish = resolve; }));
    const saved = vi.fn(), failed = vi.fn();
    const queue = createVideoProgressQueue({ classroomId: 2, materialId: 193, active: () => true, send, saved, failed });
    void queue.sample(0, true, true);
    void queue.sample(5, true);
    expect(send).toHaveBeenCalledOnce();
    const settled = queue.dispose();
    finish({ watched_seconds: 5 });
    await settled;
    expect(saved).not.toHaveBeenCalled();
    expect(send).toHaveBeenCalledOnce();
  });
  it("never sends queued evidence after its account or classroom becomes inactive", async () => {
    let active = true;
    const send = vi.fn().mockRejectedValueOnce(new Error("offline"));
    const queue = createVideoProgressQueue({ classroomId: 2, materialId: 193, active: () => active, send, saved: vi.fn(), failed: vi.fn() });
    await queue.sample(0, true, true);
    active = false;
    await queue.flush();
    await queue.sample(100, true);
    expect(send).toHaveBeenCalledOnce();
  });
});
