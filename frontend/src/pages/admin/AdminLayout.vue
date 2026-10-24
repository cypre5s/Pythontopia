<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import { useSessionStore } from '@stores/session';

const route = useRoute();
const router = useRouter();
const session = useSessionStore();
type AdminNavigationGroupKey = 'platform' | 'review' | 'accounts';

function navigationGroupForRoute(routeName: unknown): AdminNavigationGroupKey | null {
  if (routeName === 'admin-operations' || routeName === 'admin-usage' || routeName === 'admin-ai-config') return 'platform';
  if (routeName === 'admin-problem-reviews' || routeName === 'admin-course-resources') return 'review';
  if (routeName === 'admin-users' || routeName === 'admin-student-detail' || routeName === 'admin-classrooms') return 'accounts';
  return null;
}

const initialNavigationGroup = navigationGroupForRoute(route.name);
const expandedNavigationGroups = ref<Set<AdminNavigationGroupKey>>(
  new Set(initialNavigationGroup ? [initialNavigationGroup] : []),
);
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

function isNavigationGroupOpen(group: AdminNavigationGroupKey): boolean {
  return expandedNavigationGroups.value.has(group);
}

function toggleNavigationGroup(group: AdminNavigationGroupKey): void {
  const next = new Set(expandedNavigationGroups.value);
  if (next.has(group)) next.delete(group);
  else next.add(group);
  expandedNavigationGroups.value = next;
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
  ).filter((element) => !element.closest('[aria-hidden="true"]'));
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
watch(() => route.name, (name) => {
  const activeGroup = navigationGroupForRoute(name);
  if (activeGroup) {
    const next = new Set(expandedNavigationGroups.value);
    next.add(activeGroup);
    expandedNavigationGroups.value = next;
  }
});
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
            <h2
              id="admin-nav-platform"
              class="admin-nav__heading"
            >
              <button
                class="admin-nav__group-toggle"
                type="button"
                data-testid="admin-nav-toggle-platform"
                aria-controls="admin-nav-items-platform"
                :aria-expanded="isNavigationGroupOpen('platform')"
                @click="toggleNavigationGroup('platform')"
              >
                <span
                  class="admin-nav__icon"
                  aria-hidden="true"
                >
                  <svg
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="1.8"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    aria-hidden="true"
                  >
                    <rect
                      x="3"
                      y="3"
                      width="8"
                      height="8"
                      rx="1.5"
                    />
                    <rect
                      x="13"
                      y="3"
                      width="8"
                      height="5"
                      rx="1.5"
                    />
                    <rect
                      x="13"
                      y="10"
                      width="8"
                      height="11"
                      rx="1.5"
                    />
                    <rect
                      x="3"
                      y="13"
                      width="8"
                      height="8"
                      rx="1.5"
                    />
                  </svg>
                </span>
                <span class="admin-nav__group-label">平台运行</span>
                <span
                  class="admin-nav__chevron"
                  aria-hidden="true"
                >⌄</span>
              </button>
            </h2>
            <div
              v-show="isNavigationGroupOpen('platform')"
              id="admin-nav-items-platform"
              class="admin-nav__items"
              data-testid="admin-nav-items-platform"
              :aria-hidden="!isNavigationGroupOpen('platform')"
              :inert="!isNavigationGroupOpen('platform')"
            >
              <RouterLink
                class="admin-nav__link"
                :to="{ name: 'admin-operations' }"
                exact-active-class="is-active"
                data-testid="admin-nav-item-operations"
                @click="closeNavigation"
              >
                AI 运维概览
              </RouterLink>
              <RouterLink
                class="admin-nav__link"
                :to="{ name: 'admin-ai-config' }"
                exact-active-class="is-active"
                data-testid="admin-nav-item-ai-config"
                @click="closeNavigation"
              >
                AI API 配置
              </RouterLink>
              <RouterLink
                class="admin-nav__link"
                :to="{ name: 'admin-usage' }"
                exact-active-class="is-active"
                data-testid="admin-nav-item-usage"
                @click="closeNavigation"
              >
                资源消耗
              </RouterLink>
            </div>
          </section>

          <section
            class="admin-nav__group"
            aria-labelledby="admin-nav-review"
          >
            <h2
              id="admin-nav-review"
              class="admin-nav__heading"
            >
              <button
                class="admin-nav__group-toggle"
                type="button"
                data-testid="admin-nav-toggle-review"
                aria-controls="admin-nav-items-review"
                :aria-expanded="isNavigationGroupOpen('review')"
                @click="toggleNavigationGroup('review')"
              >
                <span
                  class="admin-nav__icon"
                  aria-hidden="true"
                >
                  <svg
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="1.8"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    aria-hidden="true"
                  >
                    <path d="M12 3 20 6v5c0 5-3.4 8.4-8 10-4.6-1.6-8-5-8-10V6l8-3Z" />
                    <path d="m8.5 12 2.2 2.2 4.8-5" />
                  </svg>
                </span>
                <span class="admin-nav__group-label">内容审核</span>
                <span
                  class="admin-nav__chevron"
                  aria-hidden="true"
                >⌄</span>
              </button>
            </h2>
            <div
              v-show="isNavigationGroupOpen('review')"
              id="admin-nav-items-review"
              class="admin-nav__items"
              data-testid="admin-nav-items-review"
              :aria-hidden="!isNavigationGroupOpen('review')"
              :inert="!isNavigationGroupOpen('review')"
            >
              <RouterLink
                class="admin-nav__link"
                :to="{ name: 'admin-problem-reviews' }"
                exact-active-class="is-active"
                data-testid="admin-nav-item-problem-reviews"
                @click="closeNavigation"
              >
                AI 出题审核
              </RouterLink>
              <RouterLink
                class="admin-nav__link"
                :to="{ name: 'admin-course-resources' }"
                exact-active-class="is-active"
                data-testid="admin-nav-item-course-resources"
                @click="closeNavigation"
              >
                课程资源
              </RouterLink>
            </div>
          </section>

          <section
            class="admin-nav__group"
            aria-labelledby="admin-nav-accounts"
          >
            <h2
              id="admin-nav-accounts"
              class="admin-nav__heading"
            >
              <button
                class="admin-nav__group-toggle"
                type="button"
                data-testid="admin-nav-toggle-accounts"
                aria-controls="admin-nav-items-accounts"
                :aria-expanded="isNavigationGroupOpen('accounts')"
                @click="toggleNavigationGroup('accounts')"
              >
                <span
                  class="admin-nav__icon"
                  aria-hidden="true"
                >
                  <svg
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="1.8"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    aria-hidden="true"
                  >
                    <path d="M16 20v-1.5a4 4 0 0 0-4-4H7a4 4 0 0 0-4 4V20" />
                    <circle
                      cx="9.5"
                      cy="7"
                      r="4"
                    />
                    <path d="M17 11a4 4 0 0 0 0-8M21 20v-1.5a4 4 0 0 0-3-3.87" />
                  </svg>
                </span>
                <span class="admin-nav__group-label">账号管理</span>
                <span
                  class="admin-nav__chevron"
                  aria-hidden="true"
                >⌄</span>
              </button>
            </h2>
            <div
              v-show="isNavigationGroupOpen('accounts')"
              id="admin-nav-items-accounts"
              class="admin-nav__items"
              data-testid="admin-nav-items-accounts"
              :aria-hidden="!isNavigationGroupOpen('accounts')"
              :inert="!isNavigationGroupOpen('accounts')"
            >
              <RouterLink
                class="admin-nav__link"
                :to="{ name: 'admin-users' }"
                exact-active-class="is-active"
                data-testid="admin-nav-item-users"
                @click="closeNavigation"
              >
                用户管理
              </RouterLink>
              <RouterLink
                class="admin-nav__link"
                :to="{ name: 'admin-classrooms' }"
                exact-active-class="is-active"
                data-testid="admin-nav-item-classrooms"
                @click="closeNavigation"
              >
                班级管理
              </RouterLink>
            </div>
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
  --admin-shell-bg: linear-gradient(180deg, #f8fbff 0%, #f3f7fc 100%);
  --admin-panel-bg: #fff;
  --admin-panel-border: #e2e8f0;
  --admin-text-strong: #0f172a;
  --admin-text-muted: #64748b;
  --primary-color: #2563eb;
  --primary-hover: #1d4ed8;
  --admin-primary-soft: rgb(37 99 235 / 8%);
  --admin-focus: #3b82f6;
  --control-height-md: 36px;
  --admin-toolbar-gap: 12px;
  --table-header-height: 44px;
  --table-row-height: 52px;
  --admin-panel-muted-bg: #f6f9fc;
  --admin-table-header-bg: #eef4fb;
  --admin-table-hover-bg: #f8fbff;
  --admin-panel-radius: 16px;
  --admin-panel-shadow: 0 6px 18px rgb(15 23 42 / 8%);
  --admin-control-height: 36px;
  --admin-control-radius: 8px;
  --admin-table-header-height: 44px;
  --admin-table-row-height: 52px;
  --admin-dialog-width: 520px;
  --admin-dialog-radius: 16px;
  --admin-dialog-shadow: 0 20px 60px rgb(15 23 42 / 20%);
  position: relative;
  display: flex;
  min-height: 100vh;
  min-height: 100dvh;
  background: var(--admin-shell-bg);
  color: var(--admin-text-strong);
  font-family: var(--pyt-font-read, 'PingFang SC', 'Microsoft YaHei', sans-serif);
  line-height: 1.5;
}

