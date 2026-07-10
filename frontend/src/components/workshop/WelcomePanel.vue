<script setup lang="ts">
/**
 * WelcomePanel —— 百晓工坊欢迎屏（对话流为空时的首屏）。
 *
 * 顶部百晓立绘 + 标题「想学什么直接说」+ 极简副文案；
 * 中部 3 个快捷提问 chips（点击把完整文本 emit 给调用方代发）；
 * 能力入口默认折叠为单个按钮，展开后以一行清单展示，不堆叠说明卡片。
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
      可以提问、出题、规划或找资料
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
      {{ capabilitiesOpen ? '收起' : '百晓能做什么' }}
    </button>
    <p
      v-if="capabilitiesOpen"
      class="welcome__abilities"
      data-testid="welcome-abilities"
    >
      <template
        v-for="(capability, index) in WORKSHOP_CAPABILITIES"
        :key="capability.namespace"
      >
        <span
          class="welcome__capability-label"
          data-testid="welcome-capability-item"
        >
          {{ capability.label }}
        </span>
        <span
          v-if="index < WORKSHOP_CAPABILITIES.length - 1"
          class="welcome__capability-separator"
          aria-hidden="true"
        >·</span>
      </template>
    </p>
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
  color: #493416;
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
  background: #fff7e4;
  border: 1px solid #b88939;
  border-radius: var(--pyt-radius-full);
  color: #2a1a08;
  cursor: pointer;
  box-shadow: none;
}
.welcome__chip:hover {
  background: #f4e0ae;
}
.welcome__chip:active {
  transform: translateY(1px);
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
  width: min(100%, 440px);
  margin-top: 10px;
  margin-bottom: 0;
  font-size: 12px;
  line-height: 1.8;
  text-align: center;
  color: #725628;
}
.welcome__capability-label {
  font-weight: 700;
}
.welcome__capability-separator {
  margin: 0 6px;
  color: #b38c4c;
}
</style>
