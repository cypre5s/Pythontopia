// teacher store 单测：覆盖 dashboard / quest 流转 / 错误处理 / current 状态同步。
// 该 store 之前完全没有 spec，是 R-13 班级班长视角的核心数据通道，必须先有保底测试。
import { setActivePinia, createPinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useTeacherStore } from '@stores/teacher';
import type { ClassroomDto } from '@/api/dto/ClassroomDto';
import type { ClassroomMemberDto } from '@/api/dto/ClassroomMemberDto';
import type {
  ClassroomQuestDto,
  ClassroomQuestPublishResponse,
} from '@/api/dto/ClassroomQuest';
import type {
  ErrorHotspotDto,
  KcHeatmapEntryDto,
  QuestCompletionDto,
  RecentActivityDto,
  ResourceUsageDto,
} from '@/api/dto/TeacherDashboard';

const teacherMocks = vi.hoisted(() => ({
  archiveClassroomQuest: vi.fn(),
  fetchErrorHotspots: vi.fn(),
  fetchKcHeatmap: vi.fn(),
  fetchQuestSummary: vi.fn(),
  fetchRecentActivity: vi.fn(),
  fetchResourceUsage: vi.fn(),
  listClassroomMembersByStatus: vi.fn(),
  listClassroomQuestsForTeacher: vi.fn(),
  publishClassroomQuest: vi.fn(),
  regenerateClassroomJoinCode: vi.fn(),
  updateClassroom: vi.fn(),
}));

const classroomMocks = vi.hoisted(() => ({
  createClassroom: vi.fn(),
  listMyClassrooms: vi.fn(),
  removeClassroomMember: vi.fn(),
}));

vi.mock('@/api/teacher', () => teacherMocks);
vi.mock('@/api/classroom', () => classroomMocks);

function makeClassroom(overrides: Partial<ClassroomDto> = {}): ClassroomDto {
  return {
    classroomId: 9,
    name: 'Python 演示班',
    description: 'demo',
    teacherId: 42,
    packId: 43,
    status: 'ACTIVE',
    joinCode: 'ABCDEF',
    ...overrides,
  };
}

function makeMember(overrides: Partial<ClassroomMemberDto> = {}): ClassroomMemberDto {
  return {
    studentId: 7,
    username: 'student7',
    displayName: '小七',
    status: 'ACTIVE',
    ...overrides,
  };
}

function makeQuest(overrides: Partial<ClassroomQuestDto> = {}): ClassroomQuestDto {
  return {
    id: 1,
    classroomId: 9,
    title: 'for 循环',
    description: '阶段 12 演示',
    kcCodes: ['PY43-KC-002'],
    nodeType: 'SOLVE_PROBLEM',
    payload: {},
    dueDate: '2026-05-25',
    createdBy: 42,
    createdAt: '2026-05-21T18:00:00Z',
    status: 'ACTIVE',
    targetProblemId: 476,
    targetResourceType: null,
    studentQuestId: null,
    studentQuestStatus: null,
    requiredEvidenceType: 'submission',
    targetProblemIds: [476],
    deepLinkRoute: '/training',
    deepLinkParams: { problemId: 476 },
    ...overrides,
  };
}

function resetAllMocks(): void {
  for (const fn of Object.values(teacherMocks)) {
    fn.mockReset();
  }
  for (const fn of Object.values(classroomMocks)) {
    fn.mockReset();
  }
}

describe('teacher store - 初始状态 / getters', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    resetAllMocks();
  });

  it('初始 state 为空集合 + loading=false + currentId=null', () => {
    const store = useTeacherStore();
    expect(store.classrooms).toEqual([]);
    expect(store.loading).toBe(false);
    expect(store.lastError).toBeNull();
    expect(store.currentId).toBeNull();
    expect(store.current.classroom).toBeNull();
    expect(store.current.members).toEqual([]);
    expect(store.current.quests).toEqual([]);
    expect(store.current.dashboard.loading).toBe(false);
    expect(store.lastPublishResponse).toBeNull();
  });

  it('hasClassrooms getter 空数组返回 false，非空数组返回 true', () => {
    const store = useTeacherStore();
    expect(store.hasClassrooms).toBe(false);
    store.classrooms = [makeClassroom()];
    expect(store.hasClassrooms).toBe(true);
  });
});

