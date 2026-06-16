import { createRouter, createWebHistory, type RouteLocationNormalized } from 'vue-router';
import LoginPage from '@/pages/LoginPage.vue';
import { useSessionStore } from '@stores/session';
import { useToastStore } from '@stores/toast';
import { useDialogStore } from '@stores/dialog';
import { isExamActive, isExamBlockedPath } from '@/components/chaptertest';

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
      // 方案 C：隐藏独立「示例库」画廊，统一收敛到「题目列表 /problems」。
      // 站内零散入口（图书馆关联示例 / 工坊 PLAN / NPC open_panel 等）与直接 URL 访问
      // 都在此重定向到 /problems（保留 kcCode 过滤），ExampleListPage 不再被渲染。
      beforeEnter: (to) => ({
        path: '/problems',
        query: typeof to.query.kcCode === 'string' ? { kcCode: to.query.kcCode } : {},
        replace: true,
      }),
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
    // 操作指南：文档式长页，PauseMenu「系统 → 操作帮助」跳转到这里。
    {
      path: '/guide',
      name: 'guide',
      component: () => import('@/pages/GuidePage.vue'),
      meta: { requiresAuth: true },
    },
    // 代码剧场（机关广场）：写真实 Python → 看傀儡走执行过程 + 世界条件反馈。
    // 「只有游戏能做」的具身执行可视化（notional machine 可见化）。
    {
      path: '/code-theater',
      name: 'code-theater',
      component: () => import('@/pages/CodeTheaterPage.vue'),
      meta: { requiresAuth: true, hideCompanion: true },
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
    // 毕业典礼（主线终局演出）：boss7 通关后由 BossPage 跳入；
    // 未通关经 URL 直入时页面内守卫重定向回 /world。
    {
      path: '/graduation',
      name: 'graduation',
      component: () => import('@/pages/GraduationPage.vue'),
      meta: { requiresAuth: true },
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
    // R-15 主线：关卡地图（冒险地图）/ 小节三件套学习页 / 章末大测试。
    {
      path: '/mainline-map',
      name: 'mainline-map',
      component: () => import('@/pages/MainlineMapPage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/mainline/stage/:stageId(\\d+)',
      name: 'stage-learn',
      component: () => import('@/pages/StageLearnPage.vue'),
      // 主线小节学习页（看/学/练）自带 StageContextTutor 面板，
      // 不再重复挂右下角「随身助教」浮窗（与 /workshop、/library 等同理）。
      meta: { requiresAuth: true, hideCompanion: true },
    },
    // 章末大测试：考试期禁挂随身助教（防 AI 协助），路由守卫另禁跳 AI 页。
    {
      path: '/chapter-quiz/:chapter(\\d+)',
      name: 'chapter-quiz',
      component: () => import('@/pages/ChapterTestPage.vue'),
      meta: { requiresAuth: true, hideCompanion: true },
      props: (route) => ({ chapter: Number(route.params.chapter), assessment: 'quiz' }),
    },
    {
      path: '/chapter-test/:chapter(\\d+)',
      name: 'chapter-test',
      component: () => import('@/pages/ChapterTestPage.vue'),
      meta: { requiresAuth: true, hideCompanion: true },
      props: (route) => ({ chapter: Number(route.params.chapter), assessment: 'test' }),
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
  // 章末大测试进行中：全局拦截跳转到 AI / 答疑类页面（防作弊，§4.3）。
  // examActive 单例由 ChapterTestPage 维护；blocked 前缀见 useExamRouteGuard。
  if (isExamActive() && isExamBlockedPath(to.path)) {
    toast.show('考试进行中，禁止前往该页面。', 'error');
    return false;
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
