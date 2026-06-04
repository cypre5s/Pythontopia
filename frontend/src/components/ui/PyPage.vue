<script setup lang="ts">
/**
 * PyPage：内容页统一外壳（统一标准 §3）。标准页头（返回 + 标题 + 右侧 actions 槽）+ 滚动内容区。
 * 与 AppLayout 组合使用：<AppLayout><PyPage title back @back="...">…</PyPage></AppLayout>。
 * 各页不再各自实现页头/返回栏/内边距。
 */
withDefaults(
  defineProps<{
    title: string;
    subtitle?: string;
    back?: boolean;
  }>(),
  { subtitle: '', back: false },
);

const emit = defineEmits<{ (event: 'back'): void }>();
</script>

<template>
  <section
    class="py-page"
    data-testid="py-page"
  >
    <header class="py-page__head">
      <button
        v-if="back"
        type="button"
        class="py-page__back"
        data-testid="py-page-back"
        @click="emit('back')"
      >
        ← 返回小镇
      </button>
      <div class="py-page__titles">
        <h1 class="py-page__title">
          {{ title }}
        </h1>
        <p
          v-if="subtitle"
          class="py-page__subtitle"
        >
          {{ subtitle }}
        </p>
      </div>
      <span class="py-page__spacer" />
      <div
        v-if="$slots.actions"
        class="py-page__actions"
      >
        <slot name="actions" />
      </div>
    </header>

    <div class="py-page__body">
      <slot />
    </div>
  </section>
</template>

<style scoped>
.py-page {
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-3);
  padding: var(--pyt-space-3) var(--pyt-space-4);
  height: calc(100dvh - 56px);
  background:
    linear-gradient(180deg, rgba(255, 247, 230, 0.5), rgba(243, 228, 190, 0.7)),
    #fef6dd;
  overflow: hidden;
}
.py-page__head {
  display: flex;
  align-items: center;
  gap: var(--pyt-space-3);
  padding: var(--pyt-space-2) var(--pyt-space-3);
  border: 3px solid var(--pyt-color-text-primary);
  background: var(--pyt-color-bg-elevated);
  box-shadow: 4px 4px 0 var(--pyt-color-text-primary);
  font-family: var(--pyt-font-pixel);
  flex: 0 0 auto;
}
.py-page__back {
  font-family: inherit;
  font-size: var(--pyt-font-size-sm);
  padding: var(--pyt-space-1) var(--pyt-space-3);
  background: var(--pyt-color-warning-bg);
  border: 2px solid var(--pyt-color-text-primary);
  cursor: pointer;
  box-shadow: 2px 2px 0 var(--pyt-color-text-primary);
}
.py-page__back:active {
  transform: translate(2px, 2px);
  box-shadow: none;
}
.py-page__titles {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}
.py-page__title {
  margin: 0;
  font-size: var(--pyt-font-size-lg);
  font-weight: 700;
  color: var(--pyt-color-text-primary);
}
.py-page__subtitle {
  margin: 0;
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-muted);
}
.py-page__spacer {
  flex: 1 1 auto;
}
.py-page__actions {
  display: flex;
  align-items: center;
  gap: var(--pyt-space-2);
  flex-wrap: wrap;
}
.py-page__body {
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

@media (max-width: 640px) {
  .py-page {
    height: auto;
    min-height: calc(100dvh - 56px);
    overflow: visible;
  }
  .py-page__head {
    flex-wrap: wrap;
  }
}
</style>
