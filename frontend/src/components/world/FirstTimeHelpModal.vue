<script setup lang="ts">
// W4-LOGIN-UX：首次进入 /world 时显示操作引导，关闭后写入 localStorage 标记，
// 避免后续重复打扰。仅在客户端 mounted 后做 storage 检查，SSR 不会触发。
// W4-BGM：dismiss 是首次进入 /world 后保证发生的 user gesture，必须调用
// unlockAudio() 让浏览器 autoplay 策略放行，补放 WorldScene 已排队的 BGM。
import { onMounted, ref } from 'vue';
import { unlockAudio } from '@/composables/useAudio';

const HELP_FLAG = 'pyt_helped_v1';

// 关闭时通知父级（WorldPage）：首次进入的镇长 Py 发任务对话要等帮助弹窗关掉后再触发，
// 避免「帮助弹窗 + 自动对话」两层叠加打断新手。
const emit = defineEmits<{ dismissed: [] }>();

const visible = ref(false);

function dismiss(): void {
  visible.value = false;
  unlockAudio();
  try {
    window.localStorage.setItem(HELP_FLAG, '1');
  } catch {
    // 隐私模式下 localStorage 可能不可用，忽略写入失败。
  }
  emit('dismissed');
}

onMounted(() => {
  try {
    const seen = window.localStorage.getItem(HELP_FLAG);
    if (!seen) {
      visible.value = true;
    }
  } catch {
    visible.value = true;
  }
});

defineExpose({
  reopen(): void {
    visible.value = true;
  },
});
</script>

<template>
  <div
    v-if="visible"
    class="pyt-help-modal"
    role="dialog"
    aria-modal="true"
    aria-labelledby="pyt-help-title"
  >
    <div
      class="pyt-help-backdrop"
      @click="dismiss"
    />
    <div class="pyt-help-panel">
      <h2
        id="pyt-help-title"
        class="pyt-help-title"
      >
        欢迎来到 Pythontopia
      </h2>
      <p class="pyt-help-lede">
        先花 10 秒认识操作和界面，让小镇之旅更顺畅。
      </p>

      <!-- 首次进入 HUD 引导：突出「向导=问 AI」这一新手生命线 -->
      <div class="pyt-help-guide">
        <span
          class="pyt-help-guide__icon"
          aria-hidden="true"
        >✧</span>
        <p class="pyt-help-guide__text">
          卡住了？随时点左下角「<b>向导</b>」问 AI 老师（按 <b>G</b>），它会带你看 / 学 / 练 / 测。
        </p>
      </div>
      <ul class="pyt-help-list">
        <li>
          <span class="pyt-help-key">WASD</span>
          <span class="pyt-help-key">↑ ↓ ← →</span>
          <span class="pyt-help-text">移动角色，走到地图边缘自动切换场景</span>
        </li>
        <li>
          <span class="pyt-help-key">F</span>
          <span class="pyt-help-key">E</span>
          <span class="pyt-help-text">与 NPC 对话、调查物品、领取任务</span>
        </li>
        <li>
          <span class="pyt-help-key">Space</span>
          <span class="pyt-help-text">面朝矮栅栏或木桩时跳过低障碍</span>
        </li>
        <li>
          <span class="pyt-help-key">Tab</span>
          <span class="pyt-help-text">展开快捷面板（M 地图 / K 技能 / J 对话历史）</span>
        </li>
        <li>
          <span class="pyt-help-key">L</span>
          <span class="pyt-help-key">,</span>
          <span class="pyt-help-text">L 直达课程库；逗号键打开设置（音量/语言/退出）</span>
        </li>
        <li>
          <span class="pyt-help-key">右上角</span>
          <span class="pyt-help-text">小地图常驻显示当前地图与出口方向，Shift+M 折叠</span>
        </li>
        <li>
          <span class="pyt-help-key">ESC</span>
          <span class="pyt-help-text">关闭当前对话、设置或快捷菜单</span>
        </li>
      </ul>

      <p class="pyt-help-subtitle">
        底部工具栏一览
      </p>
      <ul class="pyt-help-dock">
        <li>
          <span
            class="pyt-help-dock__ico"
            aria-hidden="true"
          >✦</span>
          <span class="pyt-help-dock__name">任务</span>
          <span class="pyt-help-dock__desc">主线进度（Q）</span>
        </li>
        <li>
          <span
            class="pyt-help-dock__ico"
            aria-hidden="true"
          >✉</span>
          <span class="pyt-help-dock__name">对话</span>
          <span class="pyt-help-dock__desc">最近对话（J）</span>
        </li>
        <li>
          <span
            class="pyt-help-dock__ico"
            aria-hidden="true"
          >◰</span>
          <span class="pyt-help-dock__name">地图</span>
          <span class="pyt-help-dock__desc">打开地图（M）</span>
        </li>
        <li>
          <span
            class="pyt-help-dock__ico"
            aria-hidden="true"
          >★</span>
          <span class="pyt-help-dock__name">技能</span>
          <span class="pyt-help-dock__desc">技能树（K）</span>
        </li>
        <li>
          <span
            class="pyt-help-dock__ico"
            aria-hidden="true"
          >☰</span>
          <span class="pyt-help-dock__name">菜单</span>
          <span class="pyt-help-dock__desc">设置 / 退出（ESC）</span>
        </li>
      </ul>
      <button
        type="button"
        class="pyt-help-btn"
        autofocus
        @click="dismiss"
      >
        我知道了，开始探索
      </button>
    </div>
  </div>
