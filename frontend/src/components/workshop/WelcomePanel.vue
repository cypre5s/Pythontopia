<script setup lang="ts">
/**
 * WelcomePanel —— 百晓工坊欢迎屏（对话流为空时的首屏）。
 *
 * 顶部百晓立绘 + 标题「想学什么直接说」+ 副文案；
 * 中部 4 个快捷提问 chips（点击把完整文本 emit 给调用方代发）；
 * 底部「能力一览」折叠：按 SLASH_COMMANDS 的 group 字段聚合渲染 icon+label+hint
 * 小网格——按数组实际内容渲染，WP5 扩充新命令/新分组后本组件无需改动。
 */
import { computed } from 'vue';
import WorkshopAvatar from '@/components/workshop/WorkshopAvatar.vue';
import {
  SLASH_COMMANDS,
  type SlashCommandGroup,
} from '@/composables/useSlashCommand';

const emit = defineEmits<{ (e: 'quick', text: string): void }>();

/** 快捷提问（完整文本原样上送，含 / 命令前缀）。 */
const QUICK_PROMPTS: readonly string[] = [
  '什么是列表推导式？',
  '/考考我 循环',
  '出几道函数的练习',
  '我学得怎么样？',
];

// 按 group 聚合且保持命令表中的出现顺序（不依赖 useSlashCommand 内部未导出的分组序）。
const groups = computed<SlashCommandGroup[]>(() => {
  const out: SlashCommandGroup[] = [];
  for (const cmd of SLASH_COMMANDS) {
    const bucket = out.find((g) => g.group === cmd.group);
    if (bucket) bucket.items.push(cmd);
    else out.push({ group: cmd.group, items: [cmd] });
  }
  return out;
});

function onQuick(text: string): void {
  emit('quick', text);
}
</script>

<template>
  <div
    class="welcome"
    data-testid="welcome-panel"
  >
    <WorkshopAvatar class="welcome__avatar" />
    <p class="welcome__title">
      想学什么直接说
    </p>
    <p class="welcome__sub">
      比如「循环怎么用」，也可以让百晓出题、规划学习、找资料
    </p>
    <div class="welcome__chips">
      <button
        v-for="text in QUICK_PROMPTS"
        :key="text"
        type="button"
        class="welcome__chip"
        data-testid="welcome-quick-chip"
        @click="onQuick(text)"
      >
        {{ text }}
      </button>
    </div>
    <details
      class="welcome__abilities"
      data-testid="welcome-abilities"
    >
      <summary class="welcome__abilities-summary">
        能力一览
      </summary>
      <div
        v-for="g in groups"
        :key="g.group"
        class="welcome__group"
      >
        <p class="welcome__group-name">
          {{ g.group }}
        </p>
        <ul class="welcome__grid">
          <li
            v-for="cmd in g.items"
            :key="cmd.intent"
            class="welcome__cell"
          >
            <span
              class="welcome__cell-icon"
              aria-hidden="true"
            >{{ cmd.icon }}</span>
            <span class="welcome__cell-body">
              <span class="welcome__cell-label">{{ cmd.label }}</span>
              <span class="welcome__cell-hint">{{ cmd.hint }}</span>
            </span>
          </li>
        </ul>
      </div>
    </details>
  </div>
</template>

<style scoped>
.welcome {
  margin: auto;
  max-width: 480px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 24px 16px;
  text-align: center;
  font-family: var(--pyt-font-pixel);
  color: var(--pyt-color-text-primary);
}
.welcome__avatar {
  margin-bottom: 2px;
}
.welcome__title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--pyt-color-text-primary);
}
.welcome__sub {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--pyt-color-text-muted);
}
.welcome__chips {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 6px;
  margin-top: 6px;
}
.welcome__chip {
  font-family: inherit;
  font-size: 12px;
  padding: 4px 12px;
  background: #fff8df;
  border: 2px solid #8c6a23;
  border-radius: var(--pyt-radius-full);
  color: #2a1a08;
  cursor: pointer;
  box-shadow: 2px 2px 0 rgba(140, 106, 35, 0.5);
}
.welcome__chip:hover {
  background: #ffe098;
}
.welcome__chip:active {
  transform: translate(2px, 2px);
  box-shadow: none;
}
.welcome__abilities {
  width: 100%;
  margin-top: 10px;
  font-size: 12px;
  text-align: left;
}
.welcome__abilities-summary {
  text-align: center;
  font-weight: 700;
  font-size: 12px;
  color: var(--pyt-color-text-hint);
  cursor: pointer;
  list-style: none;
}
.welcome__abilities-summary::-webkit-details-marker {
  display: none;
}
.welcome__abilities-summary:hover {
  color: var(--pyt-color-text-primary);
}
.welcome__group {
  margin-top: 8px;
}
.welcome__group-name {
  margin: 0 0 4px;
  font-size: 11px;
  font-weight: 700;
  color: var(--pyt-color-text-muted);
  letter-spacing: 0.5px;
}
.welcome__grid {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 4px;
}
.welcome__cell {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 5px 8px;
  background: #f5ecd4;
  border: 1px solid rgba(140, 106, 35, 0.45);
  border-radius: var(--pyt-radius-md);
}
.welcome__cell-icon {
  flex: none;
  width: 14px;
  text-align: center;
  font-weight: 700;
  color: #8c6a23;
}
.welcome__cell-body {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}
.welcome__cell-label {
  font-size: 11px;
  font-weight: 700;
  color: var(--pyt-color-text-primary);
}
.welcome__cell-hint {
  font-size: 10px;
  line-height: 1.4;
  color: var(--pyt-color-text-muted);
}
</style>