describe('teacher store - loadClassrooms', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    resetAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('成功时填充 classrooms 并 toggle loading flag', async () => {
    classroomMocks.listMyClassrooms.mockResolvedValue([
      makeClassroom({ classroomId: 1, name: 'A' }),
      makeClassroom({ classroomId: 2, name: 'B' }),
    ]);
    const store = useTeacherStore();
    const promise = store.loadClassrooms();
    expect(store.loading).toBe(true);
    await promise;
    expect(store.loading).toBe(false);
    expect(store.classrooms).toHaveLength(2);
    expect(store.lastError).toBeNull();
  });

  it('Error 实例失败时存 message，loading 仍归 false', async () => {
    classroomMocks.listMyClassrooms.mockRejectedValue(new Error('网络不可达'));
    const store = useTeacherStore();
    await store.loadClassrooms();
    expect(store.lastError).toBe('网络不可达');
    expect(store.loading).toBe(false);
    expect(store.classrooms).toEqual([]);
  });

  it('非 Error 抛出物（字符串/对象）被 String 化作为 lastError', async () => {
    classroomMocks.listMyClassrooms.mockRejectedValue('timeout');
    const store = useTeacherStore();
    await store.loadClassrooms();
    expect(store.lastError).toBe('timeout');
    expect(store.loading).toBe(false);
  });
});

describe('teacher store - classroom CRUD', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    resetAllMocks();
  });

  it('createClassroomEntry 把新班加到列表尾部并返回创建结果', async () => {
    const store = useTeacherStore();
    store.classrooms = [makeClassroom({ classroomId: 1, name: 'old' })];
    const created = makeClassroom({ classroomId: 2, name: 'new' });
    classroomMocks.createClassroom.mockResolvedValue(created);

    const result = await store.createClassroomEntry({ name: 'new' });

    expect(result).toBe(created);
    expect(store.classrooms.map((c) => c.classroomId)).toEqual([1, 2]);
  });

  it('updateClassroomEntry 同步替换列表中的同 id 项 + 同步 current.classroom', async () => {
    const store = useTeacherStore();
    const original = makeClassroom({ classroomId: 9, name: '旧名' });
    store.classrooms = [original];
    store.current.classroom = original;
    store.currentId = 9;
    const updated = makeClassroom({ classroomId: 9, name: '新名' });
    teacherMocks.updateClassroom.mockResolvedValue(updated);

    const result = await store.updateClassroomEntry(9, { name: '新名' });

    expect(result).toBe(updated);
    expect(store.classrooms[0]?.name).toBe('新名');
    expect(store.current.classroom?.name).toBe('新名');
  });

  it('updateClassroomEntry 当 current.classroom 是别的班级时不会污染 current', async () => {
    const store = useTeacherStore();
    const otherCurrent = makeClassroom({ classroomId: 3, name: '别的班' });
    store.classrooms = [makeClassroom({ classroomId: 9, name: '旧' }), otherCurrent];
    store.current.classroom = otherCurrent;
    store.currentId = 3;
    const updated = makeClassroom({ classroomId: 9, name: '新' });
    teacherMocks.updateClassroom.mockResolvedValue(updated);

    await store.updateClassroomEntry(9, { name: '新' });

    expect(store.current.classroom?.classroomId).toBe(3);
    expect(store.current.classroom?.name).toBe('别的班');
    expect(store.classrooms.find((c) => c.classroomId === 9)?.name).toBe('新');
  });

  it('regenerateJoinCode 替换 classroom 的 joinCode 并同步 current', async () => {
    const store = useTeacherStore();
    const original = makeClassroom({ classroomId: 9, joinCode: 'OLD123' });
    store.classrooms = [original];
    store.current.classroom = original;
    store.currentId = 9;
    teacherMocks.regenerateClassroomJoinCode.mockResolvedValue(
      makeClassroom({ classroomId: 9, joinCode: 'NEW999' }),
    );

    const result = await store.regenerateJoinCode(9);

    expect(result.joinCode).toBe('NEW999');
    expect(store.classrooms[0]?.joinCode).toBe('NEW999');
    expect(store.current.classroom?.joinCode).toBe('NEW999');
  });
});

