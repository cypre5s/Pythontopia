<script setup lang="ts">
withDefaults(defineProps<{ title: string; description?: string }>(), {
  description: '',
});
</script>

<template>
  <section
    class="admin-panel"
    :class="{ 'admin-panel--with-footer': Boolean($slots.footer) }"
  >
    <header class="admin-panel__header">
      <div>
        <h2>{{ title }}</h2>
        <p v-if="description">
          {{ description }}
        </p>
      </div>
      <div
        v-if="$slots.toolbar || $slots.actions"
        class="admin-panel__tools"
      >
        <div
          v-if="$slots.toolbar"
          class="admin-panel__toolbar"
        >
          <slot name="toolbar" />
        </div>
        <div
          v-if="$slots.actions"
          class="admin-panel__actions"
        >
          <slot name="actions" />
        </div>
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
  border: 1px solid var(--admin-panel-border);
  border-radius: var(--admin-panel-radius);
  background: var(--admin-panel-bg);
  box-shadow: var(--admin-panel-shadow);
}

.admin-panel__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding: 20px 22px 16px;
  border-bottom: 1px solid rgb(148 163 184 / 12%);
  background: linear-gradient(180deg, rgb(255 255 255 / 98%) 0%, rgb(246 249 252 / 92%) 100%);
}

.admin-panel__header h2 {
  margin: 0;
  color: var(--admin-text-strong);
  font-size: 20px;
  font-weight: 700;
  line-height: 1.35;
  letter-spacing: -0.01em;
}

.admin-panel__header p {
  margin: 4px 0 0;
  color: var(--admin-text-muted);
  font-size: 13px;
  line-height: 1.5;
}

.admin-panel__actions {
  display: flex;
  flex: 1 1 auto;
  min-width: 0;
  align-items: center;
  justify-content: flex-end;
  gap: var(--admin-toolbar-gap, 10px);
  flex-wrap: wrap;
}

.admin-panel__tools {
  display: flex;
  min-width: 0;
  flex: 1 1 480px;
  align-items: center;
  justify-content: flex-end;
  gap: var(--admin-toolbar-gap, 10px);
  flex-wrap: wrap;
}

.admin-panel__toolbar {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--admin-toolbar-gap, 10px);
  flex-wrap: wrap;
}

.admin-panel__body {
  min-width: 0;
  padding: 18px 22px 22px;
}

.admin-panel--with-footer .admin-panel__body {
  padding-bottom: 0;
}

.admin-panel__footer {
  padding: 18px 22px 22px;
}

@media (max-width: 640px) {
  .admin-panel__header,
  .admin-panel__body {
    padding: 16px;
  }

  .admin-panel--with-footer .admin-panel__body {
    padding-bottom: 0;
  }

  .admin-panel__toolbar {
    align-items: stretch;
  }

  .admin-panel__tools {
    width: 100%;
    flex: 0 1 auto;
    align-items: stretch;
    justify-content: flex-start;
  }

  .admin-panel__actions {
    justify-content: flex-start;
  }

  .admin-panel__footer {
    padding: 18px 16px 22px;
  }

  .admin-panel__header {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
