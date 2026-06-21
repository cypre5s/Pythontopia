<script setup lang="ts">
/** 通用空状态：图标 + 标题 + 说明 + 可选操作插槽，统一各列表/面板"暂无内容"的引导。 */
defineOptions({ name: 'EmptyState' });

withDefaults(
  defineProps<{
    icon?: string;
    title?: string;
    description?: string;
  }>(),
  {
    icon: '📭',
    title: '暂无内容',
    description: '',
  },
);
</script>

<template>
  <div
    class="pyt-empty"
    role="status"
    data-testid="empty-state"
  >
    <div
      class="pyt-empty__icon"
      aria-hidden="true"
    >
      {{ icon }}
    </div>
    <p class="pyt-empty__title">
      {{ title }}
    </p>
    <p
      v-if="description"
      class="pyt-empty__desc"
    >
      {{ description }}
    </p>
    <div class="pyt-empty__action">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.pyt-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 28px 18px;
  text-align: center;
  color: var(--pyt-color-text-hint, #6b5a45);
}
.pyt-empty__icon {
  font-size: 30px;
  opacity: 0.7;
}
.pyt-empty__title {
  margin: 0;
  font-weight: 700;
  color: var(--pyt-color-text-primary, #2f2418);
}
.pyt-empty__desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  max-width: 360px;
}
.pyt-empty__action:empty {
  display: none;
}
.pyt-empty__action {
  margin-top: 6px;
}
</style>
