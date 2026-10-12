<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import { useSessionStore } from '@stores/session';

const route = useRoute();
const router = useRouter();
const session = useSessionStore();
const navigationOpen = ref(false);
const isNarrow = ref(false);
let navigationMediaQuery: MediaQueryList | undefined;

const pageTitle = computed(() =>
  route.name === 'admin-problem-reviews' ? 'AI 出题审核' : 'AI 运维概览',
);

function syncNavigationViewport(event: MediaQueryListEvent): void {
  isNarrow.value = event.matches;
  if (!event.matches) navigationOpen.value = false;
}

function closeNavigation(): void {
  navigationOpen.value = false;
}

function logout(): void {
  session.logout();
  void router.replace({ name: 'login' });
}

onMounted(() => {
  navigationMediaQuery = window.matchMedia?.('(max-width: 767px)');
  isNarrow.value = navigationMediaQuery?.matches ?? false;
  navigationMediaQuery?.addEventListener('change', syncNavigationViewport);
});

onBeforeUnmount(() => {
  navigationMediaQuery?.removeEventListener('change', syncNavigationViewport);
});

watch(() => route.fullPath, closeNavigation);
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
        class="admin-sidebar"
        aria-label="管理员导航"
        :aria-hidden="isNarrow && !navigationOpen"
        :inert="isNarrow && !navigationOpen"
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
        </nav>

        <div class="admin-sidebar__account">
          <span
            class="admin-sidebar__avatar"
            aria-hidden="true"
          >管</span>
          <span class="admin-sidebar__identity">
            <strong>{{ session.displayName }}</strong>
            <small>ADMIN</small>
          </span>
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

      <div class="admin-shell__main">
        <header class="admin-topbar">
          <button
            class="admin-topbar__menu"
            type="button"
            aria-controls="admin-navigation"
            :aria-expanded="navigationOpen"
            @click="navigationOpen = !navigationOpen"
          >
            <span aria-hidden="true">☰</span>
            <span>导航</span>
          </button>
          <div class="admin-topbar__title">
            <span>管理控制台</span>
            <h1>{{ pageTitle }}</h1>
          </div>
          <span class="admin-topbar__role">管理员</span>
        </header>

        <div class="admin-shell__content">
          <RouterView v-slot="{ Component }">
            <Transition
              name="admin-page"
              mode="out-in"
            >
              <component :is="Component" />
            </Transition>
          </RouterView>
        </div>

        <footer class="admin-shell__footer">
          <span>Pythontopia 管理后台</span>
          <span>课程包 43 · Python语言基础</span>
        </footer>
      </div>
    </div>
  </AppLayout>
</template>

<style scoped>
.admin-shell {
  --admin-bg: #f5f7fb;
  --admin-surface: #fff;
  --admin-border: #e2e8f0;
  --admin-text: #172033;
  --admin-muted: #64748b;
  --admin-primary: #2563eb;
  --admin-primary-soft: #eff6ff;
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
  background: var(--admin-surface);
  transition: transform 180ms ease;
}

.admin-brand {
  display: flex;
  min-height: 76px;
  align-items: center;
  gap: 12px;
  padding: 16px 22px;
  border-bottom: 1px solid var(--admin-border);
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
  gap: 26px;
  padding: 24px 14px;
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
  min-height: 42px;
  align-items: center;
  gap: 11px;
  padding: 0 10px;
  border-radius: 8px;
  color: #475569;
  font-size: 13px;
  font-weight: 600;
  text-decoration: none;
  transition: background 140ms ease, color 140ms ease;
}

.admin-nav__link:hover,
.admin-nav__link.is-active {
  background: var(--admin-primary-soft);
  color: var(--admin-primary);
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
  align-items: center;
  gap: 10px;
  margin-top: auto;
  padding: 16px 20px;
  border-top: 1px solid var(--admin-border);
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
  flex: 0 0 auto;
  margin-left: auto;
  padding: 6px 9px;
  border: 1px solid var(--admin-border);
  border-radius: 7px;
  background: var(--admin-surface);
  color: var(--admin-muted);
  font: inherit;
  font-size: 11px;
  cursor: pointer;
}

.admin-sidebar__logout:hover {
  border-color: #bfdbfe;
  background: var(--admin-primary-soft);
  color: var(--admin-primary);
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

.admin-topbar {
  display: flex;
  min-height: 76px;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 12px 30px;
  border-bottom: 1px solid var(--admin-border);
  background: var(--admin-surface);
}

.admin-topbar__title > span {
  color: var(--admin-muted);
  font-size: 11px;
  letter-spacing: 0.04em;
}

.admin-topbar__title h1 {
  margin: 2px 0 0;
  color: var(--admin-text);
  font-size: 18px;
  font-weight: 700;
  line-height: 1.35;
}

.admin-topbar__role {
  padding: 5px 10px;
  border: 1px solid #bfdbfe;
  border-radius: 999px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 11px;
  font-weight: 700;
}

.admin-topbar__menu {
  display: none;
  min-height: 40px;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  border: 1px solid var(--admin-border);
  border-radius: 8px;
  background: #fff;
  color: var(--admin-text);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}

.admin-topbar__menu:focus-visible,
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
  padding: 28px 30px;
}

.admin-shell__footer {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 30px;
  border-top: 1px solid var(--admin-border);
  color: var(--admin-muted);
  font-size: 11px;
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
    width: min(288px, 86vw);
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

  .admin-topbar {
    min-height: 68px;
    padding: 10px 16px;
  }

  .admin-topbar__menu {
    display: inline-flex;
  }

  .admin-topbar__title {
    margin-right: auto;
  }

  .admin-topbar__role {
    display: none;
  }

  .admin-shell__content {
    padding: 20px 16px;
  }

  .admin-shell__footer {
    flex-wrap: wrap;
    padding: 14px 16px;
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
