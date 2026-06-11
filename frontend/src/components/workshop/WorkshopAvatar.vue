<script setup lang="ts">
/**
 * WorkshopAvatar —— 百晓（工坊主理人）像素头像 + 名牌。
 *
 * - 32×32 像素渲染（image-rendering: pixelated），名牌「百晓」小字在下方。
 * - streaming=true：头像轻微上下浮动「呼吸」，并亮起 ● 实时指示点；
 *   prefers-reduced-motion 下浮动/闪烁全部停掉，仅余静态 ● 指示点表达「正在作答」。
 * - 头像缺图 @error → 回退「百」字方块（同尺寸、像素边框），永不白屏。
 *
 * 资产注：执行蓝本 §6 写的 /assets/pixel/portraits/portrait_workshop_master.png 在仓库中
 * 并不存在；真实已审计资产为 heads/portrait_workshop_master_head.png（与 agentActors 的
 * 兜底「百晓师傅」头像同源），故此处用真实路径，props 契约不受影响。
 */
import { ref } from 'vue';

withDefaults(defineProps<{ streaming?: boolean }>(), { streaming: false });

/** 百晓头像（已审计像素资产；与多智能体工作室兜底人物同源）。 */
const AVATAR_SRC = '/assets/pixel/portraits/heads/portrait_workshop_master_head.png';

const imgError = ref(false);

function onImgError(): void {
  imgError.value = true;
}
</script>

<template>
  <figure
    class="avatar"
    :class="{ 'avatar--streaming': streaming }"
    data-testid="workshop-avatar"
  >
    <span class="avatar__frame">
      <img
        v-if="!imgError"
        :src="AVATAR_SRC"
        alt="百晓"
        class="avatar__img"
        data-testid="workshop-avatar-img"
        @error="onImgError"
      >
      <span
        v-else
        class="avatar__fallback"
        data-testid="workshop-avatar-fallback"
        aria-hidden="true"
      >百</span>
      <span
        v-if="streaming"
        class="avatar__dot"
        data-testid="workshop-avatar-dot"
        aria-hidden="true"
      >●</span>
    </span>
    <figcaption class="avatar__name">
      百晓
    </figcaption>
  </figure>
</template>

<style scoped>
.avatar {
  margin: 0;
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  font-family: var(--pyt-font-pixel);
}
.avatar__frame {
  position: relative;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff8df;
  border: 2px solid #8c6a23;
  border-radius: var(--pyt-radius-lg);
  box-shadow: 2px 2px 0 rgba(140, 106, 35, 0.5);
  image-rendering: pixelated;
}
.avatar--streaming .avatar__frame {
  animation: avatar-breathe 1.6s ease-in-out infinite;
}
.avatar__img {
  width: 32px;
  height: 32px;
  object-fit: cover;
  image-rendering: pixelated;
  border-radius: calc(var(--pyt-radius-lg) - 2px);
}
.avatar__fallback {
  font-size: 16px;
  font-weight: 700;
  line-height: 1;
  color: var(--pyt-color-text-primary);
}
.avatar__dot {
  position: absolute;
  top: -5px;
  right: -5px;
  font-size: 8px;
  line-height: 1;
  color: var(--pyt-color-success);
  text-shadow: 0 0 2px #fff8df;
  animation: avatar-dot 1.1s ease-in-out infinite;
}
.avatar__name {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 1px;
  color: var(--pyt-color-text-hint);
}
@keyframes avatar-breathe {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-2px); }
}
@keyframes avatar-dot {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.35; }
}
@media (prefers-reduced-motion: reduce) {
  .avatar--streaming .avatar__frame,
  .avatar__dot {
    animation: none;
  }
}
</style>