.admin-shell :deep(button) {
  box-shadow: none;
}

.admin-shell :deep(button:active) {
  transform: none;
  box-shadow: none;
}

.admin-shell :deep(.admin-control) {
  min-height: var(--control-height-md);
  border: 1px solid var(--admin-panel-border);
  border-radius: 8px;
  padding: 0 10px;
  background: #fff;
  color: var(--admin-text-strong);
  font: inherit;
  font-size: 13px;
}

.admin-shell :deep(textarea.admin-control) {
  min-height: 96px;
  padding: 9px 10px;
  resize: vertical;
}

.admin-shell :deep(.admin-control:focus-visible),
.admin-shell :deep(.admin-button:focus-visible) {
  outline: 2px solid var(--admin-focus);
  outline-offset: 2px;
}

.admin-shell :deep(.admin-control:disabled),
.admin-shell :deep(.admin-button:disabled) {
  cursor: not-allowed;
  opacity: 0.58;
}

.admin-shell :deep(.admin-button) {
  display: inline-flex;
  min-height: var(--control-height-md);
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  padding: 0 18px;
  background: #fff;
  color: #334155;
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.2;
  white-space: nowrap;
  cursor: pointer;
  transition: border-color 120ms ease, background 120ms ease, color 120ms ease;
}

.admin-shell :deep(.admin-button:hover:not(:disabled)) {
  border-color: var(--admin-focus);
  color: var(--primary-hover);
}

