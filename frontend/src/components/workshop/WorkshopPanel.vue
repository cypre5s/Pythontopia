<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useLearningStore } from '@stores/learning';
import { useToastStore } from '@stores/toast';
import { playSfx } from '@/composables/useAudio';
import type { WorkshopCreateRequest, WorkshopResourceType } from '@/api/dto/Workshop';

const RESOURCE_TYPES: { value: WorkshopResourceType; label: string; hint: string }[] = [
  { value: 'HANDOUT', label: '讲义', hint: '段落 + 课件页引用' },
  { value: 'MINDMAP', label: '思维导图', hint: 'Mermaid 文本，浏览器渲染' },
  { value: 'PRACTICE', label: '练习题', hint: '1-3 道题，不含完整答案' },
  { value: 'CODE_LAB', label: '代码案例', hint: '可在 Pyodide 中跑通' },
  { value: 'READING', label: '拓展阅读', hint: '至少 2 个延伸阅读卡片' },
  { value: 'STORYBOARD', label: '动画脚本', hint: '3-5 个分镜，不渲染视频' },
];

const learning = useLearningStore();
const toast = useToastStore();
const router = useRouter();

const selectedType = ref<WorkshopResourceType>('HANDOUT');
const focusHint = ref<string>('');
const submitting = ref<boolean>(false);

const unlockedKcs = computed(() => learning.unlockedCrops.map((crop) => crop.kcCode));
const selectedKcs = ref<string[]>([]);

const canSubmit = computed(() => selectedKcs.value.length > 0 && !submitting.value);

function toggleKc(code: string): void {
  const idx = selectedKcs.value.indexOf(code);
  if (idx === -1) {
    selectedKcs.value = [...selectedKcs.value, code];
  } else {
    selectedKcs.value = selectedKcs.value.filter((entry) => entry !== code);
  }
}

async function submit(): Promise<void> {
  if (selectedKcs.value.length === 0) {
    toast.show('请选择至少一个 KC。', 'error');
    return;
  }
  submitting.value = true;
  try {
    const trimmed = focusHint.value.trim();
    const payload: WorkshopCreateRequest =
      trimmed.length > 0
        ? { kcCodes: selectedKcs.value, type: selectedType.value, focusHint: trimmed }
        : { kcCodes: selectedKcs.value, type: selectedType.value };
    const response = await learning.createResource(payload);
    playSfx('sfx_success');
    playSfx('sfx_coin');
    toast.show(`资源生成成功（runId=${response.runId}）`, 'success');
    await router.push({ path: '/workshop', query: { id: String(response.resourceId) } });
  } catch (error) {
    playSfx('sfx_error');
    const message = error instanceof Error ? error.message : '资源生成失败';
    toast.show(message, 'error');
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <section
    class="panel"
    data-testid="workshop-panel"
  >
    <header>
      <h2>资源工坊</h2>
      <p class="hint">
        资源工坊是教育闭环的"浇水/施肥"环节：在知识田挑选已解锁 KC，选择资源类型，由多智能体合作生成。
        生成结果会带课件页引用，超纲内容会显式标注，不会编造课件来源。
      </p>
    </header>
    <section class="form">
      <label class="block">
        <span class="label">已解锁 KC</span>
        <div
          v-if="unlockedKcs.length === 0"
          class="empty"
        >
          请先去知识田开垦/解锁一些 KC。
        </div>
        <ul
          v-else
          class="kc-list"
          data-testid="kc-list"
        >
          <li
            v-for="code in unlockedKcs"
            :key="code"
          >
            <label class="kc-pill">
              <input
                type="checkbox"
                :checked="selectedKcs.includes(code)"
                @change="toggleKc(code)"
              >
              <span>{{ code }}</span>
            </label>
          </li>
        </ul>
      </label>
      <label class="block">
        <span class="label">资源类型</span>
        <ul
          class="type-list"
          data-testid="type-list"
        >
          <li
            v-for="entry in RESOURCE_TYPES"
            :key="entry.value"
          >
            <label class="type-card">
              <input
                v-model="selectedType"
                type="radio"
                :value="entry.value"
                :data-testid="`type-${entry.value}`"
              >
              <span class="type-label">{{ entry.label }}</span>
              <small>{{ entry.hint }}</small>
            </label>
          </li>
        </ul>
      </label>
      <label class="block">
        <span class="label">学习重点（可选）</span>
        <textarea
          v-model="focusHint"
          maxlength="512"
          rows="2"
          placeholder="例如：我想重点理解 for 循环 break/continue 的区别"
        />
      </label>
      <div class="actions">
        <button
          type="button"
          class="primary"
          :disabled="!canSubmit"
          @click="submit"
        >
          {{ submitting ? '生成中…' : '生成资源' }}
        </button>
        <span
          v-if="learning.resources.phase === 'error' && learning.resources.lastError"
          class="error"
        >
          {{ learning.resources.lastError }}
        </span>
      </div>
    </section>
  </section>
</template>

<style scoped>
.panel {
  background: #fffbe9;
  border: 2px solid #c9a86b;
  border-radius: 10px;
  padding: 16px;
  display: grid;
  gap: 14px;
  color: #2b1c0b;
}
.panel header h2 {
  margin: 0;
}
.hint {
  color: #6e4f1d;
  font-size: 12px;
}
.form {
  display: grid;
  gap: 14px;
}
.block {
  display: grid;
  gap: 6px;
}
.label {
  font-weight: 600;
}
.kc-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 0;
  margin: 0;
  list-style: none;
}
.kc-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: 1px solid #6e4f1d;
  border-radius: 999px;
  cursor: pointer;
  background: #fff7e6;
  font-size: 13px;
}
.type-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 8px;
}
.type-card {
  display: grid;
  padding: 10px;
  border: 1px solid #c9a86b;
  border-radius: 8px;
  background: #fff7e6;
  cursor: pointer;
}
.type-card input {
  margin-bottom: 4px;
}
.type-label {
  font-weight: 600;
}
.actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.primary {
  padding: 8px 14px;
  border: none;
  background: #6e4f1d;
  color: #fff7e6;
  border-radius: 4px;
  font-weight: 600;
  cursor: pointer;
}
.primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
textarea {
  padding: 8px;
  border-radius: 6px;
  border: 1px solid #c9a86b;
  background: #fff7e6;
  resize: vertical;
}
.empty {
  padding: 12px;
  text-align: center;
  background: #f4e4c1;
  border-radius: 6px;
  font-size: 13px;
  color: #6e4f1d;
}
.error {
  color: #b3402a;
  font-size: 12px;
}
</style>
