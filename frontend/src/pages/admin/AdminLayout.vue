<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import { useSessionStore } from '@stores/session';

const route = useRoute();
const router = useRouter();
const session = useSessionStore();
const navigationOpen = ref(false);
const navigationToggle = ref<HTMLButtonElement | null>(null);
const navigationMenu = ref<HTMLElement | null>(null);
const navigationMediaQuery = window.matchMedia?.('(max-width: 767px)');
const isNarrow = ref(navigationMediaQuery?.matches ?? false);

function syncNavigationViewport(event: MediaQueryListEvent): void {
  isNarrow.value = event.matches;
  if (!event.matches) navigationOpen.value = false;
}

function closeNavigation(): void {
  navigationOpen.value = false;
}

function handleNavigationKeydown(event: KeyboardEvent): void {
  if (!isNarrow.value || !navigationOpen.value) return;
  if (event.key === 'Escape') {
    event.preventDefault();
    closeNavigation();
    return;
  }
  if (event.key !== 'Tab') return;
  const focusable = Array.from(
    navigationMenu.value?.querySelectorAll<HTMLElement>('a[href], button:not(:disabled)') ?? [],
  );
  const first = focusable[0];
  const last = focusable[focusable.length - 1];
  if (!first || !last) return;
  if (
    event.shiftKey
    && (document.activeElement === first || !navigationMenu.value?.contains(document.activeElement))
  ) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first.focus();
  }
}

function logout(): void {
  session.logout();
  void router.replace({ name: 'login' });
}

onMounted(() => {
  isNarrow.value = navigationMediaQuery?.matches ?? false;
  navigationMediaQuery?.addEventListener('change', syncNavigationViewport);
});

onBeforeUnmount(() => {
  navigationMediaQuery?.removeEventListener('change', syncNavigationViewport);
});

watch(() => route.fullPath, closeNavigation);
watch(navigationOpen, (open) => {
  if (!isNarrow.value) return;
  void nextTick(() => {
    if (open) navigationMenu.value?.querySelector<HTMLElement>('a[href]')?.focus();
    else navigationToggle.value?.focus();
  });
});
</script>

<template>
  <AppLayout>
    <div
      class="admin-shell"
      :class="{ 'admin-shell--nav-open': navigationOpen }"
    >
      <button
        v-if="navigationOpen && isNarrow"
        class="admin-shell__scrim"
        type="button"
        aria-label="关闭管理导航"
        @click="closeNavigation"
      />

      <aside
        id="admin-navigation"
        ref="navigationMenu"
        class="admin-sidebar"
        aria-label="管理员导航"
        :role="isNarrow && navigationOpen ? 'dialog' : undefined"
        :aria-modal="isNarrow && navigationOpen ? 'true' : undefined"
        :aria-hidden="isNarrow && !navigationOpen"
        :inert="isNarrow && !navigationOpen"
        @keydown="handleNavigationKeydown"
      >
        <RouterLink
          class="admin-brand"
          :to="{ name: 'admin-operations' }"
          @click="closeNavigation"
        >
          <span
            class="admin-brand__mark"
            aria-hidden="true"
          >P</span>
          <span>
            <strong>Pythontopia</strong>
            <small>管理后台</small>
          </span>
        </RouterLink>

        <nav
          class="admin-nav"
          aria-label="管理功能"
        >
          <section
            class="admin-nav__group"
            aria-labelledby="admin-nav-platform"
          >
            <h2 id="admin-nav-platform">
              平台运行
            </h2>
            <RouterLink
              class="admin-nav__link"
              :to="{ name: 'admin-operations' }"
              exact-active-class="is-active"
              @click="closeNavigation"
            >
              <span
                class="admin-nav__icon"
                aria-hidden="true"
              >01</span>
              AI 运维概览
            </RouterLink>
          </section>

          <section
            class="admin-nav__group"
            aria-labelledby="admin-nav-review"
          >
            <h2 id="admin-nav-review">
              内容审核
            </h2>
            <RouterLink
              class="admin-nav__link"
              :to="{ name: 'admin-problem-reviews' }"
              exact-active-class="is-active"
              @click="closeNavigation"
            >
              <span
                class="admin-nav__icon"
                aria-hidden="true"
              >02</span>
              AI 出题审核
            </RouterLink>
          </section>

          <section
            class="admin-nav__group"
            aria-labelledby="admin-nav-accounts"
          >
            <h2 id="admin-nav-accounts">
              账号管理
            </h2>
            <RouterLink
              class="admin-nav__link"
              :to="{ name: 'admin-users' }"
              exact-active-class="is-active"
              @click="closeNavigation"
            >
              <span
                class="admin-nav__icon"
                aria-hidden="true"
              >03</span>
              用户管理
            </RouterLink>
          </section>
        </nav>

        <div class="admin-sidebar__account">
          <div class="admin-sidebar__account-user">
            <span
              class="admin-sidebar__avatar"
              aria-hidden="true"
            >管</span>
            <span class="admin-sidebar__identity">
              <strong>{{ session.displayName }}</strong>
              <small>ADMIN</small>
            </span>
          </div>
          <button
            class="admin-sidebar__logout"
            type="button"
            aria-label="退出管理员账号"
            data-testid="admin-logout"
            @click="logout"
          >
            退出
          </button>
        </div>
      </aside>

      <div
        class="admin-shell__main"
        :inert="isNarrow && navigationOpen"
      >
        <button
          ref="navigationToggle"
          class="admin-shell__navigation-toggle"
          type="button"
          aria-controls="admin-navigation"
          :aria-expanded="navigationOpen"
          @click="navigationOpen = !navigationOpen"
        >
          <span aria-hidden="true">☰</span>
          管理导航
        </button>

        <main class="admin-shell__content">
          <RouterView v-slot="{ Component }">
            <Transition
              name="admin-page"
              mode="out-in"
            >
              <component :is="Component" />
            </Transition>
          </RouterView>
        </main>

        <footer class="admin-shell__footer">
          <div class="admin-shell__footer-divider" />
          <div class="admin-shell__footer-row">
            <span class="admin-shell__footer-title">Pythontopia 管理台</span>
            <span class="admin-shell__footer-version">课程包 43 · Python语言基础</span>
          </div>
        </footer>
      </div>
    </div>
  </AppLayout>
