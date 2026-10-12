<script setup lang="ts">
/**
 * 工坊输入框的「附件 pill」列表（纯展示，仿 Claude Code 网页版）。
 * 每枚 pill 呈现：类型图标 + 文件名 + 大小，并按状态机切换右侧信息：
 *   uploading → 旋转 spinner（解析中…）；
 *   ready     → 行数 · 字数；
 *   error     → 错误信息 + 「重试」。
 * 每枚 pill 常驻一个「×」删除按钮。本组件不含任何网络逻辑，状态机由父级 ChatComposer 驱动，
 * 这里只 emit remove / retry。
 */
import { computed } from 'vue';
import { attachmentIcon, formatFileSize } from '@/api/upload';

export type AttachmentStatus = 'uploading' | 'ready' | 'error';

/** 一枚附件 pill 的视图模型（客户端状态机；localId 贯穿状态切换，与后端 upload id 不同）。 */
export interface AttachmentDraft {
  localId: string;
  filename: string;
  ext: string;
  byteSize: number;
  status: AttachmentStatus;
  /** status==='ready' 必有：后端 upload id。 */
  uploadId?: number;
  /** status==='ready'：解析行数 / 字数。 */
  lineCount?: number;
  charCount?: number;
  /** 图片附件只保留本轮 Data URL，不上传或持久化到文档附件服务。 */
  imageDataUrl?: string;
  /** status==='error'：错误信息。 */
  error?: string;
}

const props = defineProps<{
  attachments: AttachmentDraft[];
}>();

const emit = defineEmits<{
  (event: 'remove', localId: string): void;
  (event: 'retry', localId: string): void;
}>();

const hasAttachments = computed<boolean>(() => props.attachments.length > 0);
</script>

<template>
  <div
    v-if="hasAttachments"
    class="pills"
    data-testid="attachment-pills"
  >
    <div
      v-for="a in attachments"
      :key="a.localId"
      class="pill"
      :class="`pill--${a.status}`"
      :data-testid="`attachment-pill`"
      :data-status="a.status"
      :data-local-id="a.localId"
    >
      <span
        class="pill__icon"
        aria-hidden="true"
      >{{ a.imageDataUrl ? '🖼️' : attachmentIcon(a.ext) }}</span>
      <span class="pill__body">
        <span
          class="pill__name"
          :title="a.filename"
        >{{ a.filename }}</span>
        <span class="pill__meta">
          <template v-if="a.status === 'uploading'">
            <span
              class="pill__spinner"
              data-testid="pill-spinner"
              aria-label="上传中"
            />
            <span>{{ formatFileSize(a.byteSize) }} · 解析中…</span>
          </template>
          <template v-else-if="a.status === 'ready'">
            <template v-if="a.imageDataUrl">{{ formatFileSize(a.byteSize) }} · 图片</template>
            <template v-else>
              {{ formatFileSize(a.byteSize) }} · {{ a.lineCount ?? 0 }} 行 · {{ a.charCount ?? 0 }} 字
            </template>
          </template>
          <span
            v-else
            class="pill__error"
            data-testid="pill-error"
          >{{ a.error || '解析失败' }}</span>
        </span>
      </span>
      <button
        v-if="a.status === 'error'"
        type="button"
        class="pill__retry"
        data-testid="pill-retry"
        title="重试上传"
        @click="emit('retry', a.localId)"
      >
        重试
      </button>
      <button
        type="button"
        class="pill__x"
        data-testid="pill-remove"
        title="移除"
        @click="emit('remove', a.localId)"
      >
        ×
      </button>
    </div>
  </div>
</template>

<style scoped>
.pills {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  font-family: var(--pyt-font-pixel);
}
.pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 240px;
  padding: 3px 6px 3px 8px;
  font-size: 11px;
  border-radius: 8px;
  border: 2px solid var(--pyt-color-border);
  background: var(--pyt-color-bg-elevated);
  color: var(--pyt-color-text-primary);
}
.pill--ready {
  border-color: #4d6e1d;
  background: #d6ead1;
  color: #2d5b1b;
}
.pill--error {
  border-color: var(--pyt-color-danger);
  background: #f8d7d0;
  color: #b3402a;
}
.pill__icon {
  flex: 0 0 auto;
  font-size: 14px;
  line-height: 1;
}
.pill__body {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 1px;
}
.pill__name {
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 700;
}
.pill__meta {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 10px;
  color: var(--pyt-color-text-muted);
}
.pill--ready .pill__meta {
  color: #3f6b27;
}
.pill__error {
  color: #b3402a;
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 上传中 spinner（像素风：小方块旋转）。 */
.pill__spinner {
  flex: 0 0 auto;
  width: 10px;
  height: 10px;
  border: 2px solid rgba(140, 106, 35, 0.35);
  border-top-color: #8c6a23;
  border-radius: 50%;
  animation: pill-spin 0.7s linear infinite;
}
@keyframes pill-spin {
  to { transform: rotate(360deg); }
}
.pill__retry,
.pill__x {
  flex: 0 0 auto;
  font-family: inherit;
  cursor: pointer;
  background: transparent;
  color: inherit;
}
.pill__retry {
  font-size: 10px;
  font-weight: 700;
  padding: 1px 6px;
  border: 1px solid currentColor;
  border-radius: 999px;
}
.pill__retry:hover {
  background: rgba(255, 255, 255, 0.4);
}
.pill__x {
  font-size: 14px;
  line-height: 1;
  font-weight: 700;
  padding: 0 2px;
  border: none;
}
.pill__x:hover {
  color: var(--pyt-color-danger);
}
</style>
