<script setup lang="ts">
/**
 * 百晓工坊统一输入框：一个 textarea 同时承载
 *   - `@`  引用 KC / 章节（复用 useCoursewareMention + CoursewareMentionMenu）；
 *   - `/`  选工具（useSlashCommand + SlashCommandMenu），命令以「工具徽标」呈现；
 *   - `+`  打开 6 个能力组（普通学生不用记内部工具名）；
 *   - 提交时 emit { text, manualKcCodes, contextHint, forcedIntent, source, capabilityNamespace }，由父级
 *     WorkshopChat 决定：forcedIntent 为空 → 调 /api/intent/route 自动判定；
 *     `/` 强制具体工具；`+` 仅作为能力组 hint，仍由路由器在组内选工具。
 *
 * 本组件只负责输入与候选交互，不含任何网络/分派逻辑。
 */
import { computed, nextTick, ref, toRef, watch } from 'vue';
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
  WORKSHOP_CAPABILITIES,
  type WorkshopCapability,
} from '@/components/workshop/capabilities';
import type {
  WorkshopCapabilityNamespace,
  WorkshopToolRouteSource,
} from '@/components/workshop/toolRouting';
import {
  ALLOWED_UPLOAD_EXTS,
  extOf,
  MAX_UPLOAD_BYTES,
  uploadFile,
  uploadRejectionReason,
  type UploadAttachmentRef,
} from '@/api/upload';
import { transcribeAudio } from '@/api/media';
import { useAudioRecorder } from '@/composables/useAudioRecorder';
import { useVoiceInput } from '@/composables/useVoiceInput';

