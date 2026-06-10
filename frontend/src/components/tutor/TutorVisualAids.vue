<script setup lang="ts">
/**
 * 答疑多模态图解：把 TutorAgent 返回的 visual_aids 渲染为
 * - mermaid 结构图（用 mermaid 渲染 SVG，失败回退源码）
 * - ascii 字符画（等宽 <pre>，内存布局/索引演示类）
 * - svg 矢量示意（服务端已校验 + 前端 DOMPurify svg profile 双重消毒）
 * - AI 插画（调 /api/media/image 文生图，无 key 占位 degraded）
 * 卡片化展示，配合文字答案构成「文字 + 图解」的多模态答疑。
 */
import { onMounted, ref, watch } from 'vue';
import { generateImage } from '@/api/media';
import { sanitizeSvgMarkup } from '@/utils/sanitizeSvg';

export interface TutorVisualAid {
  kind: 'mermaid' | 'image' | 'ascii' | 'svg';
  caption?: string;
  mermaid?: string | null;
  image_prompt?: string | null;
  ascii_art?: string | null;
  svg?: string | null;
  kc_codes?: string[];
}

const props = defineProps<{ aids: TutorVisualAid[] }>();

interface RenderedAid {
  kind: 'mermaid' | 'image' | 'ascii' | 'svg';
  caption: string;
  /** mermaid 渲染产物或已消毒的后端 svg 源码。 */
  svg?: string;
  rawMermaid?: string;
  ascii?: string;
  imageUrl?: string;
  degraded?: boolean;
  loading?: boolean;
  failed?: boolean;
}

const rendered = ref<RenderedAid[]>([]);

async function renderMermaid(text: string, idx: number): Promise<RenderedAid> {
  try {
    const mermaid = (await import('mermaid')).default;
    mermaid.initialize({ startOnLoad: false, theme: 'base', securityLevel: 'strict' });
    const { svg } = await mermaid.render(`tutor-mermaid-${Date.now()}-${idx}`, text);
    return { kind: 'mermaid', caption: '', svg };
  } catch {
    return { kind: 'mermaid', caption: '', rawMermaid: text };
  }
}

async function renderImage(prompt: string, kcCodes: string[]): Promise<RenderedAid> {
  try {
    const asset = await generateImage(prompt, kcCodes[0], kcCodes);
    return { kind: 'image', caption: '', imageUrl: asset.url, degraded: asset.degraded };
  } catch {
    return { kind: 'image', caption: '', failed: true };
  }
}

async function build(): Promise<void> {
  const out: RenderedAid[] = [];
  for (let i = 0; i < props.aids.length; i += 1) {
    const aid = props.aids[i];
    if (!aid) continue;
    if (aid.kind === 'mermaid' && aid.mermaid) {
      const card = await renderMermaid(aid.mermaid, i);
      card.caption = aid.caption ?? '';
      out.push(card);
    } else if (aid.kind === 'ascii' && aid.ascii_art) {
      out.push({ kind: 'ascii', caption: aid.caption ?? '', ascii: aid.ascii_art });
    } else if (aid.kind === 'svg' && aid.svg) {
      // 消毒后必须仍是 <svg…>，否则视为不可信整体丢弃（不渲染半截残骸）。
      const clean = sanitizeSvgMarkup(aid.svg);
      if (clean.includes('<svg')) {
        out.push({ kind: 'svg', caption: aid.caption ?? '', svg: clean });
      }
    } else if (aid.kind === 'image' && aid.image_prompt) {
      const card = await renderImage(aid.image_prompt, aid.kc_codes ?? []);
      card.caption = aid.caption ?? '';
      out.push(card);
    }
  }
  rendered.value = out;
}

onMounted(build);
watch(() => props.aids, build);
</script>

