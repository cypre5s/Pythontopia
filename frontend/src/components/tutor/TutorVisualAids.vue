<script setup lang="ts">
/**
 * 答疑多模态图解：把 TutorAgent 返回的 visual_aids 渲染为
 * - mermaid 结构图（用 mermaid 渲染 SVG，失败回退源码）
 * - AI 插画（调 /api/media/image 文生图，无 key 占位 degraded）
 * 卡片化展示，配合文字答案构成「文字 + 图解」的多模态答疑。
 */
import { onMounted, ref, watch } from 'vue';
import { generateImage } from '@/api/media';

export interface TutorVisualAid {
  kind: 'mermaid' | 'image';
  caption?: string;
  mermaid?: string | null;
  image_prompt?: string | null;
  kc_codes?: string[];
}

const props = defineProps<{ aids: TutorVisualAid[] }>();

interface RenderedAid {
  kind: 'mermaid' | 'image';
  caption: string;
  svg?: string;
  rawMermaid?: string;
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
    <h4 class="visual-aids__title">图解说明</h4>
    <div class="visual-aids__grid">
      <figure
        v-for="(card, i) in rendered"
        :key="i"
        class="aid-card"
      >
        <div
          v-if="card.kind === 'mermaid' && card.svg"
          class="aid-mermaid"
          v-html="card.svg"
        />
        <pre
          v-else-if="card.kind === 'mermaid'"
          class="aid-mermaid-raw"
        >{{ card.rawMermaid }}</pre>
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