export interface ComposerSubmitPayload {
  text: string;
  manualKcCodes: string[];
  contextHint: string;
  forcedIntent: IntentKind | null;
  source?: WorkshopToolRouteSource;
  /** `+` 能力面板只传分组 hint，不限制学生只能用 primary intent。 */
  capabilityNamespace?: WorkshopCapabilityNamespace;
  /**
   * 本轮随发的已就绪上传附件（仿 Claude Code 上传，契约 §3.5）。默认空数组，向后兼容。
   * 仅含上传成功（status==='ready'、带后端 upload id）的文件；上传中 / 失败的不进 payload。
   */
  attachments: UploadAttachmentRef[];
  /** 本轮直接交给导师视觉模型的图片 Data URL（不经文档上传/解析接口）。 */
  images?: string[];
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
const capabilityMenuOpen = ref(false);
const selectedCapability = ref<WorkshopCapability | null>(null);

// ============ 上传附件状态机（仿 Claude Code：📎 / 拖拽 / 粘贴 → 多文件 pill） ============
// 每个文件一枚 pill：uploading（解析中）→ ready（行/字）/ error（错误 + 重试）。
// 原始 File 句柄存在非响应式 Map，供失败重试复用（不必让 File 进入响应式系统）。
const attachments = ref<AttachmentDraft[]>([]);
const fileHandles = new Map<string, File>();
const IMAGE_MIME_TYPES: Record<string, string> = {
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.webp': 'image/webp',
};
const MAX_IMAGE_ATTACHMENTS = 3;
const dragActive = ref(false);
let dragDepth = 0;
let localSeq = 0;

const uploadAccept = [...ALLOWED_UPLOAD_EXTS, ...Object.keys(IMAGE_MIME_TYPES)].join(',');
const hasUploading = computed<boolean>(() =>
  attachments.value.some((a) => a.status === 'uploading'),
);

// exactOptionalPropertyTypes 下不能把可选 prop（boolean|undefined）直接绑到原生属性，
// 统一收敛成严格 boolean computed（同 DialogModal/TutorPanel 不直绑可选 prop 的范式）。
const isDisabled = computed<boolean>(() => props.disabled === true);
const isBusy = computed<boolean>(() => props.busy === true);
const placeholderText = computed<string>(() => props.placeholder ?? DEFAULT_PLACEHOLDER);

// ============ 语音输入：优先 Web Speech，缺失时回退到后端 ASR ============
const voice = useVoiceInput();
const recorder = useAudioRecorder();
const transcribing = ref(false);
const voiceError = ref('');
const voiceBusy = computed<boolean>(
  () => voice.listening.value || recorder.recording.value || transcribing.value,
);
const voiceButtonDisabled = computed<boolean>(
  () => isDisabled.value || isBusy.value || hasUploading.value || transcribing.value,
);
const voiceButtonIcon = computed<string>(() => {
  if (transcribing.value) return '...';
  if (voice.listening.value || recorder.recording.value) return '■';
  return '◉';
});
const voiceButtonTitle = computed<string>(() => {
  if (transcribing.value) return '正在转写语音';
  if (voice.listening.value) return '停止语音输入';
  if (recorder.recording.value) return '停止录音并转写';
  if (voice.supported.value) return '语音输入';
  if (recorder.supported.value) return '录音转写';
  return '当前浏览器不支持语音输入';
});
const voiceStatus = computed<string>(() => {
  if (transcribing.value) return '正在转写语音';
  if (voice.listening.value) return '正在听你说';
  if (recorder.recording.value) return '再次点击停止录音并转写';
  return '';
});
// 仍有附件在上传时禁止发送，避免就绪前的附件被遗漏（与 Claude Code 一致）。
const canSubmit = computed<boolean>(
  () =>
    !isDisabled.value &&
    !isBusy.value &&
    !hasUploading.value &&
    !voiceBusy.value &&
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

function syncInputText(next: string): void {
  mention.updateFromText(next);
  slash.updateFromText(next);
}

function appendVoiceText(text: string): void {
  const clean = text.trim();
  if (!clean) return;
  const current = inputText.value.trimEnd();
  syncInputText(current ? `${current} ${clean}` : clean);
  voiceError.value = '';
  focusInput();
}

function prefill(text: string): void {
  const clean = text.trim();
  if (!clean) return;
  syncInputText(clean);
  voiceError.value = '';
  focusInput();
}

watch(
  () => voice.transcript.value,
  (value) => {
    const clean = value.trim();
    if (!clean) return;
    appendVoiceText(clean);
    voice.transcript.value = '';
  },
  { flush: 'sync' },
);

async function stopRecordingAndTranscribe(): Promise<void> {
  transcribing.value = true;
  voiceError.value = '';
  try {
    const audio = await recorder.stop();
    if (!audio) {
      voiceError.value = '没有录到声音，请再试一次';
      return;
    }
    const res = await transcribeAudio(audio);
    const text = res.text.trim();
    if (res.degraded || !text) {
      voiceError.value = '语音转写暂不可用，请用键盘输入';
      return;
    }
    appendVoiceText(text);
  } catch (err) {
    voiceError.value = err instanceof Error ? err.message : '语音转写失败，请用键盘输入';
  } finally {
    transcribing.value = false;
  }
}

async function toggleVoiceInput(): Promise<void> {
  if (voiceButtonDisabled.value) return;
  voiceError.value = '';
  if (voice.listening.value) {
    voice.stop();
    return;
  }
  if (recorder.recording.value) {
    await stopRecordingAndTranscribe();
    return;
  }
  if (voice.supported.value) {
    try {
      voice.start();
    } catch {
      voiceError.value = '无法启动语音输入，请用键盘输入';
    }
    return;
  }
  if (recorder.supported.value) {
    const started = await recorder.start();
    if (!started) voiceError.value = '无法访问麦克风，请检查浏览器权限';
    return;
  }
  voiceError.value = '当前浏览器不支持语音输入';
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
  selectedCapability.value = null;
  slash.selectCommand(cmd);
  focusInput();
}

function toggleCapabilities(): void {
  capabilityMenuOpen.value = !capabilityMenuOpen.value;
}

function selectCapability(capability: WorkshopCapability): void {
  selectedCapability.value = capability;
  capabilityMenuOpen.value = false;
  slash.clearForced();
  focusInput();
}

function clearSelectedCapability(): void {
  selectedCapability.value = null;
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
  const imageMime = IMAGE_MIME_TYPES[extOf(file.name)];
  if (imageMime) {
    const imageCount = attachments.value.filter((a) => IMAGE_MIME_TYPES[a.ext]).length;
    if (imageCount > MAX_IMAGE_ATTACHMENTS) {
      patchAttachment(localId, {
        status: 'error',
        error: `最多上传 ${MAX_IMAGE_ATTACHMENTS} 张图片`,
      });
      return;
    }
    if (file.size > MAX_UPLOAD_BYTES) {
      patchAttachment(localId, { status: 'error', error: '图片不能超过 5 MB' });
      return;
    }
    if (file.type && file.type !== imageMime) {
      patchAttachment(localId, { status: 'error', error: '图片格式与文件扩展名不匹配' });
      return;
    }
    patchAttachment(localId, { status: 'uploading', error: undefined });
    try {
      const imageDataUrl = await readImageDataUrl(file, imageMime);
      patchAttachment(localId, { status: 'ready', imageDataUrl, error: undefined });
    } catch {
      patchAttachment(localId, { status: 'error', error: '图片读取失败' });
    }
    return;
  }
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

function readImageDataUrl(file: File, mimeType: string): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result !== 'string') {
        reject(new Error('图片读取失败'));
        return;
      }
      const separator = reader.result.indexOf(',');
      if (separator < 0) {
        reject(new Error('图片读取失败'));
        return;
      }
      resolve(`data:${mimeType};base64,${reader.result.slice(separator + 1)}`);
    };
    reader.onerror = () => reject(new Error('图片读取失败'));
    reader.readAsDataURL(file);
  });
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