<template>
  <section
    v-if="rendered.length > 0"
    class="visual-aids"
    data-testid="tutor-visual-aids"
  >
    <h4 class="visual-aids__title">
      图解说明
    </h4>
    <div class="visual-aids__grid">
      <figure
        v-for="(card, i) in rendered"
        :key="i"
        class="aid-card"
      >
        <!-- eslint-disable vue/no-v-html — mermaid 以 securityLevel:'strict' 渲染；
             kind=svg 经 服务端黑名单 + DOMPurify svg profile 双重消毒，均无脚本/事件，已审阅 -->
        <div
          v-if="card.kind === 'mermaid' && card.svg"
          class="aid-mermaid"
          v-html="card.svg"
        />
        <pre
          v-else-if="card.kind === 'mermaid'"
          class="aid-mermaid-raw"
        >{{ card.rawMermaid }}</pre>
        <pre
          v-else-if="card.kind === 'ascii'"
          class="aid-ascii"
          data-testid="aid-ascii"
        >{{ card.ascii }}</pre>
        <div
          v-else-if="card.kind === 'svg' && card.svg"
          class="aid-svg"
          data-testid="aid-svg"
          v-html="card.svg"
        />
        <template v-else-if="card.kind === 'image'">
          <img
            v-if="card.imageUrl"
            :src="card.imageUrl"
            :alt="card.caption"
            class="aid-image"
          >
          <span
            v-if="card.degraded"
            class="aid-badge"
          >占位</span>
          <p
            v-if="card.failed"
            class="aid-failed"
          >
            图解生成失败
          </p>
        </template>
        <!-- eslint-enable vue/no-v-html -->
        <figcaption
          v-if="card.caption"
          class="aid-caption"
        >
          {{ card.caption }}
        </figcaption>
      </figure>
    </div>
  </section>
</template>

<style scoped>
.visual-aids {
  border-top: 2px dashed rgba(110, 79, 29, 0.35);
  padding-top: 8px;
  font-family: var(--pyt-font-pixel);
}
.visual-aids__title {
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--pyt-color-text-muted);
  font-family: var(--pyt-font-pixel);
}
.visual-aids__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 10px;
}
.aid-card {
  position: relative;
  margin: 0;
  padding: 8px;
  background: #fffbe9;
  border: 2px solid var(--pyt-color-text-hint);
  border-radius: 6px;
  box-shadow: 2px 2px 0 rgba(42, 26, 5, 0.5);
}
.aid-mermaid {
  overflow: auto;
}
.aid-mermaid :deep(svg) {
  max-width: 100%;
  height: auto;
}
.aid-mermaid-raw {
  margin: 0;
  font-size: 11px;
  white-space: pre-wrap;
  word-break: break-word;
  color: #5e4923;
  font-family: var(--pyt-font-pixel);
}
/* ascii 字符画：等宽 + 终端深色块（与代码输出块同语言），保持逐格对齐不换行。 */
.aid-ascii {
  margin: 0;
  padding: 8px 10px;
  font-family: var(--pyt-font-mono, monospace);
  font-size: 12px;
  line-height: 1.35;
  white-space: pre;
  overflow: auto;
  color: var(--pyt-color-terminal-text, #f1e5c8);
  background: var(--pyt-color-terminal-bg, #2e2417);
  border-radius: 4px;
}
/* 后端 svg 示意：限宽自适应，浅色底衬托线条。 */
.aid-svg {
  overflow: auto;
  background: #fffdf4;
  border-radius: 4px;
}
.aid-svg :deep(svg) {
  max-width: 100%;
  height: auto;
  display: block;
  margin: 0 auto;
}
.aid-image {
  width: 100%;
  aspect-ratio: 1 / 1;
  object-fit: cover;
  border: 2px solid var(--pyt-color-shadow);
  border-radius: 4px;
  image-rendering: pixelated;
  background: #1a1208;
}
.aid-badge {
  position: absolute;
  top: 12px;
  right: 12px;
  font-size: 10px;
  color: var(--pyt-color-bg-elevated);
  background: #b39554;
  border-radius: 999px;
  padding: 2px 8px;
  font-family: var(--pyt-font-pixel);
}
.aid-failed {
  margin: 0;
  font-size: 12px;
  color: #b3402a;
  text-align: center;
  padding: 16px 0;
  font-family: var(--pyt-font-pixel);
}
.aid-caption {
  margin-top: 6px;
  font-size: 11px;
  color: var(--pyt-color-text-hint);
  text-align: center;
  font-family: var(--pyt-font-pixel);
}
</style>