describe('teacher store - openClassroom / refreshDetail', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    resetAllMocks();
  });

  it('openClassroom 命中本地 classrooms 时不再 loadClassrooms，但仍刷新 detail', async () => {
    const store = useTeacherStore();
    store.classrooms = [makeClassroom({ classroomId: 9 })];
    teacherMocks.listClassroomMembersByStatus.mockResolvedValue([makeMember()]);
    teacherMocks.listClassroomQuestsForTeacher.mockResolvedValue([makeQuest()]);

    await store.openClassroom(9);

    expect(classroomMocks.listMyClassrooms).not.toHaveBeenCalled();
    expect(store.currentId).toBe(9);
    expect(store.current.classroom?.classroomId).toBe(9);
    expect(store.current.members).toHaveLength(1);
    expect(store.current.quests).toHaveLength(1);
  });

  it('openClassroom 未命中时回退 loadClassrooms 再二次查找', async () => {
    const store = useTeacherStore();
    classroomMocks.listMyClassrooms.mockResolvedValue([
      makeClassroom({ classroomId: 9, name: '远程拉取' }),
    ]);
    teacherMocks.listClassroomMembersByStatus.mockResolvedValue([]);
    teacherMocks.listClassroomQuestsForTeacher.mockResolvedValue([]);

    await store.openClassroom(9);

    expect(classroomMocks.listMyClassrooms).toHaveBeenCalledTimes(1);
    expect(store.current.classroom?.name).toBe('远程拉取');
  });

  it('openClassroom 远程也找不到时 current.classroom 保持 null 但仍 refreshDetail', async () => {
    const store = useTeacherStore();
    classroomMocks.listMyClassrooms.mockResolvedValue([]);
    teacherMocks.listClassroomMembersByStatus.mockResolvedValue([]);
    teacherMocks.listClassroomQuestsForTeacher.mockResolvedValue([]);

    await store.openClassroom(404);

    expect(store.current.classroom).toBeNull();
    expect(store.currentId).toBe(404);
    expect(teacherMocks.listClassroomMembersByStatus).toHaveBeenCalledWith(404);
    expect(teacherMocks.listClassroomQuestsForTeacher).toHaveBeenCalledWith(404);
  });

  it('refreshDetail 在 currentId=null 时 noop，不发起任何请求', async () => {
    const store = useTeacherStore();
    await store.refreshDetail();
    expect(teacherMocks.listClassroomMembersByStatus).not.toHaveBeenCalled();
    expect(teacherMocks.listClassroomQuestsForTeacher).not.toHaveBeenCalled();
  });
});

