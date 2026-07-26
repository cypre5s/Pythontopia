import { ref } from 'vue';
import {
  openBugMineSession,
  getBugMineErrorBook,
  getBugMineEchoes,
  getBugMineSession,
  openBugMineEcho,
  revealBugMineCard,
  retryBugMineSubmit,
  variantBugMineSubmit,
} from '@/api/bugMine';
import { getPublicProblem } from '@/api/problems';
import type {
  BugMineEchoCandidateDto,
  BugMineErrorBookItemDto,
  BugMineSessionDto,
} from '@/api/dto/BugMine';
import type { PublicProblemDto, SubmissionDto } from '@/api/dto/Submission';

export interface BugMineState {
  session: BugMineSessionDto | null;
  /** 当前会话对应的完整题干（按 session.problemId 独立拉取，与训练页 training.problem 解耦）。 */
  problem: PublicProblemDto | null;
  problemLoading: boolean;
  problemError: string | null;
  errorBook: BugMineErrorBookItemDto[];
  errorBookLoading: boolean;
  errorBookError: string | null;
  echoes: BugMineEchoCandidateDto[];
  echoesLoading: boolean;
  echoesError: string | null;
  pendingVariantSessionId: number | null;
  /** S1：训练场 → 错题矿洞入口的私有 id 经 store 传递（不写进 URL）。 */
  pendingEntrySubmissionId: number | null;
  pendingEntrySessionId: number | null;
  loading: boolean;
  lastError: string | null;
  lastRetry: SubmissionDto | null;
  lastVariant: SubmissionDto | null;
}

export interface BugMineSliceDeps {
  /** 变体/重做通过后刷新今日任务（失败提交触发的矿洞任务由后端校验 evidence）。 */
  loadTodayQuests: () => Promise<void>;
}