</template>

<style scoped>
.admin-shell {
  --admin-bg: linear-gradient(180deg, #f8fbff 0%, #f3f7fc 100%);
  --admin-surface: #fff;
  --admin-border: #e2e8f0;
  --admin-text: #0f172a;
  --admin-muted: #64748b;
  --admin-primary: #2563eb;
  --admin-primary-hover: #1d4ed8;
  --admin-primary-soft: rgb(37 99 235 / 8%);
  --admin-panel-muted: #f6f9fc;
  --admin-table-header: #eef4fb;
  --admin-table-hover: #f8fbff;
  --admin-panel-radius: 16px;
  --admin-panel-shadow: 0 6px 18px rgb(15 23 42 / 8%);
  position: relative;
  display: flex;
  min-height: 100vh;
  min-height: 100dvh;
  background: var(--admin-bg);
  color: var(--admin-text);
  font-family: var(--pyt-font-read, 'PingFang SC', 'Microsoft YaHei', sans-serif);
  line-height: 1.5;
}

.admin-sidebar {
  position: fixed;
  z-index: 30;
  inset: 0 auto 0 0;
  display: flex;
  width: 240px;
  flex-direction: column;
  border-right: 1px solid var(--admin-border);
  background: rgb(255 255 255 / 92%);
  backdrop-filter: blur(18px);
  box-shadow: 10px 0 30px rgb(15 23 42 / 4%);
  transition: transform 180ms ease;
}

.admin-brand {
  display: flex;
  min-height: 76px;
  align-items: center;
  gap: 12px;
  padding: 16px 22px;
  border-bottom: 1px solid var(--admin-border);
  background: rgb(255 255 255 / 60%);
  color: inherit;
  text-decoration: none;
}

.admin-brand__mark {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  border-radius: 10px;
  background: var(--admin-primary);
  color: #fff;
  font-size: 20px;
  font-weight: 800;
}

.admin-brand strong,
.admin-brand small,
.admin-sidebar__identity strong,
.admin-sidebar__identity small {
  display: block;
}

.admin-brand strong {
  font-size: 15px;
  line-height: 1.35;
}

.admin-brand small,
.admin-sidebar__identity small {
  margin-top: 3px;
  color: var(--admin-muted);
  font-size: 11px;
  letter-spacing: 0.08em;
}

.admin-nav {
  display: grid;
  gap: 22px;
  padding: 16px 12px;
}

.admin-nav__group h2 {
  margin: 0 10px 9px;
  color: #8792a5;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.admin-nav__link {
  display: flex;
  min-height: 46px;
  align-items: center;
  gap: 11px;
  padding: 0 16px;
  border-radius: 12px;
  color: #475569;
  font-size: 13px;
  font-weight: 600;
  text-decoration: none;
  transition: background 140ms ease, color 140ms ease, box-shadow 140ms ease;
}

.admin-nav__link:hover:not(.is-active) {
  background: var(--admin-primary-soft);
  color: var(--admin-primary-hover);
}

.admin-nav__link.is-active {
  background: linear-gradient(90deg, #2563eb 0%, #3b82f6 100%);
  color: #fff;
  box-shadow: 0 12px 24px rgb(37 99 235 / 22%);
}

.admin-nav__link.is-active .admin-nav__icon {
  border-color: rgb(255 255 255 / 36%);
  background: rgb(255 255 255 / 14%);
  color: #fff;
}

.admin-nav__icon {
  display: grid;
  width: 27px;
  height: 27px;
  place-items: center;
  border: 1px solid #dbe6f7;
  border-radius: 7px;
  color: var(--admin-primary);
  font-size: 9px;
  font-variant-numeric: tabular-nums;
}

.admin-sidebar__account {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 12px;
  margin-top: auto;
  padding: 12px;
  border-top: 1px solid var(--admin-border);
}

.admin-sidebar__account-user {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
}

.admin-sidebar__avatar {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border-radius: 50%;
  background: #e8eef8;
  color: #334155;
  font-size: 13px;
  font-weight: 700;
}

.admin-sidebar__identity {
  min-width: 0;
}

.admin-sidebar__identity strong {
  overflow: hidden;
  max-width: 158px;
  color: var(--admin-text);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.admin-sidebar__logout {
  display: inline-flex;
  width: 100%;
  height: 42px;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 12px;
  background: rgba(239, 68, 68, 0.1);
  color: #dc2626;
  font: inherit;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.admin-sidebar__logout:hover {
  background: rgba(239, 68, 68, 0.16);
  color: #b91c1c;
}

.admin-shell__main {
  display: flex;
  min-width: 0;
  min-height: 100vh;
  min-height: 100dvh;
  flex: 1;
  flex-direction: column;
  margin-left: 240px;
}

.admin-shell__navigation-toggle {
  display: none;
  min-height: 44px;
  align-items: center;
  gap: 8px;
  align-self: flex-start;
  margin: 16px 16px 0;
  padding: 0 16px;
  border: 1px solid rgb(148 163 184 / 30%);
  border-radius: 8px;
  background: #fff;
  color: var(--admin-text);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}

.admin-shell__navigation-toggle:focus-visible,
.admin-nav__link:focus-visible,
.admin-brand:focus-visible {
  outline: 3px solid #93c5fd;
  outline-offset: 2px;
}

.admin-shell__content {
  width: 100%;
  max-width: 1440px;
  flex: 1;
  align-self: center;
  padding: 24px 28px 28px;
}

.admin-shell__footer {
  padding: 0 28px 28px;
  font-size: 13px;
}

.admin-shell__footer-divider {
  height: 1px;
  margin-bottom: 16px;
  background: rgb(148 163 184 / 18%);
}

.admin-shell__footer-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.admin-shell__footer-title {
  color: #334155;
  font-weight: 600;
}

.admin-shell__footer-version {
  color: var(--admin-muted);
  font-size: 12px;
}

.admin-shell__scrim {
  display: none;
}

.admin-page-enter-active,
.admin-page-leave-active {
  transition: opacity 120ms ease, transform 120ms ease;
}

.admin-page-enter-from,
.admin-page-leave-to {
  opacity: 0;
  transform: translateY(4px);
}

@media (max-width: 767px) {
  .admin-sidebar {
    width: min(320px, 90vw);
    box-shadow: 12px 0 32px rgb(15 23 42 / 14%);
    transform: translateX(-105%);
  }

  .admin-shell--nav-open .admin-sidebar {
    transform: translateX(0);
  }

  .admin-shell__scrim {
    position: fixed;
    z-index: 20;
    inset: 0;
    display: block;
    border: 0;
    background: rgb(15 23 42 / 38%);
  }

  .admin-shell__main {
    margin-left: 0;
  }

  .admin-shell__navigation-toggle {
    display: inline-flex;
  }

  .admin-shell__content {
    padding: 20px 16px;
  }

  .admin-shell__footer {
    padding: 0 16px 16px;
  }

  .admin-shell__footer-row {
    flex-wrap: wrap;
    gap: 8px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .admin-sidebar,
  .admin-page-enter-active,
  .admin-page-leave-active {
    transition: none;
  }
}
</style>
