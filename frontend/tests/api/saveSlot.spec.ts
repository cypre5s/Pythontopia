// R-14 S6：saveSlot API 客户端单测。重点验证 snapshot 以「序列化 JSON 文本 + application/json」
// 形式 POST，匹配后端 `@RequestBody String` + `SET snapshot = ?::jsonb` 契约。
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const httpMock = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}));

vi.mock('@/api/http', () => ({
  http: httpMock,
}));

import {
  listSaveSlots,
  createSaveSlot,
  activateSaveSlot,
  saveSlotSnapshot,
} from '@/api/saveSlot';
import type { SaveSlotDto } from '@/api/dto/SaveSlotDto';

const SLOT: SaveSlotDto = {
  slotIndex: 0,
  displayName: '自动存档',
  isActive: true,
  createdAt: '2026-06-04T00:00:00Z',
  lastPlayedAt: '2026-06-04T00:00:00Z',
  snapshot: { version: 1, mainQuest: {} },
  sceneName: 'WorldScene',
  unlockedBuildings: [],
  accumulatedPlaySeconds: 0,
  inventoryItems: [],
};

beforeEach(() => {
  httpMock.get.mockReset();
  httpMock.post.mockReset();
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('saveSlot api client', () => {
  it('listSaveSlots GETs /api/world/saves', async () => {
    httpMock.get.mockResolvedValueOnce({ data: [SLOT] });
    const slots = await listSaveSlots();
    expect(httpMock.get).toHaveBeenCalledWith('/api/world/saves');
    expect(slots).toHaveLength(1);
    expect(slots[0]?.slotIndex).toBe(0);
  });

  it('createSaveSlot POSTs displayName body to the slot index path', async () => {
    httpMock.post.mockResolvedValueOnce({ data: SLOT });
    const slot = await createSaveSlot(0, '自动存档');
    expect(httpMock.post).toHaveBeenCalledWith('/api/world/saves/0', { displayName: '自动存档' });
    expect(slot.slotIndex).toBe(0);
  });

  it('activateSaveSlot POSTs to the activate sub-path', async () => {
    httpMock.post.mockResolvedValueOnce({ data: { ...SLOT, isActive: true } });
    await activateSaveSlot(2);
    expect(httpMock.post).toHaveBeenCalledWith('/api/world/saves/2/activate');
  });

  it('saveSlotSnapshot POSTs serialized JSON text + application/json header', async () => {
    httpMock.post.mockResolvedValueOnce({ data: SLOT });
    const snap = { version: 1, mainQuest: { currentChapter: 2 } };
    await saveSlotSnapshot(0, snap);
    expect(httpMock.post).toHaveBeenCalledWith(
      '/api/world/saves/0/snapshot',
      JSON.stringify(snap),
      { headers: { 'Content-Type': 'application/json' } },
    );
  });

  it('saveSlotSnapshot body is a string (not an object) so backend ?::jsonb binds', async () => {
    httpMock.post.mockResolvedValueOnce({ data: SLOT });
    await saveSlotSnapshot(1, { version: 1, mainQuest: {} });
    const body = httpMock.post.mock.calls[0]?.[1];
    expect(typeof body).toBe('string');
  });
});