/** Bug 矿洞（失败提交 → 调试副本支线）切片。 */
export function createBugMineSlice(deps: BugMineSliceDeps) {
  const bugMine = ref<BugMineState>({
    session: null,
    problem: null,
    problemLoading: false,
    problemError: null,
    errorBook: [],
    errorBookLoading: false,
    errorBookError: null,
    echoes: [],
    echoesLoading: false,
    echoesError: null,
    pendingVariantSessionId: null,
    pendingEntrySubmissionId: null,
    pendingEntrySessionId: null,
    loading: false,
    lastError: null,
    lastRetry: null,
    lastVariant: null,
  });

  /**
   * 错题本请求序号：页面/面板/入矿动作可能并发发起多次拉取，
   * 只允许「最后一次发起」的响应落地，避免旧响应覆盖掉包含新建会话的那次。
   */
  let errorBookRequestSeq = 0;

  async function loadBugMineErrorBook(): Promise<void> {
    const seq = (errorBookRequestSeq += 1);
    bugMine.value.errorBookLoading = true;
    bugMine.value.errorBookError = null;
    try {
      const items = await getBugMineErrorBook();
      if (seq !== errorBookRequestSeq) return;
      bugMine.value.errorBook = items;
    } catch (error) {
      if (seq !== errorBookRequestSeq) return;
      bugMine.value.errorBookError = error instanceof Error ? error.message : String(error);
    } finally {
      if (seq === errorBookRequestSeq) {
        bugMine.value.errorBookLoading = false;
      }
    }
  }

  async function loadBugMineEchoes(): Promise<void> {
    bugMine.value.echoesLoading = true;
    bugMine.value.echoesError = null;
    try {
      bugMine.value.echoes = await getBugMineEchoes();
    } catch (error) {
      bugMine.value.echoesError = error instanceof Error ? error.message : String(error);
    } finally {
      bugMine.value.echoesLoading = false;
    }
  }

  async function openBugMine(submissionId: number): Promise<BugMineSessionDto | null> {
    bugMine.value.loading = true;
    bugMine.value.lastError = null;
    try {
      const session = await openBugMineSession({ submissionId });
      bugMine.value.session = session;
      await loadBugMineErrorBook();
      return session;
    } catch (error) {
      bugMine.value.lastError = error instanceof Error ? error.message : String(error);
      return null;
    } finally {
      bugMine.value.loading = false;
    }
  }

  async function loadBugMineProblem(problemId: number): Promise<void> {
    // 矿洞会话的「题干上下文中心」：拉取完整 PublicProblemDto（含样例），
    // 失败不阻断会话调试，仅记录 problemError 由面板降级展示。
    bugMine.value.problemLoading = true;
    bugMine.value.problemError = null;
    try {
      bugMine.value.problem = await getPublicProblem(problemId);
    } catch (error) {
      bugMine.value.problemError = error instanceof Error ? error.message : String(error);
      bugMine.value.problem = null;
    } finally {
      bugMine.value.problemLoading = false;
    }
  }

  async function openBugMineEchoByKc(kcCode: string): Promise<BugMineSessionDto | null> {
    // 幂等前端半边：该 KC 已有 OPEN 会话就直接切过去，不再重复入矿。
    // （后端 openEcho 每次调用都会新插 submission + session，FSRS 到期状态又不随入矿改变，
    //   再点一次就是错题本里的第二条同 KC 条目。）
    const existing = bugMine.value.errorBook.find(
      (item) => item.kcCode === kcCode && item.status === 'OPEN',
    );
    if (existing) {
      await loadBugMine(existing.sessionId);
      return bugMine.value.session;
    }
    bugMine.value.loading = true;
    bugMine.value.lastError = null;
    try {
      const session = await openBugMineEcho({ kcCode });
      bugMine.value.session = session;
      await loadBugMineErrorBook();
      await loadBugMineEchoes();
      return session;
    } catch (error) {
      bugMine.value.lastError = error instanceof Error ? error.message : String(error);
      return null;
    } finally {
      bugMine.value.loading = false;
    }
  }

  async function loadBugMine(sessionId: number): Promise<void> {
    bugMine.value.loading = true;
    bugMine.value.lastError = null;
    try {
      bugMine.value.session = await getBugMineSession(sessionId);
    } catch (error) {
      bugMine.value.lastError = error instanceof Error ? error.message : String(error);
    } finally {
      bugMine.value.loading = false;
    }
  }

  async function revealBugMineCardById(cardId: number): Promise<void> {
    if (!bugMine.value.session) return;
    const sessionId = bugMine.value.session.id;
    await revealBugMineCard(sessionId, cardId);
    bugMine.value.session = await getBugMineSession(sessionId);
    await loadBugMineErrorBook();
  }

  function setPendingVariantSession(sessionId: number | null): void {
    bugMine.value.pendingVariantSessionId = sessionId;
  }

  /**
   * S1：训练场 → 错题矿洞「入口」私有 id 改走 store（不写进 URL）。
   * 设置后 BugMinePage 监听并 consume，避免地址栏暴露/篡改 submissionId / sessionId。
   */
  function setPendingBugMineEntry(entry: {
    submissionId?: number | null;
    sessionId?: number | null;
  }): void {
    bugMine.value.pendingEntrySubmissionId = entry.submissionId ?? null;
    bugMine.value.pendingEntrySessionId = entry.sessionId ?? null;
  }

  /** consume 并清空入口私有 id（读后即清，避免残留触发二次进入）。 */
  function consumePendingBugMineEntry(): {
    submissionId: number | null;
    sessionId: number | null;
  } {
    const out = {
      submissionId: bugMine.value.pendingEntrySubmissionId,
      sessionId: bugMine.value.pendingEntrySessionId,
    };
    bugMine.value.pendingEntrySubmissionId = null;
    bugMine.value.pendingEntrySessionId = null;
    return out;
  }

  async function submitBugMineVariant(
    code: string,
    sessionId?: number,
  ): Promise<SubmissionDto | null> {
    const targetSessionId =
      sessionId ?? bugMine.value.pendingVariantSessionId ?? bugMine.value.session?.id;
    if (targetSessionId == null) return null;
    bugMine.value.loading = true;
    bugMine.value.lastError = null;
    try {
      const result = await variantBugMineSubmit(targetSessionId, { code });
      bugMine.value.lastVariant = result;
      bugMine.value.session = await getBugMineSession(targetSessionId);
      await loadBugMineErrorBook();
      if (result.status === 'PASSED') {
        bugMine.value.pendingVariantSessionId = null;
        await deps.loadTodayQuests();
      }
      return result;
    } catch (error) {
      bugMine.value.lastError = error instanceof Error ? error.message : String(error);
      return null;
    } finally {
      bugMine.value.loading = false;
    }
  }

  async function retryBugMine(code: string): Promise<SubmissionDto | null> {
    if (!bugMine.value.session) return null;
    bugMine.value.loading = true;
    bugMine.value.lastError = null;
    try {
      const result = await retryBugMineSubmit(bugMine.value.session.id, { code });
      bugMine.value.lastRetry = result;
      const session = await getBugMineSession(bugMine.value.session.id);
      bugMine.value.session = session;
      await loadBugMineErrorBook();
      if (result.status === 'PASSED') {
        await deps.loadTodayQuests();
      }
      return result;
    } catch (error) {
      bugMine.value.lastError = error instanceof Error ? error.message : String(error);
      return null;
    } finally {
      bugMine.value.loading = false;
    }
  }

  return {
    bugMine,
    openBugMine,
    loadBugMineProblem,
    loadBugMineErrorBook,
    loadBugMineEchoes,
    openBugMineEchoByKc,
    loadBugMine,
    revealBugMineCardById,
    setPendingVariantSession,
    setPendingBugMineEntry,
    consumePendingBugMineEntry,
    submitBugMineVariant,
    retryBugMine,
  };
}
