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
import AttachmentPills, {
  type AttachmentDraft,
} from '@/components/workshop/AttachmentPills.vue';
import {
  useCoursewareMention,
  type MentionItem,
} from '@/composables/useCoursewareMention';
import { useSlashCommand, type SlashCommand } from '@/composables/useSlashCommand';
import type { IntentKind } from '@/api/intent';
import {
  ALLOWED_UPLOAD_EXTS,
  extOf,
  uploadFile,
  uploadRejectionReason,
  type UploadAttachmentRef,
} from '@/api/upload';

export interface ComposerSubmitPayload {
  text: string;
  manualKcCodes: string[];
  contextHint: string;
  forcedIntent: IntentKind | null;
  /**
   * 本轮随发的已就绪上传附件（仿 Claude Code 上传，契约 §3.5）。默认空数组，向后兼容。
   * 仅含上传成功（status==='ready'、带后端 upload id）的文件；上传中 / 失败的不进 payload。
   */
  attachments: UploadAttachmentRef[];
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
const fileInputRef = ref<HTMLInputElement | null>(null);

const mention = useCoursewareMention(toRef(props, 'mentionItems'));
const slash = useSlashCommand(mention.text);

const inputText = mention.text;
const mentionVisible = mention.menuVisible;
const slashVisible = slash.menuVisible;
const selectedRefs = mention.selected;
const forcedCommand = slash.forcedCommand;

// ============ 上传附件状态机（仿 Claude Code：📎 / 拖拽 / 粘贴 → 多文件 pill） ============
// 每个文件一枚 pill：uploading（解析中）→ ready（行/字）/ error（错误 + 重试）。
// 原始 File 句柄存在非响应式 Map，供失败重试复用（不必让 File 进入响应式系统）。
const attachments = ref<AttachmentDraft[]>([]);
const fileHandles = new Map<string, File>();
const dragActive = ref(false);
let dragDepth = 0;
let localSeq = 0;

const uploadAccept = ALLOWED_UPLOAD_EXTS.join(',');
const hasUploading = computed<boolean>(() =>
  attachments.value.some((a) => a.status === 'uploading'),
);

// exactOptionalPropertyTypes 下不能把可选 prop（boolean|undefined）直接绑到原生属性，
// 统一收敛成严格 boolean computed（同 DialogModal/TutorPanel 不直绑可选 prop 的范式）。
const isDisabled = computed<boolean>(() => props.disabled === true);
const isBusy = computed<boolean>(() => props.busy === true);
const placeholderText = computed<string>(() => props.placeholder ?? DEFAULT_PLACEHOLDER);
// 仍有附件在上传时禁止发送，避免就绪前的附件被遗漏（与 Claude Code 一致）。
const canSubmit = computed<boolean>(
  () =>
    !isDisabled.value &&
    !isBusy.value &&
    !hasUploading.value &&
    inputText.value.trim().length > 0,
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

// ============ 上传：选择 / 拖拽 / 粘贴 → 状态机 ============
function genLocalId(): string {
  localSeq += 1;
  return `att-${Date.now()}-${localSeq}`;
}

function patchAttachment(localId: string, patch: Partial<AttachmentDraft>): void {
  attachments.value = attachments.value.map((a) =>
    a.localId === localId ? { ...a, ...patch } : a,
  );
}

/** 单个文件上传：先前端预校验（白名单 / 5MB），再 POST；按结果推进状态机。 */
async function runUpload(localId: string, file: File): Promise<void> {
  const reason = uploadRejectionReason(file);
  if (reason) {
    patchAttachment(localId, { status: 'error', error: reason });
    return;
  }
  patchAttachment(localId, { status: 'uploading', error: undefined });
  try {
    const dto = await uploadFile(file);
    if (dto.status === 'FAILED') {
      patchAttachment(localId, { status: 'error', error: dto.error ?? '解析失败' });
      return;
    }
    patchAttachment(localId, {
      status: 'ready',
      uploadId: dto.id,
      lineCount: dto.lineCount,
      charCount: dto.charCount,
      error: undefined,
    });
  } catch (err) {
    const message = err instanceof Error ? err.message : '上传失败';
    patchAttachment(localId, { status: 'error', error: message });
  }
}

function addFiles(files: FileList | File[] | null | undefined): void {
  if (!files) return;
  for (const file of Array.from(files)) {
    const localId = genLocalId();
    fileHandles.set(localId, file);
    attachments.value = [
      ...attachments.value,
      {
        localId,
        filename: file.name,
        ext: extOf(file.name),
        byteSize: file.size,
        status: 'uploading',
      },
    ];
    void runUpload(localId, file);
  }
}

function openFilePicker(): void {
  fileInputRef.value?.click();
}

function onFilePick(event: Event): void {
  const input = event.target as HTMLInputElement;
  addFiles(input.files);
  // 清空 value，使再次选择同名文件也能触发 change。
  input.value = '';
}

function onRemoveAttachment(localId: string): void {
  fileHandles.delete(localId);
  attachments.value = attachments.value.filter((a) => a.localId !== localId);
}

function onRetryAttachment(localId: string): void {
  const file = fileHandles.get(localId);
  if (!file) return;
  void runUpload(localId, file);
}

// 仅当拖拽中包含「文件」时才激活覆盖层（拖选文本不应触发）。
function isFileDrag(event: DragEvent): boolean {
  const types = event.dataTransfer?.types;
  return types ? Array.from(types).includes('Files') : false;
}

function onDragEnter(event: DragEvent): void {
  if (!isFileDrag(event)) return;
  event.preventDefault();
  dragDepth += 1;
  dragActive.value = true;
}

function onDragOver(event: DragEvent): void {
  if (!isFileDrag(event)) return;
  // 必须 preventDefault 才能允许 drop。
  event.preventDefault();
}

function onDragLeave(event: DragEvent): void {
  if (!isFileDrag(event)) return;
  dragDepth -= 1;
  if (dragDepth <= 0) {
    dragDepth = 0;
    dragActive.value = false;
  }
}

function onDrop(event: DragEvent): void {
  if (!isFileDrag(event)) return;
  event.preventDefault();
  dragDepth = 0;
  dragActive.value = false;
  addFiles(event.dataTransfer?.files);
}

function onPaste(event: ClipboardEvent): void {
  const files = event.clipboardData?.files;
  if (files && files.length > 0) {
    event.preventDefault();
    addFiles(files);
  }
}

/** 收集已就绪附件为提交载荷（上传中 / 失败的不进 payload）。 */
function readyAttachments(): UploadAttachmentRef[] {
  const out: UploadAttachmentRef[] = [];
  for (const a of attachments.value) {
    if (a.status === 'ready' && a.uploadId !== undefined) {
      out.push({
        id: a.uploadId,
        filename: a.filename,
        ext: a.ext,
        charCount: a.charCount ?? 0,
      });
    }
  }
  return out;
}

function submit(): void {
  const text = inputText.value.trim();
  if (!text || props.disabled || props.busy) return;
  // 还有附件在上传：不发送，避免就绪前的附件被遗漏。
  if (hasUploading.value) return;
  emit('submit', {
    text,
    manualKcCodes: [...mention.selectedKcCodes.value],
    contextHint: mention.contextHint.value,
    forcedIntent: slash.forcedIntent.value,
    attachments: readyAttachments(),
  });
  mention.reset();
  slash.reset();
  attachments.value = [];
  fileHandles.clear();
}

defineExpose({ focus: focusInput });
</script>

<template>
  <div
    class="composer"
    data-testid="chat-composer"
    @dragenter="onDragEnter"
    @dragover="onDragOver"
    @dragleave="onDragLeave"
    @drop="onDrop"
  >
    <!-- 拖拽上传覆盖层（仅拖入文件时出现） -->
    <div
      v-if="dragActive"
      class="composer__dropzone"
      data-testid="composer-dropzone"
    >
      <span class="composer__dropzone-text">📎 松开上传文件</span>
    </div>

    <!-- 隐藏文件选择器（📎 触发，支持多选） -->
    <input
      ref="fileInputRef"
      type="file"
      multiple
      :accept="uploadAccept"
      class="composer__file-input"
      data-testid="composer-file-input"
      @change="onFilePick"
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

    <!-- 附件 pill 列表（上传中 spinner / 就绪 行·字 / 失败 重试） -->
    <AttachmentPills
      :attachments="attachments"
      @remove="onRemoveAttachment"
      @retry="onRetryAttachment"
    />

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
      <button
        type="button"
        class="composer__attach"
        data-testid="composer-attach"
        title="上传文件（也可拖拽 / 粘贴）"
        :disabled="isDisabled"
        @click="openFilePicker"
      >
        📎
      </button>
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
        @paste="onPaste"
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
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-family: var(--pyt-font-pixel);
}
/* 拖拽上传覆盖层 */
.composer__dropzone {
  position: absolute;
  inset: -6px;
  z-index: 5;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 243, 196, 0.92);
  border: 3px dashed #8c6a23;
  border-radius: 8px;
  pointer-events: none;
}
.composer__dropzone-text {
  font-size: 14px;
  font-weight: 700;
  color: #8c6a23;
}
.composer__file-input {
  display: none;
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
.composer__attach {
  flex: 0 0 auto;
  font-family: inherit;
  font-size: 16px;
  line-height: 1;
  padding: 8px 10px;
  background: #fff8df;
  color: #2a1a08;
  border: 3px solid #8c6a23;
  border-radius: 6px;
  box-shadow: 0 3px 0 #8c6a23;
  cursor: pointer;
  transition: transform 80ms, box-shadow 80ms;
}
.composer__attach:hover:not(:disabled) {
  background: #ffe098;
}
.composer__attach:active:not(:disabled) {
  transform: translateY(2px);
  box-shadow: 0 1px 0 #8c6a23;
}
.composer__attach:disabled {
  opacity: 0.55;
  cursor: not-allowed;
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
