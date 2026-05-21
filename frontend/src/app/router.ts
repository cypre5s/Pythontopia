import { createRouter, createWebHistory, type RouteLocationNormalized } from 'vue-router';
import LoginPage from '@/pages/LoginPage.vue';
import TownPage from '@/pages/TownPage.vue';
import PlaceholderPage from '@/pages/PlaceholderPage.vue';
import { useSessionStore } from '@stores/session';
import { useToastStore } from '@stores/toast';

function placeholder(title: string, subtitle: string) {
  return {
    component: PlaceholderPage,
    props: { title, subtitle },
  };
}

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/town' },
    { path: '/login', name: 'login', component: LoginPage, meta: { guestOnly: true } },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/pages/RegisterPage.vue'),
      meta: { guestOnly: true },
    },
    { path: '/town', name: 'town', component: TownPage, meta: { requiresAuth: true } },
    {
      path: '/profile-room',
      name: 'profile-room',
      meta: { requiresAuth: true },
      component: () => import('@/pages/ProfileRoomPage.vue'),
    },
    {
      path: '/profile',
      name: 'profile',
      component: () => import('@/pages/ProfilePage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/field',
      name: 'field',
      component: () => import('@/pages/FieldPage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/workshop',
      name: 'workshop',
      component: () => import('@/pages/WorkshopPage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/library',
      name: 'library',
      meta: { requiresAuth: true },
      ...placeholder('图书馆', '复盘大厅整合了资源历史；图书馆 Beta 后再扩展。'),
    },
    {
      path: '/training',
      name: 'training',
      component: () => import('@/pages/TrainingPage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/bug-mine',
      name: 'bug-mine',
      component: () => import('@/pages/BugMinePage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/board',
      name: 'board',
      component: () => import('@/pages/BoardPage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/review-hall',
      name: 'review-hall',
      component: () => import('@/pages/ReviewHallPage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/teacher',
      name: 'teacher',
      component: () => import('@/pages/TeacherPage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/teacher/classroom/:id',
      name: 'teacher-classroom',
      component: () => import('@/pages/teacher/ClassroomDetail.vue'),
      meta: { requiresAuth: true },
    },
  ],
});

router.beforeEach((to: RouteLocationNormalized) => {
  const session = useSessionStore();
  const toast = useToastStore();
  session.refreshIfNeeded();
  if (to.meta.requiresAuth && !session.isAuthenticated) {
    toast.show('登录状态已失效，请重新登录。', 'error');
    return { name: 'login', query: { redirect: to.fullPath } };
  }
  if (to.meta.guestOnly && session.isAuthenticated) {
    return { name: 'town' };
  }
  return true;
});