.admin-shell :deep(.admin-button--primary) {
  border-color: var(--primary-color);
  background: var(--primary-color);
  color: #fff;
}

.admin-shell :deep(.admin-button--primary:hover:not(:disabled)) {
  border-color: var(--primary-hover);
  background: var(--primary-hover);
  color: #fff;
}

.admin-shell :deep(.admin-button--positive) {
  border-color: #bbf7d0;
  background: #f0fdf4;
  color: #166534;
}

.admin-shell :deep(.admin-button--danger) {
  border-color: #fecaca;
  background: #fff7f7;
  color: #991b1b;
}

.admin-shell :deep(.admin-status) {
  display: inline-flex;
  min-height: 22px;
  align-items: center;
  border-radius: 999px;
  padding: 0 8px;
  font-size: 10px;
  font-weight: 700;
  line-height: 1.2;
  white-space: nowrap;
}

.admin-shell :deep(.admin-status--positive) {
  background: #dcfce7;
  color: #166534;
}

.admin-shell :deep(.admin-status--warning) {
  background: #fef3c7;
  color: #92400e;
}

.admin-shell :deep(.admin-status--muted) {
  background: #e2e8f0;
  color: #475569;
}

.admin-shell :deep(.admin-status--danger) {
  background: #fee2e2;
  color: #991b1b;
}

.admin-shell :deep(.admin-dialog) {
  width: min(480px, calc(100vw - 32px));
  max-height: calc(100dvh - 32px);
  overflow: auto;
  border: 1px solid #dbe3ef;
  border-radius: var(--admin-panel-radius);
  padding: 22px 24px;
  background: var(--admin-panel-bg);
  color: var(--admin-text-strong);
  box-shadow: 0 20px 60px rgb(15 23 42 / 20%);
}

@media (max-width: 720px) {
  .admin-shell :deep(.admin-dialog) {
    padding: 16px;
  }
}

.admin-shell :deep(.admin-dialog::backdrop) {
  background: rgb(15 23 42 / 42%);
}

.admin-shell :deep(.admin-dialog h2) {
  margin: 0;
  color: var(--admin-text-strong);
  font-size: 17px;
}

.admin-sidebar {
  position: fixed;
  z-index: 30;
  inset: 0 auto 0 0;
  display: flex;
  width: 240px;
  flex-direction: column;
  overflow-y: auto;
  overscroll-behavior: contain;
  border-right: 1px solid var(--admin-panel-border);
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
  border-bottom: 1px solid var(--admin-panel-border);
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
  background: var(--primary-color);
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
  color: var(--admin-text-muted);
  font-size: 11px;
  letter-spacing: 0.08em;
}

.admin-nav {
  display: grid;
  gap: 4px;
  padding: 16px 12px;
}

.admin-nav__group h2 {
  margin: 0;
}

.admin-nav__group-toggle {
  display: flex;
  width: 100%;
  min-height: 46px;
  align-items: center;
  gap: 10px;
  margin: 4px 0;
  padding: 0 16px;
  border: 0;
  border-radius: 12px;
  background: transparent;
  color: #475569;
  font: inherit;
  font-size: 16px;
  font-weight: 600;
  text-align: left;
  cursor: pointer;
  transition: background 140ms ease, color 140ms ease;
}

