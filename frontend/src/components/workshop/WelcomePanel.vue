<script setup lang="ts">
/**
 * WelcomePanel —— 百晓工坊欢迎屏（对话流为空时的首屏）。
 *
 * 顶部百晓立绘 + 标题「想学什么直接说」+ 副文案；
 * 中部 3 个快捷提问 chips（点击把完整文本 emit 给调用方代发）；
 * 能力入口默认折叠为单个按钮，点击后才展示 6 个学生可见能力组。
 */
import { ref } from 'vue';
import WorkshopAvatar from '@/components/workshop/WorkshopAvatar.vue';
import { WORKSHOP_CAPABILITIES } from '@/components/workshop/capabilities';

const emit = defineEmits<{ (e: 'quick', text: string): void }>();

/** 快捷提问（完整文本原样上送）。 */
const QUICK_PROMPTS: readonly string[] = [
  '我想学循环，先从哪里开始？',
  '解释一下这段代码为什么这么写',
  '出一道函数的小练习',
];

const capabilitiesOpen = ref(false);

function onQuick(text: string): void {
  emit('quick', text);
}

function toggleCapabilities(): void {
  capabilitiesOpen.value = !capabilitiesOpen.value;
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
    <button
      type="button"
      class="welcome__capability-button"
      data-testid="welcome-open-capabilities"
      :aria-expanded="capabilitiesOpen"
      @click="toggleCapabilities"
    >
      {{ capabilitiesOpen ? '收起能力' : '查看百晓能帮我做什么' }}
    </button>
    <div
      v-if="capabilitiesOpen"
      class="welcome__abilities"
      data-testid="welcome-abilities"
    >
      <article
        v-for="capability in WORKSHOP_CAPABILITIES"
        :key="capability.namespace"
        class="welcome__capability-card"
        data-testid="welcome-capability-card"
      >
        <p class="welcome__capability-label">
          {{ capability.label }}
        </p>
        <p class="welcome__capability-desc">
          {{ capability.description }}
        </p>
        <p class="welcome__capability-example">
          例如：{{ capability.examples[0] }}
        </p>
      </article>
    </div>
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
.welcome__capability-button {
  margin-top: 10px;
  font-family: inherit;
  font-size: 12px;
  font-weight: 700;
  color: #4f3514;
  background: transparent;
  border: 0;
  border-bottom: 2px solid rgba(140, 106, 35, 0.4);
  cursor: pointer;
}
.welcome__capability-button:hover {
  color: var(--pyt-color-text-primary);
  border-bottom-color: #8c6a23;
}
.welcome__abilities {
  width: 100%;
  margin-top: 10px;
  font-size: 12px;
  text-align: left;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 6px;
}
.welcome__capability-card {
  padding: 8px 10px;
  background: #f5ecd4;
  border: 1px solid rgba(140, 106, 35, 0.45);
  border-radius: var(--pyt-radius-md);
}
.welcome__capability-label {
  margin: 0 0 4px;
  font-size: 12px;
  font-weight: 700;
  color: #8c6a23;
}
.welcome__capability-desc,
.welcome__capability-example {
  margin: 0;
  font-size: 10px;
  line-height: 1.4;
  color: var(--pyt-color-text-muted);
}
.welcome__capability-example {
  margin-top: 3px;
  color: #6b4b19;
}
</style>
