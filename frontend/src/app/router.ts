import { createRouter, createWebHistory, type RouteLocationNormalized } from 'vue-router';
import LoginPage from '@/pages/LoginPage.vue';
import { useSessionStore } from '@stores/session';
import { useToastStore } from '@stores/toast';
import { useDialogStore } from '@stores/dialog';
import { useMainlineStore } from '@stores/mainline';
import { isExamActive, isExamBlockedPath } from '@/components/chaptertest';

declare module 'vue-router' {
  interface RouteMeta {
    adminOnly?: boolean;
    teacherOnly?: boolean;
  }
}

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
      path: '/admin',
      component: () => import('@/pages/admin/AdminLayout.vue'),
      meta: { requiresAuth: true, adminOnly: true, fullscreen: true, hideCompanion: true },
      children: [
        { path: '', redirect: { name: 'admin-operations' } },
        {
          path: 'operations',
          name: 'admin-operations',
          component: () => import('@/pages/admin/AdminOperationsPage.vue'),
        },
        {
          path: 'problem-reviews',
          name: 'admin-problem-reviews',
          component: () => import('@/pages/admin/AdminProblemReviewsPage.vue'),
        },
        {
          path: 'users',
          name: 'admin-users',
          component: () => import('@/pages/admin/AdminUsersPage.vue'),
        },
      ],
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
    // 代码剧场（机关广场）暂时下线（不上线）：保留实现代码（CodeTheaterPage / 引擎 / 场景），
    // 仅隐藏入口与路由。直接访问旧链接统一回小镇，避免暴露未上线功能。
    { path: '/code-theater', redirect: '/world' },
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
      meta: { requiresAuth: true, teacherOnly: true },
    },
    {
      path: '/teacher/classroom/:id',
      name: 'teacher-classroom',
      component: () => import('@/pages/teacher/ClassroomDetail.vue'),
      meta: { requiresAuth: true, teacherOnly: true },
    },
    // AI 出题教师审核页：队列、决定与审计信息均由后端持久化，路由与 API 双重鉴权。
    {
      path: '/teacher/review',
      name: 'teacher-review',
      component: () => import('@/pages/teacher/TeacherReviewPage.vue'),
      meta: { requiresAuth: true, teacherOnly: true, hideCompanion: true },
    },
    // 章末 BOSS 副本（4 阶段 AI 出题）：本章通过章末大测试（≥1★）后开放的可选挑战。
    // 入口在冒险地图的章节抽屉（ChapterStagePanel「挑战第 N 章 BOSS」），
    // 守卫按后端权威 overview 校验，杜绝手输 /boss/7 直接开打终极 BOSS。
    {
      path: '/boss/:chapter(\\d+)',
      name: 'boss',
      component: () => import('@/pages/BossPage.vue'),
      meta: { requiresAuth: true },
      props: (route) => ({ chapter: Number(route.params.chapter) }),
      beforeEnter: async (to) => {
        const chapter = Number(to.params.chapter);
        const mainline = useMainlineStore();
        const toast = useToastStore();
        // 已有缓存直接用（地图页刚拉过）；无缓存时拉一次权威 overview。
        await mainline.load();
        const ch = mainline.chapterByNumber(chapter);
        if (!ch) {
          toast.show(
            mainline.error
              ? '主线进度加载失败，暂时无法进入章末 BOSS，请稍后重试。'
              : `第 ${chapter} 章不存在。`,
            'error',
          );
          return { path: '/mainline-map', replace: true };
        }
        if (!ch.unlocked || ch.stars < 1) {
          toast.show(`第 ${chapter} 章 BOSS 需先通过本章章末大测试后解锁。`, 'error');
          return { path: '/mainline-map', replace: true };
        }
        return true;
      },
    },
    // 毕业典礼（主线终局演出）：7 章全部 ≥1★ 后由冒险地图「前往毕业典礼」进入
    // （第 7 章 BOSS 通关也会跳这里）。未达标经 URL 直入时页面内给出「还差什么」的说明，
    // 不再静默弹回小镇。
    {
      path: '/graduation',
      name: 'graduation',
      component: () => import('@/pages/GraduationPage.vue'),
      meta: { requiresAuth: true },
    },
    // 无尽模式 — 连续 AI 出题，自适应难度（简洁「随便练练」入口）。
    {
      path: '/endless',
      name: 'endless',
      component: () => import('@/pages/EndlessPage.vue'),
      meta: { requiresAuth: true },
    },
    // 无尽远征（Roguelike）：知识前沿 + 画像生成分支地图，取代「7 章 × 10 关」死网格。
    // 详见 docs/plans/2026-06-19-endless-roguelike-rebuild.md。
    {
      path: '/expedition',
      name: 'expedition',
      component: () => import('@/pages/EndlessExpeditionPage.vue'),
      // 无尽远征自带像素塔/列表 + 战斗页交互，右下角再挂「随身助教」浮层会遮挡塔与按钮，故关掉。
      meta: { requiresAuth: true, hideCompanion: true },
    },
    // 闯关塔（静态 7 章×10 关）已退役，并入「无尽远征」(/expedition)。
    // 旧链接重定向，保留章节作 ?focus 聚焦过滤。
    {
      path: '/arena/:chapter(\\d+)',
      redirect: (to) => ({ path: '/expedition', query: { focus: String(to.params.chapter) } }),
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
  // 导航定位（功能交互）：新页面滚动到顶，浏览器前进/后退恢复离开时的滚动位置，
  // 避免「点进新页却停在上一页的半中间」。锚点跳转（带 hash）交给浏览器原生处理。
  scrollBehavior(to, _from, savedPosition) {
    if (savedPosition) {
      return savedPosition;
    }
    if (to.hash) {
      return { el: to.hash, behavior: 'smooth' };
    }
    return { top: 0 };
  },
});