.admin-nav__group-toggle:hover {
  background: var(--admin-primary-soft);
  color: var(--primary-color);
}

.admin-nav__group-toggle:focus-visible {
  outline: 3px solid var(--admin-focus);
  outline-offset: 2px;
}

.admin-nav__group-label {
  flex: 1;
  line-height: 1;
}

.admin-nav__chevron {
  color: #94a3b8;
  font-size: 19px;
  line-height: 1;
  transition: transform 140ms ease, color 140ms ease;
}

.admin-nav__group-toggle:hover .admin-nav__chevron {
  color: var(--primary-color);
}

.admin-nav__group-toggle[aria-expanded="true"] .admin-nav__chevron {
  transform: rotate(180deg);
}

.admin-nav__items {
  display: grid;
}

.admin-nav__link {
  display: flex;
  min-height: 46px;
  align-items: center;
  margin: 4px 0;
  padding: 0 16px;
  border-radius: 12px;
  color: #475569;
  font-size: 15px;
  font-weight: 600;
  text-decoration: none;
  transition: background 140ms ease, color 140ms ease, box-shadow 140ms ease;
}

.admin-nav__link:hover:not(.is-active) {
  background: var(--admin-primary-soft);
  color: var(--primary-hover);
}

.admin-nav__link.is-active {
  background: linear-gradient(90deg, #2563eb 0%, #3b82f6 100%);
  color: #fff;
  box-shadow: 0 12px 24px rgb(37 99 235 / 22%);
}

.admin-nav__icon {
  display: inline-flex;
  width: 18px;
  flex: 0 0 18px;
  align-items: center;
  color: #94a3b8;
  transition: color 140ms ease;
}

.admin-nav__icon svg {
  display: block;
  width: 17px;
  height: 17px;
}

.admin-nav__group-toggle:hover .admin-nav__icon {
  color: var(--primary-color);
}

.admin-sidebar__account {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 12px;
  margin-top: auto;
  padding: 12px;
  border-top: 1px solid var(--admin-panel-border);
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
  color: var(--admin-text-strong);
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
  color: var(--admin-text-strong);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}

.admin-shell__navigation-toggle:focus-visible,
.admin-nav__link:focus-visible,
.admin-brand:focus-visible {
  outline: 3px solid var(--admin-focus);
  outline-offset: 2px;
}

.admin-shell__content {
  width: 100%;
  flex: 1;
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
  color: var(--admin-text-muted);
  font-size: 12px;
}

.admin-shell__scrim {
  display: none;
}

.admin-shell :deep(.admin-page-heading) {
  display: grid;
  gap: 6px;
}

.admin-shell :deep(.admin-page-heading h1) {
  margin: 0;
  color: var(--admin-text);
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.2;
}

.admin-shell :deep(.admin-page-heading p) {
  margin: 0;
  color: var(--admin-muted);
  font-size: 13px;
  line-height: 1.5;
}

.admin-shell :deep(.admin-dialog) {
  position: fixed;
  z-index: 1000;
  width: min(var(--admin-dialog-width), calc(100vw - 32px));
  max-height: calc(100dvh - 32px);
  margin: auto;
  overflow: auto;
  padding: 24px;
  border: 1px solid var(--admin-border);
  border-radius: var(--admin-dialog-radius);
  background: var(--admin-panel-bg, #fff);
  box-shadow: var(--admin-dialog-shadow);
  color: var(--admin-text);
}

.admin-shell :deep(.admin-dialog::backdrop) {
  background: rgb(15 23 42 / 42%);
}

.admin-shell :deep(.admin-dialog h2) {
  margin: 0;
  color: var(--admin-text);
  font-size: 17px;
  font-weight: 700;
}

.admin-shell :deep(.admin-dialog > p) {
  margin: 8px 0 16px;
  color: var(--admin-muted);
  font-size: 13px;
  line-height: 1.6;
}

.admin-shell :deep(.admin-dialog__actions) {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}

.admin-shell :deep(.admin-dialog__error) {
  color: #b91c1c;
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
    padding: 16px;
  }

  .admin-shell__footer {
    padding: 0 16px 16px;
  }

  .admin-shell__footer-row {
    flex-wrap: wrap;
    gap: 8px;
  }

  .admin-shell :deep(.admin-panel__header) {
    align-items: stretch;
  }

  .admin-shell :deep(.admin-panel__actions) {
    width: 100%;
    justify-content: flex-start;
  }

  .admin-shell :deep(.admin-page-heading h1) {
    font-size: 22px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .admin-sidebar,
  .admin-page-enter-active,
  .admin-page-leave-active,
  .admin-nav__chevron {
    transition: none;
  }
}
</style>
