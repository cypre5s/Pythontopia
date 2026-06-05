<script setup lang="ts">
/**
 * 百晓工坊统一输入框：一个 textarea 同时承载
 *   - `@`  引用 KC / 章节（复用 useCoursewareMention + CoursewareMentionMenu）；
 *   - `/`  选工具（useSlashCommand + SlashCommandMenu），命令以「工具徽标」呈现；
 *   - 提交时 emit { text, manualKcCodes, contextHint, forcedIntent }，由父级
 *     WorkshopChat 决定：forcedIntent 为空 → 调 /api/intent/route 自动判定；
 *     否则直接用该工具，再分派到导师 / 工坊 SSE 管线。
 *
 * 本组件只负责输入与候选交互，不含任何网络/分派逻辑。
 */
import { computed, nextTick, ref, toRef } from 'vue';
import CoursewareMentionMenu from '@/components/tutor/CoursewareMentionMenu.vue';
import SlashCommandMenu from '@/components/workshop/SlashCommandMenu.vue';
import {
  useCoursewareMention,
  type MentionItem,
} from '@/composables/useCoursewareMention';
import { useSlashCommand, type SlashCommand } from '@/composables/useSlashCommand';
import type { IntentKind } from '@/api/intent';

export interface ComposerSubmitPayload {
  text: string;
  manualKcCodes: string[];
  contextHint: string;
  forcedIntent: IntentKind | null;
}

const DEFAULT_PLACEHOLDER = '说说你想学什么…';

const props = withDefaults(
  defineProps<{
    mentionItems: MentionItem[];
    placeholder?: string;
    disabled?: boolean;
    busy?: boolean;
  }>(),
  {
    placeholder: DEFAULT_PLACEHOLDER,
    disabled: false,
    busy: false,
  },
);

const emit = defineEmits<{
  (event: 'submit', payload: ComposerSubmitPayload): void;
}>();

const inputRef = ref<HTMLTextAreaElement | null>(null);

const mention = useCoursewareMention(toRef(props, 'mentionItems'));
const slash = useSlashCommand(mention.text);

const inputText = mention.text;
const mentionVisible = mention.menuVisible;
const slashVisible = slash.menuVisible;
const selectedRefs = mention.selected;
const forcedCommand = slash.forcedCommand;

// exactOptionalPropertyTypes 下不能把可选 prop（boolean|undefined）直接绑到原生属性，
// 统一收敛成严格 boolean computed（同 DialogModal/TutorPanel 不直绑可选 prop 的范式）。
const isDisabled = computed<boolean>(() => props.disabled === true);
const isBusy = computed<boolean>(() => props.busy === true);
const placeholderText = computed<string>(() => props.placeholder ?? DEFAULT_PLACEHOLDER);
const canSubmit = computed<boolean>(
  () => !isDisabled.value && !isBusy.value && inputText.value.trim().length > 0,
);

interface MenuController {
  menuVisible: { value: boolean };
  moveActive: (direction: number) => void;
  commitActive: () => boolean;
  close: () => void;
}

function focusInput(): void {
  void nextTick(() => inputRef.value?.focus());
}

function onInput(event: Event): void {
  const value = (event.target as HTMLTextAreaElement).value;
  mention.updateFromText(value);
  slash.updateFromText(value);
}

function handleMenuKey(event: KeyboardEvent, ctrl: MenuController): boolean {
  if (event.key === 'ArrowDown') {
    event.preventDefault();
    ctrl.moveActive(1);
    return true;
  }
  if (event.key === 'ArrowUp') {
    event.preventDefault();
    ctrl.moveActive(-1);
    return true;
  }
  if (event.key === 'Enter' && !event.shiftKey) {
    if (ctrl.commitActive()) {
      event.preventDefault();
      focusInput();
      return true;
    }
  }
  if (event.key === 'Escape') {
    event.preventDefault();
    ctrl.close();
    return true;
  }
  return false;
}

function onKeydown(event: KeyboardEvent): void {
  if (slashVisible.value && handleMenuKey(event, slash)) return;
  if (mentionVisible.value && handleMenuKey(event, mention)) return;
  // 菜单未开时：Enter 发送，Shift+Enter 换行，Ctrl/Cmd+Enter 也发送。
  const menuOpen = slashVisible.value || mentionVisible.value;
  if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
    event.preventDefault();
    submit();
    return;
  }
  if (event.key === 'Enter' && !event.shiftKey && !menuOpen) {
    event.preventDefault();
    submit();
  }
}

function onSelectMention(item: MentionItem): void {
  mention.selectItem(item);
  focusInput();
}

function onSelectSlash(cmd: SlashCommand): void {
  slash.selectCommand(cmd);
  focusInput();
}