describe('teacher store - quest 操作', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    resetAllMocks();
  });

  it('publishQuest 记录 lastPublishResponse，并在 currentId 匹配时刷新 detail', async () => {
    const store = useTeacherStore();
    store.currentId = 9;
    const response: ClassroomQuestPublishResponse = {
      classroomQuest: makeQuest({ id: 100, title: '发布的任务' }),
      totalStudents: 5,
      createdQuests: 5,
      skippedDuplicates: 0,
    };
    teacherMocks.publishClassroomQuest.mockResolvedValue(response);
    teacherMocks.listClassroomMembersByStatus.mockResolvedValue([]);
    teacherMocks.listClassroomQuestsForTeacher.mockResolvedValue([response.classroomQuest]);

    const result = await store.publishQuest(9, {
      title: '发布的任务',
      kcCodes: ['PY43-KC-002'],
      dueDate: '2026-05-30',
    });

    expect(result).toBe(response);
    // Pinia 把 state 字段包成 Proxy，引用相等不再成立，所以走 toEqual 比较结构。
    expect(store.lastPublishResponse).toEqual(response);
    expect(store.lastPublishResponse?.totalStudents).toBe(5);
    expect(teacherMocks.listClassroomQuestsForTeacher).toHaveBeenCalledWith(9);
  });

  it('publishQuest 当 classroomId 不等于 currentId 时不触发 refreshDetail', async () => {
    const store = useTeacherStore();
    store.currentId = 10;
    teacherMocks.publishClassroomQuest.mockResolvedValue({
      classroomQuest: makeQuest({ classroomId: 9 }),
      totalStudents: 1,
      createdQuests: 1,
      skippedDuplicates: 0,
    });

    await store.publishQuest(9, {
      title: 't',
      kcCodes: [],
      dueDate: '2026-05-30',
    });

    expect(teacherMocks.listClassroomMembersByStatus).not.toHaveBeenCalled();
    expect(teacherMocks.listClassroomQuestsForTeacher).not.toHaveBeenCalled();
  });

  it('archiveQuest 返回归档结果且在 currentId 匹配时刷新', async () => {
    const store = useTeacherStore();
    store.currentId = 9;
    const archived = makeQuest({ id: 1, status: 'ARCHIVED' });
    teacherMocks.archiveClassroomQuest.mockResolvedValue(archived);
    teacherMocks.listClassroomMembersByStatus.mockResolvedValue([]);
    teacherMocks.listClassroomQuestsForTeacher.mockResolvedValue([archived]);

    const result = await store.archiveQuest(9, 1);

    expect(result).toBe(archived);
    expect(teacherMocks.listClassroomQuestsForTeacher).toHaveBeenCalledWith(9);
  });

  it('removeMember 调用 classroom API 并刷新当前班级 detail', async () => {
    const store = useTeacherStore();
    store.currentId = 9;
    classroomMocks.removeClassroomMember.mockResolvedValue(undefined);
    teacherMocks.listClassroomMembersByStatus.mockResolvedValue([]);
    teacherMocks.listClassroomQuestsForTeacher.mockResolvedValue([]);

    await store.removeMember(9, 7);

    expect(classroomMocks.removeClassroomMember).toHaveBeenCalledWith(9, 7);
    expect(teacherMocks.listClassroomMembersByStatus).toHaveBeenCalledWith(9);
  });

  it('removeMember 非当前班级时仅调用 remove API 而不刷新本地 detail', async () => {
    const store = useTeacherStore();
    store.currentId = 99;
    classroomMocks.removeClassroomMember.mockResolvedValue(undefined);
    await store.removeMember(9, 7);
    expect(classroomMocks.removeClassroomMember).toHaveBeenCalledWith(9, 7);
    expect(teacherMocks.listClassroomMembersByStatus).not.toHaveBeenCalled();
  });
});