</template>

<style scoped>
.pyt-help-modal {
  position: fixed;
  inset: 0;
  z-index: 1200;
  display: flex;
  align-items: center;
  justify-content: center;
}

.pyt-help-backdrop {
  position: absolute;
  inset: 0;
  background: rgba(28, 43, 24, 0.7);
  cursor: pointer;
}

.pyt-help-panel {
  position: relative;
  width: min(520px, calc(100vw - 32px));
  border: 3px solid var(--pyt-color-text-primary);
  border-radius: var(--pyt-radius-lg);
  background: var(--pyt-color-bg-elevated);
  box-shadow: 6px 6px 0 var(--pyt-color-text-primary), 0 16px 32px rgba(0, 0, 0, 0.3);
  padding: 28px 28px 24px;
}

.pyt-help-title {
  margin: 0 0 6px;
  font-family: var(--pyt-font-pixel);
  color: #d4af37;
  font-size: 28px;
  text-shadow: 2px 2px 0 var(--pyt-color-text-primary);
  letter-spacing: 1px;
}

.pyt-help-lede {
  margin: 0 0 18px;
  color: var(--pyt-color-text-hint);
  font-size: 14px;
}

.pyt-help-list {
  list-style: none;
  margin: 0 0 22px;
  padding: 0;
  display: grid;
  gap: 10px;
}

.pyt-help-list li {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background: var(--pyt-color-bg-soft);
  border: 2px solid var(--pyt-color-border);
  border-radius: 6px;
}

.pyt-help-key {
  display: inline-block;
  min-width: 44px;
  padding: 4px 8px;
  border: 2px solid var(--pyt-color-text-primary);
  border-radius: 4px;
  background: var(--pyt-color-gold-bright);
  color: var(--pyt-color-text-primary);
  font-family: var(--pyt-font-pixel-mono, 'Ark Pixel 12 Mono', monospace);
  font-weight: 700;
  font-size: 13px;
  text-align: center;
  box-shadow: 2px 2px 0 var(--pyt-color-text-primary);
}

.pyt-help-text {
  flex: 1;
  color: var(--pyt-color-text-primary);
  font-size: 14px;
  line-height: 1.5;
}

.pyt-help-btn {
  width: 100%;
  min-height: 44px;
  background: var(--pyt-color-gold-bright);
  border: 2px solid var(--pyt-color-text-primary);
  border-radius: 6px;
  color: var(--pyt-color-text-primary);
  font-weight: 800;
  font-size: 15px;
  cursor: pointer;
  box-shadow: 3px 3px 0 var(--pyt-color-text-primary);
}

.pyt-help-btn:active {
  transform: translate(2px, 2px);
  box-shadow: 1px 1px 0 var(--pyt-color-text-primary);
}

/* 首次进入 HUD 引导：向导高亮 callout + 底部工具栏一览 */
.pyt-help-guide {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 0 18px;
  padding: 12px 14px;
  background: linear-gradient(160deg, rgba(241, 196, 83, 0.28), rgba(110, 79, 29, 0.14));
  border: 2px solid var(--pyt-color-gold);
  border-radius: var(--pyt-radius-lg);
}
.pyt-help-guide__icon {
  flex: 0 0 auto;
  font-size: 24px;
  color: #d4af37;
  text-shadow: 0 0 6px rgba(241, 196, 83, 0.7);
}
.pyt-help-guide__text {
  margin: 0;
  font-size: 14px;
  line-height: 1.5;
  color: var(--pyt-color-text-primary);
}
.pyt-help-subtitle {
  margin: 0 0 8px;
  font-family: var(--pyt-font-pixel);
  font-size: 13px;
  color: var(--pyt-color-text-hint);
}
.pyt-help-dock {
  list-style: none;
  margin: 0 0 22px;
  padding: 0;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px 14px;
}
.pyt-help-dock li {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--pyt-color-text-primary);
}
.pyt-help-dock__ico {
  flex: 0 0 auto;
  width: 20px;
  text-align: center;
  color: #d4af37;
}
.pyt-help-dock__name {
  font-weight: 700;
}
.pyt-help-dock__desc {
  color: var(--pyt-color-text-hint);
  font-size: 12px;
}
@media (max-width: 520px) {
  .pyt-help-dock {
    grid-template-columns: 1fr;
  }
}
</style>
