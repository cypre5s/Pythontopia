import { describe, expect, it } from "vitest";
import { createAssessmentDraftQueue } from "../src/shared/assessmentDraft";
import type { AssessmentAttempt } from "../src/shared/types";
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(done => { resolve = done; }); return { promise, resolve }; }
const saved = (revision: number) => ({ revision, locked: false } as AssessmentAttempt);
describe("assessment draft save ordering", () => {
  it("serializes revisions and keeps edits made during a pending save", async () => {
    const first = deferred<AssessmentAttempt>(), calls: { revision: number; answer: unknown }[] = [];
    const queue = createAssessmentDraftQueue({ revision: 0, initial: [], active: () => true,
      send: async (revision, answers) => { calls.push({ revision, answer: answers[0]?.answer }); return calls.length === 1 ? first.promise : saved(2); }, saved: () => undefined, failed: () => undefined });
    queue.set([{ question_id: "q", answer: false }]);
    const saving = queue.flush();
    queue.set([{ question_id: "q", answer: true }]);
    expect(calls).toEqual([{ revision: 0, answer: false }]);
    first.resolve(saved(1));
    await saving;
    expect(calls).toEqual([{ revision: 0, answer: false }, { revision: 1, answer: true }]);
    expect(queue.hasPending()).toBe(false);
  });
  it("keeps failed evidence for retry and stops sends after owner change", async () => {
    let active = true, attempts = 0;
    const queue = createAssessmentDraftQueue({ revision: 4, initial: [], active: () => active,
      send: async () => { attempts++; if (attempts === 1) throw new Error("offline"); return saved(5); }, saved: () => undefined, failed: () => undefined });
    queue.set([{ question_id: "q", answer: "draft" }]); await queue.flush();
    expect(queue.hasPending()).toBe(true);
    expect(queue.failure()).toBeInstanceOf(Error);
    active = false; await queue.flush(); expect(attempts).toBe(1);
    active = true; await queue.flush(); expect(attempts).toBe(2);
    expect(queue.revision()).toBe(5);
  });
});
