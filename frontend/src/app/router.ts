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
    // 向后兼容：旧的 /town /field 路由 redirect 到 /world（R-14 单中央 hub）。
    // /bug-mine 现为独立页面（mine_south 风景地图已删），不再 redirect、不切 Phaser 场景。
    { path: '/town', redirect: '/world' },
    { path: '/field', redirect: '/world' },
    {
      path: '/bug-mine',
      name: 'bug-mine',
      component: () => import('@/pages/BugMinePage.vue'),
      beforeEnter: (to) => {
        const {
          submissionId,
          sessionId,
          bugMineSessionId,
          ...safeQuery
        } = to.query;
        if (
          submissionId !== undefined
          || sessionId !== undefined
          || bugMineSessionId !== undefined
        ) {
          return {
            path: '/bug-mine',
            query: safeQuery,
            hash: to.hash,
            replace: true,
          };
        }
        return true;
      },
      // 错题矿洞本身是聚焦调试场景，不挂随身助教（避免与逐层下探干扰）。
      meta: { requiresAuth: true, hideCompanion: true },
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
      // 百晓工坊本身就是 AI 答疑/资源生成主面板，右下角再挂「随身助教」浮层属重复入口，
      // 故 hideCompanion 关掉工坊内的随身助教快捷按钮（与 /training、/bug-mine 同理）。
      meta: { requiresAuth: true, hideCompanion: true },
      component: () => import('@/pages/WorkshopPage.vue'),
    },
    {
      path: '/library',
      name: 'library',
      component: () => import('@/pages/LibraryPage.vue'),
      // 课件阅读器右侧已有 RAG 检索与阅读区，不重复挂右下角「随身助教」。
      meta: { requiresAuth: true, hideCompanion: true },
    },
    // 章节视频讲解页（超星任务点视频 / 无视频时回退到 PPT）。
    // 由 NPC 对话的 open_panel 效果（route="/lecture/:chapter"）进入。
    {
      path: '/lecture/:chapter(\\d+)',
      name: 'lecture',
      component: () => import('@/pages/LecturePage.vue'),
      // 视频讲解页右侧已有 AI 助教侧栏，不重复挂随身助教浮层。
      meta: { requiresAuth: true, hideCompanion: true },
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
      // 做题页已有自带「AI 助教」入口，不重复挂随身助教。
      meta: { requiresAuth: true, hideCompanion: true },
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
    // 费曼反演（创新点 C）：教会会犯你同款错的 AI 学弟 → 真判题回流。
    // 复盘大厅「测」入口与 NPC 对话可跳转 /feynman?kc=...。
    {
      path: '/feynman',
      name: 'feynman',
      component: () => import('@/pages/FeynmanPage.vue'),
      meta: { requiresAuth: true, hideCompanion: true },
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
