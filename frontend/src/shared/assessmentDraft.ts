import type { AssessmentAnswer, AssessmentAttempt } from "./types";

/** One serial save stream per student, classroom and activity. */
export function createAssessmentDraftQueue(options: {
  revision: number;
  initial: AssessmentAnswer[];
  active: () => boolean;
  send: (revision: number, answers: AssessmentAnswer[]) => Promise<AssessmentAttempt>;
  saved: (attempt: AssessmentAttempt) => void;
  failed: (cause: unknown) => void;
}) {
  let revision = options.revision, baseline = JSON.stringify(options.initial), latest = options.initial;
  let task: Promise<void> | null = null, failure: unknown;
  function set(answers: AssessmentAnswer[]) { latest = answers.map(answer => ({ ...answer })); }
  function flush(): Promise<void> {
    if (task) return task;
    failure = undefined;
    task = (async () => {
      while (options.active() && JSON.stringify(latest) !== baseline) {
        const answers = latest, serialized = JSON.stringify(answers);
        try {
          const saved = await options.send(revision, answers);
          revision = saved.revision;
          baseline = serialized;
          if (options.active()) options.saved(saved);
          if (saved.locked) return;
        } catch (cause) {
          failure = cause;
          if (options.active()) options.failed(cause);
          return;
        }
      }
    })().finally(() => { task = null; });
    return task;
  }
  return {
    set, flush,
    settle: () => task ?? Promise.resolve(),
    revision: () => revision,
    hasPending: () => JSON.stringify(latest) !== baseline,
    failure: () => failure,
  };
}
