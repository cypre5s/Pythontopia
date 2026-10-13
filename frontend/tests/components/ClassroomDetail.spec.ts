import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';
import ClassroomDetail from '@/pages/teacher/ClassroomDetail.vue';
import { useTeacherStore } from '@stores/teacher';
import { useSessionStore } from '@stores/session';
import type { RemediationResultDto } from '@/api/teacher';

const apiMocks = vi.hoisted(() => ({
  listMyClassrooms: vi.fn(),
  createClassroom: vi.fn(),
  removeClassroomMember: vi.fn(),
  listClassroomMembersByStatus: vi.fn(),
  listClassroomQuestsForTeacher: vi.fn(),
  publishClassroomQuest: vi.fn(),
  archiveClassroomQuest: vi.fn(),
  fetchKcHeatmap: vi.fn(),
  fetchQuestSummary: vi.fn(),
  fetchErrorHotspots: vi.fn(),
  fetchResourceUsage: vi.fn(),
  fetchRecentActivity: vi.fn(),
  updateClassroom: vi.fn(),
  regenerateClassroomJoinCode: vi.fn(),
  triggerRemediation: vi.fn(),
}));

vi.mock('@/api/classroom', () => ({
  listMyClassrooms: apiMocks.listMyClassrooms,
  createClassroom: apiMocks.createClassroom,
  removeClassroomMember: apiMocks.removeClassroomMember,
}));
vi.mock('@/api/teacher', () => ({
  listClassroomMembersByStatus: apiMocks.listClassroomMembersByStatus,
  listClassroomQuestsForTeacher: apiMocks.listClassroomQuestsForTeacher,
  publishClassroomQuest: apiMocks.publishClassroomQuest,
  archiveClassroomQuest: apiMocks.archiveClassroomQuest,
  fetchKcHeatmap: apiMocks.fetchKcHeatmap,
  fetchQuestSummary: apiMocks.fetchQuestSummary,
  fetchErrorHotspots: apiMocks.fetchErrorHotspots,
  fetchResourceUsage: apiMocks.fetchResourceUsage,
  fetchRecentActivity: apiMocks.fetchRecentActivity,
  updateClassroom: apiMocks.updateClassroom,
  regenerateClassroomJoinCode: apiMocks.regenerateClassroomJoinCode,
  triggerRemediation: apiMocks.triggerRemediation,
}));

let router: Router;

function mountClassroomDetail() {
  return mount(ClassroomDetail, {
    global: {
      plugins: [router],
      stubs: { AppLayout: { template: '<div><slot /></div>' } },
    },
  });
}

function setupRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/teacher', name: 'teacher', component: { template: '<div/>' } },
      {
        path: '/teacher/classroom/:id',
        name: 'teacher-classroom',
        component: ClassroomDetail,
      },
    ],
  });
}