function submit(): void {
  const text = inputText.value.trim();
  if (!text || props.disabled || props.busy) return;
  emit('submit', {
    text,
    manualKcCodes: [...mention.selectedKcCodes.value],
    contextHint: mention.contextHint.value,
    forcedIntent: slash.forcedIntent.value,
  });
  mention.reset();
  slash.reset();
}

defineExpose({ focus: focusInput });
</script>

<template>
  <div
    class="composer"
    data-testid="chat-composer"
  >
    <!-- 已选引用 + 强制工具徽标 -->
    <div
      v-if="selectedRefs.length > 0 || forcedCommand"
      class="composer__chips"
    >
      <button
        v-if="forcedCommand"
        type="button"
        class="tool-pill"
        :title="`取消强制「${forcedCommand.label}」，恢复自动判定`"
        data-testid="composer-tool-pill"
        @click="slash.clearForced()"
      >
        <span class="tool-pill__icon">{{ forcedCommand.icon }}</span>
        <span class="tool-pill__label">{{ forcedCommand.label }}</span>
        <span
          class="tool-pill__x"
          aria-hidden="true"
        >×</span>
      </button>
      <button
        v-for="ref_ in selectedRefs"
        :key="ref_.token"
        type="button"
        class="ref-chip"
        :class="`ref-chip--${ref_.kind}`"
        :title="`移除引用 ${ref_.label}`"
        @click="mention.removeRef(ref_.token)"
      >
        <span class="ref-chip__label">{{ ref_.label }}</span>
        <span
          class="ref-chip__x"
          aria-hidden="true"
        >×</span>
      </button>
    </div>

    <div class="composer__input">
      <SlashCommandMenu
        :visible="slashVisible"
        :groups="slash.groups.value"
        :active-index="slash.activeIndex.value"
        @select="onSelectSlash"
      />
      <CoursewareMentionMenu
        :visible="mentionVisible"
        :groups="mention.groups.value"
        :active-index="mention.activeIndex.value"
        @select="onSelectMention"
      />
      <textarea
        ref="inputRef"
        :value="inputText"
        class="composer__textarea"
        data-testid="composer-input"
        :placeholder="placeholderText"
        rows="2"
        :disabled="isDisabled"
        @input="onInput"
        @keydown="onKeydown"
      />
      <button
        type="button"
        class="composer__submit"
        data-testid="composer-submit"
        :disabled="!canSubmit"
        @click="submit"
      >
        {{ isBusy ? '处理中…' : '发送' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.composer {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-family: var(--pyt-font-pixel);
}
.composer__chips {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}
.tool-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  font-family: inherit;
  font-size: 11px;
  font-weight: 700;
  border-radius: 999px;
  cursor: pointer;
  border: 2px solid #8c6a23;
  background: #f7d774;
  color: #2a1a08;
  box-shadow: 0 2px 0 #8c6a23;
}
.tool-pill:hover {
  background: #fde48a;
}
.tool-pill__icon {
  color: #8c6a23;
}
.ref-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 6px 2px 8px;
  font-family: inherit;
  font-size: 11px;
  border-radius: 999px;
  cursor: pointer;
  border: 1px solid var(--pyt-color-text-hint);
  background: #ffe098;
  color: var(--pyt-color-text-primary);
  transition: background 120ms;
}
.ref-chip:hover {
  background: #f7d774;
}
.ref-chip--chapter {
  background: #d6e6f4;
  border-color: var(--pyt-color-info);
  color: #1b4a6b;
}
.ref-chip--kc {
  background: #d6ead1;
  border-color: #4d6e1d;
  color: #2d5b1b;
}
.ref-chip__label {
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ref-chip__x,
.tool-pill__x {
  font-weight: 700;
  font-size: 13px;
  line-height: 1;
}
.composer__input {
  position: relative;
  display: flex;
  gap: 8px;
  align-items: flex-end;
}
.composer__textarea {
  flex: 1 1 auto;
  padding: 8px 10px;
  background: var(--pyt-color-bg-elevated);
  border: 3px solid var(--pyt-color-border);
  border-radius: 6px;
  font-family: inherit;
  font-size: 14px;
  color: #3a2a14;
  resize: none;
}
.composer__textarea:focus {
  outline: none;
  border-color: var(--pyt-color-text-hint);
}
.composer__textarea:disabled {
  opacity: 0.6;
}
.composer__submit {
  font-family: inherit;
  font-size: 14px;
  font-weight: 700;
  padding: 8px 18px;
  background: #f7d774;
  color: #2a1a08;
  border: 3px solid #8c6a23;
  border-radius: 6px;
  box-shadow: 0 3px 0 #8c6a23;
  cursor: pointer;
  transition: transform 80ms, box-shadow 80ms;
  white-space: nowrap;
}
.composer__submit:hover:not(:disabled) {
  background: #fde48a;
}
.composer__submit:active:not(:disabled) {
  transform: translateY(2px);
  box-shadow: 0 1px 0 #8c6a23;
}
.composer__submit:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
</style>