function readyImages(): string[] {
  const images: string[] = [];
  for (const attachment of attachments.value) {
    if (attachment.status === 'ready' && attachment.imageDataUrl) {
      images.push(attachment.imageDataUrl);
    }
  }
  return images;
}

function submit(): void {
  const text = inputText.value.trim();
  if (!text || isDisabled.value || isBusy.value) return;
  // 还有附件在上传或语音仍在采集中：不发送，避免上下文被截断。
  if (hasUploading.value || voiceBusy.value) return;
  const capabilityNamespace = selectedCapability.value?.namespace;
  const forcedIntent = selectedCapability.value ? null : slash.forcedIntent.value;
  const source: WorkshopToolRouteSource = selectedCapability.value
    ? 'capability_launcher'
    : slash.forcedIntent.value
      ? 'slash_command'
      : 'composer_text';
  const images = readyImages();
  emit('submit', {
    text,
    manualKcCodes: [...mention.selectedKcCodes.value],
    contextHint: mention.contextHint.value,
    forcedIntent,
    source,
    ...(capabilityNamespace ? { capabilityNamespace } : {}),
    attachments: readyAttachments(),
    ...(images.length > 0 ? { images } : {}),
  });
  mention.reset();
  slash.reset();
  selectedCapability.value = null;
  capabilityMenuOpen.value = false;
  voiceError.value = '';
  attachments.value = [];
  fileHandles.clear();
}