router.beforeEach((to: RouteLocationNormalized) => {
  const session = useSessionStore();
  const toast = useToastStore();
  // refreshIfNeeded() 在过期时会 logout（清空 token），所以必须先取快照：
  // 「从没登录过」与「登录过但已过期」是两件事，对首次访问的用户说「登录状态已失效」是误导。
  const hadSession = session.token !== null;
  session.refreshIfNeeded();
  if (to.meta.requiresAuth && !session.isAuthenticated) {
    toast.show(
      hadSession ? '登录状态已失效，请重新登录。' : '请先登录后再进入小镇。',
      hadSession ? 'error' : 'info',
    );
    return { name: 'login', query: { redirect: to.fullPath } };
  }
  if (to.meta.adminOnly && session.role !== 'ADMIN') {
    toast.show('需要管理员权限才能打开管理后台。', 'error');
    return { name: 'world', replace: true };
  }
  if (to.meta.teacherOnly && session.role !== 'TEACHER' && session.role !== 'ADMIN') {
    toast.show('需要教师或管理员权限才能打开教师页面。', 'error');
    return { name: 'world', replace: true };
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

// ── 发布滚动更新自愈：旧标签页引用了已被新构建替换掉的分块 ──────────────────────
// 部署新版本后，index.html 引用的 *.js 分块文件名（内容指纹 hash）会变化。仍开着旧页面的
// 用户点进懒加载路由（如 /arena 的 ArenaPage 分块）时，旧 hash 文件已不存在：
//   · 服务端命中 SPA 回退把 index.html(text/html) 当作 .js 返回 → 严格 MIME 校验报错；
//   · 或直接 404 → 动态 import() reject。
// 两种都会让导航中断、页面卡住。这里统一拦截，做「一次性整页跳转到目标地址」自愈：
// 整页加载会拉到最新 index.html 与正确的分块清单，路由随即正常解析。
const CHUNK_RELOAD_KEY = 'pyt:chunk-reload-at';

/**
 * 站内路径断言：只接受单个 `/` 开头的绝对站内路径。
 * `//evil.com` 这类协议相对 URL 传给 `location.assign` 会整页跳出本站（开放重定向），
 * 而 targetPath 源自 `to.fullPath`（含攻击者可构造的地址），必须先过滤。
 */
export function toSafeInternalPath(targetPath: string): string {
  return /^\/(?!\/)/.test(targetPath) ? targetPath : '/';
}

/** 是否为动态 import 分块加载失败（含跨浏览器措辞与 MIME 误判）。 */
function isDynamicImportError(error: unknown): boolean {
  const message = error instanceof Error ? error.message : String(error ?? '');
  return /Failed to fetch dynamically imported module|error loading dynamically imported module|Importing a module script failed|expected a javascript.*module|dynamically imported module/i.test(
    message,
  );
}

/**
 * 命中分块失效时整页跳到目标地址自愈。用 sessionStorage 做一次性闸门：
 * 10 秒内只自愈一次，避免目标分块在新构建里也真的取不到时陷入无限重载。
 */
function recoverFromStaleChunk(targetPath: string): void {
  try {
    const last = Number(sessionStorage.getItem(CHUNK_RELOAD_KEY) ?? '0');
    if (Number.isFinite(last) && Date.now() - last < 10_000) {
      return;
    }
    sessionStorage.setItem(CHUNK_RELOAD_KEY, String(Date.now()));
  } catch {
    /* sessionStorage 不可用：放弃闸门但仍尝试一次跳转（极少触发，可接受） */
  }
  window.location.assign(toSafeInternalPath(targetPath));
}

router.onError((error, to) => {
  if (isDynamicImportError(error)) {
    recoverFromStaleChunk(to?.fullPath ?? window.location.pathname);
  }
});

// Vite 的 modulepreload 预载失败会先抛 `vite:preloadError`（早于 import() reject）。
// preventDefault 后由我们统一走整页自愈，避免 Vite 默认直接抛错。
window.addEventListener('vite:preloadError', (event) => {
  event.preventDefault();
  recoverFromStaleChunk(window.location.pathname);
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
  // 导航成功即清除「分块加载失败」一次性闸门：下一次真正的分块失效能立刻自愈刷新。
  try {
    sessionStorage.removeItem(CHUNK_RELOAD_KEY);
  } catch {
    /* 隐私模式等禁用 sessionStorage 时忽略 */
  }
});
