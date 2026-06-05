import { createRouter, createWebHistory, type RouteLocationNormalized } from 'vue-router';
import LoginPage from '@/pages/LoginPage.vue';
import { useSessionStore } from '@stores/session';
import { useToastStore } from '@stores/toast';
import { useDialogStore } from '@stores/dialog';

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/world' },
    {
      path: '/home',
      name: 'home',
      component: () => import('@/pages/HomePage.vue'),
    },
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
      meta: { fullscreen: true, requiresAuth: true },
    },
    // 向后兼容：旧的 /town /field /bug-mine 路由保留，但直接 redirect 到 /world，
    // 由 WorldScene 内部用 sceneKey 切换 town_hub / farm_north / mine_south。
    // /bug-mine 用 function-form 透传 ?side=east|west（mine_south.json 引用过）。
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
    // 章节视频讲解页（超星任务点视频 / 无视频时回退到 PPT）。
    // 由 NPC 对话的 open_panel 效果（route="/lecture/:chapter"）进入。
    {
      path: '/lecture/:chapter(\\d+)',
      name: 'lecture',
      component: () => import('@/pages/LecturePage.vue'),
      meta: { requiresAuth: true },
      props: (route) => ({ chapter: Number(route.params.chapter) }),
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
    // 导师并入「百晓工坊」。/tutor 永久重定向到 /workshop，
    // PauseMenu / HomePage / GuideOverlay / NPC 对话等旧入口自动落到合并页。
    { path: '/tutor', redirect: '/workshop' },
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
    // AI 出题教师审核兜底页。
    // 教师拉一题 → 看 5 重审查结论 → 通过 / 驳回；驳回原因写 LearningEvent。
    // Web 演示用 localStorage 兜底。
    {
      path: '/teacher/review',
      name: 'teacher-review',
      component: () => import('@/pages/teacher/TeacherReviewPage.vue'),
      meta: { requiresAuth: true },
    },
    // 章末 BOSS 副本（4 阶段 AI 出题）。
    // chapter param 1..7 对应主线 7 章；进入前必须本章 5 子任务全部完成。
    {
      path: '/boss/:chapter(\\d+)',
      name: 'boss',
      component: () => import('@/pages/BossPage.vue'),
      meta: { requiresAuth: true },
      props: (route) => ({ chapter: Number(route.params.chapter) }),
    },
    // 无尽模式 — 连续 AI 出题，自适应难度。
    // 与闯关模式 /boss/:chapter 互补：闯关推主线，无尽刷分练手。
    {
      path: '/endless',
      name: 'endless',
      component: () => import('@/pages/EndlessPage.vue'),
      meta: { requiresAuth: true },
    },
    // 章节闯关塔，每章 10 关 = 9 知识/技能小关 + 1 章末 boss。
    // 前 9 关跳 /endless?chapter=&kc=；第 10 关跳 /boss/:chapter。
    // 关卡进度由 useArenaStore 持久化到 localStorage。
    {
      path: '/arena/:chapter(\\d+)',
      name: 'arena',
      component: () => import('@/pages/ArenaPage.vue'),
      meta: { requiresAuth: true },
    },
    // 6 个小建筑（民居/灯塔/谷仓/鸡舍）用 PlaceholderPage 兜底，
    // 让 town_hub.json / port_west.json / farm_north.json 里的 building.route 全部能 resolve。
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
    // catch-all 404 兜底，永不白屏。必须放在最后，避免抢占其它路由。
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

// 切换路由时关闭可能残留的 NPC 对话浮层：DialogTree 全局挂在 App.vue（不随路由卸载），
// 若用 HUD 等方式离开当前页而对话仍 visible，浮层会残留到下一页。close() 幂等，
// 由对话选项触发导航时已 close 过，这里再调一次也无副作用。
router.afterEach(() => {
  try {
    useDialogStore().close();
  } catch {
    /* pinia 未就绪（极端时序）忽略 */
  }
});