describe('teacher store - loadDashboard 并发与错误', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    resetAllMocks();
  });

  it('成功时并发拉取 5 个 dashboard 数据源并写入 state', async () => {
    const store = useTeacherStore();
    const heatmap: KcHeatmapEntryDto[] = [
      {
        kcCode: 'PY43-KC-001',
        kcName: '变量',
        chapterId: 1,
        chapterOrder: 1,
        avgMastery: 0.7,
        studentCount: 5,
        masteredCount: 3,
        learningCount: 1,
        strugglingCount: 1,
      },
    ];
    const summary: QuestCompletionDto[] = [
      {
        classroomQuestId: 1,
        title: '任务一',
        dueDate: '2026-05-25',
        status: 'ACTIVE',
        totalStudents: 5,
        completedCount: 3,
        inProgressCount: 1,
        skippedCount: 1,
        completionRate: 0.6,
      },
    ];
    const hotspots: ErrorHotspotDto[] = [
      {
        kcCode: 'PY43-KC-005',
        kcName: 'for 循环',
        failedSubmissionCount: 11,
        affectedStudentCount: 4,
        categories: [{ category: 'IndentationError', count: 6 }],
      },
    ];
    const usage: ResourceUsageDto = {
      periodStart: '2026-05-01',
      periodEnd: '2026-05-22',
      totals: [{ resourceType: 'HANDOUT', count: 12 }],
      daily: [{ day: '2026-05-22', resourceType: 'HANDOUT', count: 3 }],
    };
    const activity: RecentActivityDto[] = [
      {
        studentId: 7,
        displayName: '小七',
        eventKind: 'SUBMISSION_PASSED',
        kcCode: 'PY43-KC-002',
        detail: '提交 #900',
        occurredAt: '2026-05-22T08:00:00Z',
      },
    ];
    teacherMocks.fetchKcHeatmap.mockResolvedValue(heatmap);
    teacherMocks.fetchQuestSummary.mockResolvedValue(summary);
    teacherMocks.fetchErrorHotspots.mockResolvedValue(hotspots);
    teacherMocks.fetchResourceUsage.mockResolvedValue(usage);
    teacherMocks.fetchRecentActivity.mockResolvedValue(activity);

    const promise = store.loadDashboard(9);
    expect(store.current.dashboard.loading).toBe(true);
    await promise;

    expect(store.current.dashboard.loading).toBe(false);
    expect(store.current.dashboard.lastError).toBeNull();
    expect(store.current.dashboard.kcHeatmap).toEqual(heatmap);
    expect(store.current.dashboard.questSummary).toEqual(summary);
    expect(store.current.dashboard.errorHotspots).toEqual(hotspots);
    expect(store.current.dashboard.resourceUsage).toEqual(usage);
    expect(store.current.dashboard.recentActivity).toEqual(activity);
    expect(teacherMocks.fetchRecentActivity).toHaveBeenCalledWith(9, 20);
  });

  it('任一接口失败即写 lastError，loading 仍归 false（Promise.all 失败语义）', async () => {
    const store = useTeacherStore();
    teacherMocks.fetchKcHeatmap.mockRejectedValue(new Error('热力图服务挂了'));
    teacherMocks.fetchQuestSummary.mockResolvedValue([]);
    teacherMocks.fetchErrorHotspots.mockResolvedValue([]);
    teacherMocks.fetchResourceUsage.mockResolvedValue({
      periodStart: '',
      periodEnd: '',
      totals: [],
      daily: [],
    });
    teacherMocks.fetchRecentActivity.mockResolvedValue([]);

    await store.loadDashboard(9);

    expect(store.current.dashboard.loading).toBe(false);
    expect(store.current.dashboard.lastError).toBe('热力图服务挂了');
  });

  it('非 Error 抛出（字符串）也安全 String 化', async () => {
    const store = useTeacherStore();
    teacherMocks.fetchKcHeatmap.mockResolvedValue([]);
    teacherMocks.fetchQuestSummary.mockResolvedValue([]);
    teacherMocks.fetchErrorHotspots.mockResolvedValue([]);
    teacherMocks.fetchResourceUsage.mockRejectedValue('boom');
    teacherMocks.fetchRecentActivity.mockResolvedValue([]);

    await store.loadDashboard(9);

    expect(store.current.dashboard.lastError).toBe('boom');
  });
});

describe('teacher store - replaceClassroom 直接调用', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    resetAllMocks();
  });

  it('替换不存在的 id 时不会插入新条目（保持原列表）', () => {
    const store = useTeacherStore();
    store.classrooms = [makeClassroom({ classroomId: 9, name: 'A' })];
    store.replaceClassroom(makeClassroom({ classroomId: 99, name: '不存在' }));
    expect(store.classrooms).toHaveLength(1);
    expect(store.classrooms[0]?.name).toBe('A');
  });

  it('替换存在的 id 时整条对象被替换为新数据（结构相等而非 Proxy 引用相等）', () => {
    const store = useTeacherStore();
    const old = makeClassroom({ classroomId: 9, name: 'A' });
    store.classrooms = [old, makeClassroom({ classroomId: 10, name: 'B' })];
    const fresh = makeClassroom({ classroomId: 9, name: 'A2', joinCode: 'NEW' });
    store.replaceClassroom(fresh);
    // Pinia state 数组里的对象被代理化，直接 toBe(fresh) 不成立，故走结构比对 + 字段断言。
    expect(store.classrooms[0]).toEqual(fresh);
    expect(store.classrooms[0]?.name).toBe('A2');
    expect(store.classrooms[0]?.joinCode).toBe('NEW');
    expect(store.classrooms[1]?.classroomId).toBe(10);
  });
});