defineExpose({ focus: focusInput, prefill });
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
      <span class="composer__dropzone-text">📎 松开上传文件或图片</span>
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
      v-if="selectedRefs.length > 0 || forcedCommand || selectedCapability"
      class="composer__chips"
    >
      <button
        v-if="selectedCapability"
        type="button"
        class="tool-pill"
        :title="`取消「${selectedCapability.label}」，恢复自动判定`"
        data-testid="composer-capability-pill"
        @click="clearSelectedCapability"
      >
        <span class="tool-pill__icon">+</span>
        <span class="tool-pill__label">{{ selectedCapability.label }}</span>
        <span
          class="tool-pill__x"
          aria-hidden="true"
        >×</span>
      </button>
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
      <div class="composer__capability-wrap">
        <button
          type="button"
          class="composer__capability-toggle"
          data-testid="composer-open-capabilities"
          title="选择能力"
          :aria-expanded="capabilityMenuOpen"
          :disabled="isDisabled"
          @click="toggleCapabilities"
        >
          +
        </button>
        <div
          v-if="capabilityMenuOpen"
          class="composer__capability-menu"
          data-testid="composer-capability-menu"
        >
          <button
            v-for="capability in WORKSHOP_CAPABILITIES"
            :key="capability.namespace"
            type="button"
            class="composer__capability-card"
            :data-testid="`composer-capability-${capability.namespace}`"
            @click="selectCapability(capability)"
          >
            <span class="composer__capability-label">{{ capability.label }}</span>
            <span class="composer__capability-desc">{{ capability.description }}</span>
          </button>
        </div>
      </div>
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
        title="上传文件或图片（也可拖拽 / 粘贴）"
        :disabled="isDisabled"
        @click="openFilePicker"
      >
        📎
      </button>
      <button
        type="button"
        class="composer__voice"
        :class="{ 'composer__voice--active': voiceBusy }"
        data-testid="composer-voice"
        :title="voiceButtonTitle"
        :aria-pressed="voiceBusy"
        :disabled="voiceButtonDisabled"
        @click="toggleVoiceInput"
      >
        <span class="composer__voice-icon">{{ voiceButtonIcon }}</span>
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
    <div
      v-if="voiceStatus || voiceError"
      class="composer__voice-line"
    >
      <span
        v-if="voiceStatus"
        class="composer__voice-status"
        data-testid="composer-voice-status"
      >{{ voiceStatus }}</span>
      <span
        v-if="voiceError"
        class="composer__voice-error"
        data-testid="composer-voice-error"
      >{{ voiceError }}</span>
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
.composer__capability-wrap {
  position: relative;
  flex: 0 0 auto;
}
.composer__capability-toggle {
  width: 38px;
  height: 38px;
  font-family: inherit;
  font-size: 20px;
  font-weight: 700;
  line-height: 1;
  background: #fff8df;
  color: #2a1a08;
  border: 3px solid #8c6a23;
  border-radius: 6px;
  box-shadow: 0 3px 0 #8c6a23;
  cursor: pointer;
  transition: transform 80ms, box-shadow 80ms;
}
.composer__capability-toggle:hover:not(:disabled) {
  background: #ffe098;
}
.composer__capability-toggle:active:not(:disabled) {
  transform: translateY(2px);
  box-shadow: 0 1px 0 #8c6a23;
}
.composer__capability-toggle:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.composer__capability-menu {
  position: absolute;
  left: 0;
  bottom: calc(100% + 8px);
  z-index: 4;
  display: grid;
  grid-template-columns: repeat(2, minmax(150px, 1fr));
  gap: 6px;
  width: min(420px, 82vw);
  padding: 8px;
  background: #fff8df;
  border: 3px solid #8c6a23;
  border-radius: 8px;
  box-shadow: 0 8px 0 rgba(140, 106, 35, 0.18);
}
.composer__capability-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  min-height: 58px;
  padding: 8px;
  font-family: inherit;
  text-align: left;
  color: #2a1a08;
  background: #fff3c4;
  border: 2px solid rgba(140, 106, 35, 0.55);
  border-radius: 6px;
  cursor: pointer;
}
.composer__capability-card:hover {
  background: #fde48a;
  border-color: #8c6a23;
}
.composer__capability-label {
  font-size: 12px;
  font-weight: 800;
}
.composer__capability-desc {
  font-size: 10px;
  line-height: 1.45;
  color: #6b4c17;
}
.composer__attach,
.composer__voice {
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
.composer__voice {
  width: 38px;
  height: 38px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
}
.composer__voice--active {
  background: #2a1a08;
  color: #fff8df;
}
.composer__voice-icon {
  font-size: 13px;
  line-height: 1;
}
.composer__attach:hover:not(:disabled),
.composer__voice:hover:not(:disabled) {
  background: #ffe098;
}
.composer__voice--active:hover:not(:disabled) {
  background: #3a2a14;
}
.composer__attach:active:not(:disabled),
.composer__voice:active:not(:disabled) {
  transform: translateY(2px);
  box-shadow: 0 1px 0 #8c6a23;
}
.composer__attach:disabled,
.composer__voice:disabled {
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
.composer__voice-line {
  min-height: 20px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding-left: 96px;
  font-size: 11px;
  line-height: 1.45;
}
.composer__voice-status {
  color: #4d6e1d;
}
.composer__voice-error {
  color: #9b2d20;
}
</style>
