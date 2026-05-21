import { setActivePinia, createPinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useLearningStore } from '@stores/learning';

const mocks = vi.hoisted(() => ({
  createWorkshopResource: vi.fn(),
  listWorkshopResources: vi.fn(),
  getWorkshopResource: vi.fn(),
  submitWorkshopFeedback: vi.fn(),
}));

vi.mock('@/api/workshop', () => mocks);

const sampleHandout = {
  id: 7,
  userId: 1,
  packId: 43,
  kcCodes: ['PY43-KC-001'],
  chapterId: 11,
  type: 'HANDOUT' as const,
  title: '讲义',
  contentFormat: 'MARKDOWN' as const,
  content: {
    sections: [
      {
        heading: '小节',
        paragraphs: ['段落'],
        code_snippets: [],
        page_refs: [{ page_id: 101, page_no: 12, chapter_id: 11 }],
      },
    ],
  },
  evidencePageIds: [101],
  summary: '摘要',
  safetyStatus: 'PASSED' as const,
  groundingStatus: 'PASSED' as const,
  feedbackStatus: 'NONE' as const,
  feedbackNote: null,
  createdAt: '2026-05-21T11:00:00Z',
};

describe('learning.resources substate', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    mocks.createWorkshopResource.mockReset();
    mocks.listWorkshopResources.mockReset();
    mocks.getWorkshopResource.mockReset();
    mocks.submitWorkshopFeedback.mockReset();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('createResource transitions phase and reloads list', async () => {
    mocks.createWorkshopResource.mockResolvedValue({ runId: 42, resourceId: 7, status: 'SUCCESS' });
    mocks.listWorkshopResources.mockResolvedValue([
      {
        id: 7,
        type: 'HANDOUT',
        title: '讲义',
        summary: '摘要',
        kcCodes: ['PY43-KC-001'],
        groundingStatus: 'PASSED',
        feedbackStatus: 'NONE',
        createdAt: '2026-05-21T11:00:00Z',
      },
    ]);

    const store = useLearningStore();
    const response = await store.createResource({
      kcCodes: ['PY43-KC-001'],
      type: 'HANDOUT',
    });
    expect(response.resourceId).toBe(7);
    expect(store.resources.phase).toBe('success');
    expect(store.resources.list).toHaveLength(1);
  });

  it('createResource propagates errors and records them', async () => {
    mocks.createWorkshopResource.mockRejectedValue(new Error('RESOURCE_THROTTLED'));
    const store = useLearningStore();
    await expect(
      store.createResource({ kcCodes: ['PY43-KC-001'], type: 'HANDOUT' }),
    ).rejects.toThrow('RESOURCE_THROTTLED');
    expect(store.resources.phase).toBe('error');
    expect(store.resources.lastError).toBe('RESOURCE_THROTTLED');
  });

  it('openResource sets current detail', async () => {
    mocks.getWorkshopResource.mockResolvedValue(sampleHandout);
    const store = useLearningStore();
    const detail = await store.openResource(7);
    expect(detail.title).toBe('讲义');
    expect(store.resources.current?.evidencePageIds).toContain(101);
  });

  it('giveResourceFeedback updates current and reloads list', async () => {
    mocks.getWorkshopResource.mockResolvedValue(sampleHandout);
    mocks.submitWorkshopFeedback.mockResolvedValue(undefined);
    mocks.listWorkshopResources.mockResolvedValue([]);

    const store = useLearningStore();
    await store.openResource(7);
    await store.giveResourceFeedback(7, { status: 'LIKED', note: '好评' });
    expect(store.resources.current?.feedbackStatus).toBe('LIKED');
    expect(store.resources.current?.feedbackNote).toBe('好评');
  });
});
