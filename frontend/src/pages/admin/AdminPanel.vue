<script setup lang="ts">
withDefaults(defineProps<{ title: string; description?: string }>(), {
  description: '',
});
</script>

<template>
  <section class="admin-panel">
    <header class="admin-panel__header">
      <div>
        <h2>{{ title }}</h2>
        <p v-if="description">
          {{ description }}
        </p>
      </div>
      <div
        v-if="$slots.actions"
        class="admin-panel__actions"
      >
        <slot name="actions" />
      </div>
    </header>
    <div class="admin-panel__body">
      <slot />
    </div>
    <footer
      v-if="$slots.footer"
      class="admin-panel__footer"
    >
      <slot name="footer" />
    </footer>
  </section>
</template>

<style scoped>
.admin-panel {
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--admin-border);
  border-radius: 12px;
  background: var(--admin-surface);
  box-shadow: 0 2px 8px rgb(15 23 42 / 4%);
}

.admin-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px;
  border-bottom: 1px solid var(--admin-border);
}

.admin-panel__header h2 {
  margin: 0;
  color: var(--admin-text);
  font-size: 17px;
  font-weight: 700;
  line-height: 1.4;
}

.admin-panel__header p {
  margin: 4px 0 0;
  color: var(--admin-muted);
  font-size: 13px;
  line-height: 1.5;
}

.admin-panel__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}

.admin-panel__body {
  min-width: 0;
  padding: 20px;
}

.admin-panel__footer {
  padding: 0 20px 20px;
}

@media (max-width: 640px) {
  .admin-panel__header,
  .admin-panel__body {
    padding: 16px;
  }

  .admin-panel__header {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
