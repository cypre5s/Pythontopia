import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';
import { nextTick } from 'vue';
import GlassBox from '../GlassBox.vue';
import { http } from '@/api/http';
vi.mock('@/api/http', () => ({ http: { get: vi.fn() } }));

describe('GlassBox', () => {
  beforeEach(() => { vi.clearAllMocks(); });
  it('shows loading', () => {
    vi.mocked(http.get).mockReturnValue(new Promise(() => {}));
    expect(mount(GlassBox).text()).toContain('...');
  });
  it('renders items with evidence', async () => {
    vi.mocked(http.get).mockResolvedValue({ data: [
      { agent_name:'PracticeAgent',scenario:'练习',status:'SUCCESS',evidence_page_ids:[1,2],trace:'[]',ts:'2026-01-01' },
    ]});
    const w = mount(GlassBox); await nextTick(); await nextTick(); await nextTick();
    expect(w.text()).toContain('PracticeAgent');
    expect(w.text()).toContain('证据页');
  });
  it('shows通识知识 when no evidence', async () => {
    vi.mocked(http.get).mockResolvedValue({ data: [
      { agent_name:'Tutor',scenario:'答疑',status:'OK',evidence_page_ids:[],trace:'[]',ts:'2026-01-01' },
    ]});
    const w = mount(GlassBox); await nextTick(); await nextTick(); await nextTick();
    expect(w.text()).toContain('通识知识');
  });
});
