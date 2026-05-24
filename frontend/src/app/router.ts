import { createRouter, createWebHistory, type RouteLocationNormalized } from 'vue-router';
import LoginPage from '@/pages/LoginPage.vue';
import { useSessionStore } from '@stores/session';
import { useToastStore } from '@stores/toast';

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/world' },
    { path: '/login', name: 'login', component: LoginPage, meta: { guestOnly: true } },
    {
      path: '/credits',
      name: 'credits',
      component: () => import('@/pages/CreditsPage.vue'),
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/pages/RegisterPage.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/world',
      name: 'world',
      component: () => import('@/pages/WorldPage.vue'),
      meta: { requiresAuth: true },
    },
    // M2 向后兼容：旧的 /town /field /bug-mine 路由保留，但直接 redirect 到 /world，
    // 由 WorldScene 内部用 sceneKey 切换 town_hub / farm_north / mine_south。
    // R-13：/bug-mine 用 function-form 透传 ?side=east|west（mine_south.json 引用过）。
    { path: '/town', redirect: '/world' },
    { path: '/field', redirect: '/world' },
    {
      path: '/bug-mine',
      redirect: (to) => ({ path: '/world', query: to.query }),
    },
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
      path: '/workshop',
      name: 'workshop',
      component: () => import('@/pages/WorkshopPage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/library',
      name: 'library',
      component: () => import('@/pages/LibraryPage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/problems',
      name: 'problems',
      component: () => import('@/pages/ProblemListPage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/examples',
      name: 'examples',
      component: () => import('@/pages/ExampleListPage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/training',
      name: 'training',
      component: () => import('@/pages/TrainingPage.vue'),
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
    // R-13：3 条静态 shop 路由（与 ShopController GET /api/world/shops/{shopKey} 对齐）。
    // 路由 props 注入 shopKey + 中文 displayName，避免在 URL 里暴露中文。
    {
      path: '/shop-east',
      name: 'shop-east',
      component: () => import('@/pages/ShopPage.vue'),
      props: { shopKey: 'shop_east', displayName: '工具铺' },
      meta: { requiresAuth: true },
    },
    {
      path: '/shop-west',
      name: 'shop-west',
      component: () => import('@/pages/ShopPage.vue'),
      props: { shopKey: 'shop_west', displayName: '杂货铺' },
      meta: { requiresAuth: true },
    },
    {
      path: '/merchant-ship',
      name: 'merchant-ship',
      component: () => import('@/pages/ShopPage.vue'),
      props: { shopKey: 'merchant_port', displayName: '商船' },
      meta: { requiresAuth: true },
    },
    // R-13：6 个小建筑（民居/灯塔/谷仓/鸡舍）用 PlaceholderPage 兜底，
    // 让 town_hub.json / port_west.json / farm_north.json 里的 building.route 全部能 resolve。
    // 真实功能（NPC 互动 / 道具入库 / 任务节点）等 R-14 接入。
    {
      path: '/lighthouse',
      name: 'lighthouse',
      component: () => import('@/pages/PlaceholderPage.vue'),
      props: { title: '灯塔', subtitle: '夜晚为远航者守望，但这里目前还在筹建中。' },
      meta: { requiresAuth: true },
    },
    {
      path: '/house-east',
      name: 'house-east',
      component: () => import('@/pages/PlaceholderPage.vue'),
      props: { title: '东侧民居', subtitle: '居民正在午睡，请稍后再访。' },
      meta: { requiresAuth: true },
    },
    {
      path: '/house-mid',
      name: 'house-mid',
      component: () => import('@/pages/PlaceholderPage.vue'),
      props: { title: '中央民居', subtitle: '门后传来读书声，等小镇正式开放将允许进入。' },
      meta: { requiresAuth: true },
    },
    {
      path: '/house-west',
      name: 'house-west',
      component: () => import('@/pages/PlaceholderPage.vue'),
      props: { title: '西侧民居', subtitle: '老人在炉边喝茶，今日不待客。' },
      meta: { requiresAuth: true },
    },
    {
      path: '/storage',
      name: 'storage',
      component: () => import('@/pages/PlaceholderPage.vue'),
      props: { title: '谷仓', subtitle: '稻香满仓，仓门暂未对玩家开放。' },
      meta: { requiresAuth: true },
    },
    {
      path: '/coop',
      name: 'coop',
      component: () => import('@/pages/PlaceholderPage.vue'),
      props: { title: '鸡舍', subtitle: '鸡群咯咯叫，但这里还在内测。' },
      meta: { requiresAuth: true },
    },
    // R-13：catch-all 404 兜底，永不白屏。必须放在最后，避免抢占其它路由。
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/pages/NotFoundPage.vue'),
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
    return { name: 'world' };
  }
  return true;
});