describe('ClassroomDetail', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setActivePinia(createPinia());
    router = setupRouter();
    apiMocks.listMyClassrooms.mockResolvedValue([
      {
        classroomId: 9,
        name: 'Stage12 Demo',
        description: '演示班级',
        teacherId: 42,
        packId: 43,
        status: 'ACTIVE',
        joinCode: 'ABC123',
      },
    ]);
    apiMocks.listClassroomMembersByStatus.mockResolvedValue([
      { studentId: 11, username: 'stu01', displayName: 'Stu 01', status: 'ACTIVE' },
    ]);
    apiMocks.listClassroomQuestsForTeacher.mockResolvedValue([]);
    apiMocks.fetchKcHeatmap.mockResolvedValue([]);
    apiMocks.fetchQuestSummary.mockResolvedValue([]);
    apiMocks.fetchErrorHotspots.mockResolvedValue([]);
    apiMocks.fetchResourceUsage.mockResolvedValue({
      periodStart: '2026-05-15',
      periodEnd: '2026-05-21',
      totals: [],
      daily: [],
    });
    apiMocks.fetchRecentActivity.mockResolvedValue([]);
    apiMocks.publishClassroomQuest.mockResolvedValue({
      classroomQuest: {},
      totalStudents: 1,
      createdQuests: 1,
      skippedDuplicates: 0,
    });
    apiMocks.regenerateClassroomJoinCode.mockResolvedValue({
      classroomId: 9,
      name: 'Stage12 Demo',
      description: '演示班级',
      teacherId: 42,
      packId: 43,
      status: 'ACTIVE',
      joinCode: 'NEW999',
    });
    apiMocks.triggerRemediation.mockResolvedValue({
      classroomId: 9,
      weakKcCount: 0,
      packs: [],
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('renders tabs and switches active panel', async () => {
    await router.push('/teacher/classroom/9');
    const wrapper = mountClassroomDetail();
    await flushPromises();

    expect(wrapper.text()).toContain('Stage12 Demo');
    // 重构为侧栏导航（dashboard-01）：tab 现为 .cd-nav__item，共 7 个。
    const tabButtons = wrapper.findAll('.cd-nav__item');
    expect(tabButtons.length).toBeGreaterThanOrEqual(7);
    // index 2 = 任务 tab（overview/live/quests/...）。
    await tabButtons[2]!.trigger('click');
    expect(wrapper.find('[data-testid="quest-tab"]').isVisible()).toBe(true);
  });

  it('regenerates join code via API', async () => {
    await router.push('/teacher/classroom/9');
    const wrapper = mountClassroomDetail();
    await flushPromises();
    const button = wrapper.findAll('button').find((b) => b.text() === '重置加入码');
    expect(button).toBeTruthy();
    await button!.trigger('click');
    await flushPromises();
    const teacher = useTeacherStore();
    expect(apiMocks.regenerateClassroomJoinCode).toHaveBeenCalledWith(9);
    expect(teacher.current.classroom?.joinCode).toBe('NEW999');
  });

  it('prevents duplicate join-code regeneration while the request is pending', async () => {
    let resolveRequest!: (value: { classroomId: number; name: string; joinCode: string }) => void;
    apiMocks.regenerateClassroomJoinCode.mockReturnValue(
      new Promise((resolve) => {
        resolveRequest = resolve;
      }),
    );

    await router.push('/teacher/classroom/9');
    const wrapper = mountClassroomDetail();
    await flushPromises();
    const button = wrapper.findAll('button').find((item) => item.text().includes('重置加入码'))!;

    await button.trigger('click');
    await wrapper.vm.$nextTick();
    await button.trigger('click');

    expect(button.attributes('disabled')).toBeDefined();
    expect(button.text()).toContain('重置中…');
    expect(apiMocks.regenerateClassroomJoinCode).toHaveBeenCalledTimes(1);

    resolveRequest({ classroomId: 9, name: 'Stage12 Demo', joinCode: 'NEW999' });
    await flushPromises();
    expect(button.attributes('disabled')).toBeUndefined();
  });

  // ── P6.1 一键补救包 ──
  it('一键生成补救包：点击触发 triggerRemediation(classroomId) 并渲染补救包（KC 名 + 资源数 + 证据页）', async () => {
    apiMocks.triggerRemediation.mockResolvedValue({
      classroomId: 9,
      weakKcCount: 2,
      packs: [
        {
          kcCode: 'PY43-KC-012',
          kcName: 'for 循环',
          resourceIds: [501, 502],
          evidencePageIds: [12, 13],
        },
        {
          kcCode: 'PY43-KC-020',
          kcName: '列表切片',
          resourceIds: [503],
          evidencePageIds: [],
        },
      ],
    });

    await router.push('/teacher/classroom/9');
    const wrapper = mountClassroomDetail();
    await flushPromises();

    const trigger = wrapper.find('[data-testid="remediation-trigger"]');
    expect(trigger.exists()).toBe(true);
    expect(trigger.text()).toContain('一键生成补救包');

    await trigger.trigger('click');
    await flushPromises();

    // 调用真实契约端点（经 api 层），且带正确 classroomId；绝不直连真实后端。
    expect(apiMocks.triggerRemediation).toHaveBeenCalledWith(9);

    const packs = wrapper.find('[data-testid="remediation-packs"]');
    expect(packs.exists()).toBe(true);
    expect(wrapper.findAll('.cd-remediation__pack').length).toBe(2);

    const forLoop = wrapper.find('[data-testid="remediation-pack-PY43-KC-012"]');
    expect(forLoop.text()).toContain('for 循环');
    expect(forLoop.text()).toContain('2 个补救资源');
    expect(forLoop.text()).toContain('12、13');

    const slice = wrapper.find('[data-testid="remediation-pack-PY43-KC-020"]');
    expect(slice.text()).toContain('列表切片');
    expect(slice.text()).toContain('1 个补救资源');
    expect(slice.text()).toContain('—'); // 无证据页 → 回退占位

    expect(wrapper.find('[data-testid="remediation-panel"]').text()).toContain('2 个薄弱知识点');
  });

  it('补救包为空（无薄弱 KC）→ 渲染空态，不渲染补救包列表', async () => {
    apiMocks.triggerRemediation.mockResolvedValue({
      classroomId: 9,
      weakKcCount: 0,
      packs: [],
    });

    await router.push('/teacher/classroom/9');
    const wrapper = mountClassroomDetail();
    await flushPromises();

    await wrapper.find('[data-testid="remediation-trigger"]').trigger('click');
    await flushPromises();

    expect(apiMocks.triggerRemediation).toHaveBeenCalledWith(9);
    expect(wrapper.find('[data-testid="remediation-empty"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="remediation-packs"]').exists()).toBe(false);
  });

  it('补救包接口失败 → 渲染错误态文案', async () => {
    apiMocks.triggerRemediation.mockRejectedValue(new Error('班级数据异常'));

    await router.push('/teacher/classroom/9');
    const wrapper = mountClassroomDetail();
    await flushPromises();

    await wrapper.find('[data-testid="remediation-trigger"]').trigger('click');
    await flushPromises();

    const err = wrapper.find('[data-testid="remediation-error"]');
    expect(err.exists()).toBe(true);
    expect(err.text()).toContain('补救包生成失败');
    expect(err.text()).toContain('班级数据异常');
    expect(wrapper.find('[data-testid="remediation-packs"]').exists()).toBe(false);
  });

  it('请求进行中 → 按钮禁用并显示「生成中…」，完成后恢复', async () => {
    let resolveFn: (value: RemediationResultDto) => void = () => {};
    const pending = new Promise<RemediationResultDto>((resolve) => {
      resolveFn = resolve;
    });
    apiMocks.triggerRemediation.mockReturnValue(pending);

    await router.push('/teacher/classroom/9');
    const wrapper = mountClassroomDetail();
    await flushPromises();

    const trigger = wrapper.find('[data-testid="remediation-trigger"]');
    await trigger.trigger('click');
    await wrapper.vm.$nextTick();

    // 飞行中：禁用 + 「生成中…」。
    expect(trigger.text()).toContain('生成中');
    expect(trigger.attributes('disabled')).toBeDefined();

    resolveFn({ classroomId: 9, weakKcCount: 0, packs: [] });
    await flushPromises();

    expect(wrapper.find('[data-testid="remediation-trigger"]').text()).toContain('一键生成补救包');
    expect(wrapper.find('[data-testid="remediation-trigger"]').attributes('disabled')).toBeUndefined();
  });

  // ── 直播 Tab：useClassroomLive 改为 fetch + Bearer token 订阅 SSE ──
  it('直播 Tab：点击「开始直播」用带 Authorization 的 fetch 订阅，并渲染事件', async () => {
    const session = useSessionStore();
    session.token = 'teacher-token';

    const encoder = new TextEncoder();
    const body = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(encoder.encode('event: connected\ndata: {}\n\n'));
        controller.enqueue(
          encoder.encode('event: submission\ndata: {"studentId":11,"status":"PASSED"}\n\n'),
        );
        // 保持流打开，避免触发 5 秒重连定时器。
      },
    });
    const fetchMock = vi.fn(async () => new Response(body, { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);

    await router.push('/teacher/classroom/9');
    const wrapper = mountClassroomDetail();
    await flushPromises();

    const liveTab = wrapper.find('[data-testid="classroom-live-tab"]');
    const startBtn = liveTab.findAll('button').find((b) => b.text().includes('开始直播'));
    expect(startBtn).toBeTruthy();
    await startBtn!.trigger('click');

    await vi.waitFor(() => {
      expect(wrapper.find('[data-testid="classroom-live-tab"]').text()).toContain('已连接直播');
    });

    expect(fetchMock).toHaveBeenCalledOnce();
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe('/api/classroom/9/live');
    expect((init.headers as Record<string, string>)['Authorization']).toBe('Bearer teacher-token');

    expect(wrapper.find('[data-testid="classroom-live-tab"]').text()).toContain('submission');
    wrapper.unmount();
  });

  it('直播 Tab：订阅遭 401 → 显示权限错误且不再重连', async () => {
    const fetchMock = vi.fn(async () => new Response(null, { status: 401 }));
    vi.stubGlobal('fetch', fetchMock);

    await router.push('/teacher/classroom/9');
    const wrapper = mountClassroomDetail();
    await flushPromises();

    const liveTab = wrapper.find('[data-testid="classroom-live-tab"]');
    const startBtn = liveTab.findAll('button').find((b) => b.text().includes('开始直播'));
    await startBtn!.trigger('click');

    await vi.waitFor(() => {
      expect(wrapper.find('[data-testid="live-error"]').exists()).toBe(true);
    });
    expect(wrapper.find('[data-testid="live-error"]').text()).toContain('无权订阅课堂直播');
    expect(fetchMock).toHaveBeenCalledOnce();
    wrapper.unmount();
  });
});
