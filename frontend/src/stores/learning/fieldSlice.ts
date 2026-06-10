import { computed, ref } from 'vue';
import { bootstrapCurriculum, fetchMastery } from '@/api/field';
import type { CurriculumBootstrapResult } from '@/api/dto/CurriculumBootstrapResult';
import type { MasterySnapshotDto } from '@/api/dto/MasterySnapshotDto';

export interface FieldState {
  masterySnapshot: MasterySnapshotDto[];
  activeKcCode: string | null;
  loading: boolean;
  bootstrapped: boolean;
  hydrated: boolean;
  lastSignature: string | null;
}

/** KC 掌握度快照（「测」环）切片。 */
export function createFieldSlice() {
  const field = ref<FieldState>({
    masterySnapshot: [],
    activeKcCode: null,
    loading: false,
    bootstrapped: false,
    hydrated: false,
    lastSignature: null,
  });

  const activeKcMastery = computed<MasterySnapshotDto | null>(() => {
    if (!field.value.activeKcCode) {
      return null;
    }
    const code = field.value.activeKcCode;
    return field.value.masterySnapshot.find((entry) => entry.kcCode === code) ?? null;
  });

  async function loadField(): Promise<void> {
    field.value.loading = true;
    try {
      const mastery = await fetchMastery();
      field.value.masterySnapshot = mastery;
      field.value.bootstrapped = mastery.length > 0;
      field.value.hydrated = true;
    } finally {
      field.value.loading = false;
    }
  }

  async function bootstrapField(): Promise<CurriculumBootstrapResult> {
    const result = await bootstrapCurriculum();
    field.value.lastSignature = result.signature;
    field.value.bootstrapped = true;
    await loadField();
    return result;
  }

  function selectKc(kcCode: string | null): void {
    field.value.activeKcCode = kcCode;
  }

  /** `selectKc` 的兼容别名：部分老组件按 `setFieldActiveKc` 命名调用。 */
  function setFieldActiveKc(kcCode: string | null): void {
    field.value.activeKcCode = kcCode;
  }

  async function refreshAfterEvent(kcCode: string): Promise<void> {
    const mastery = await fetchMastery();
    field.value.masterySnapshot = mastery;
    field.value.hydrated = true;
    if (kcCode) {
      field.value.activeKcCode = kcCode;
    }
  }

  /**
   * KC mastery 自然衰减 — 模拟学习遗忘曲线。
   * sleep 推进 1 天时调用，每个已掌握的 KC mastery 减少 decayAmount。
   * 下限 clamp 到 0，不会变成负数。
   */
  function applyMasteryDecay(decayAmount: number): void {
    if (decayAmount <= 0) return;
    field.value.masterySnapshot = field.value.masterySnapshot.map((entry) => ({
      ...entry,
      masteryScore: Math.max(0, entry.masteryScore - decayAmount),
    }));
  }

  return {
    field,
    activeKcMastery,
    loadField,
    bootstrapField,
    selectKc,
    setFieldActiveKc,
    refreshAfterEvent,
    applyMasteryDecay,
  };
}
