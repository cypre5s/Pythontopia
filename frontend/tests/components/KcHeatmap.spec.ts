import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import KcHeatmap from '@/components/teacher/KcHeatmap.vue';
import type { KcHeatmapEntryDto } from '@/api/dto/TeacherDashboard';

describe('KcHeatmap', () => {
  it('groups entries by chapter and colors mastery buckets', () => {
    const entries: KcHeatmapEntryDto[] = [
      {
        kcCode: 'PY43-KC-001',
        kcName: 'KC1',
        chapterId: 1,
        chapterOrder: 1,
        avgMastery: 0.1,
        studentCount: 3,
        masteredCount: 0,
        learningCount: 0,
        strugglingCount: 3,
      },
      {
        kcCode: 'PY43-KC-002',
        kcName: 'KC2',
        chapterId: 1,
        chapterOrder: 2,
        avgMastery: 0.9,
        studentCount: 3,
        masteredCount: 3,
        learningCount: 0,
        strugglingCount: 0,
      },
    ];
    const wrapper = mount(KcHeatmap, { props: { entries } });
    const cells = wrapper.findAll('.cell');
    expect(cells).toHaveLength(2);
    const firstStyle = cells[0]!.attributes('style') ?? '';
    expect(firstStyle).toContain('background-color: rgb(231, 111, 81)');
    const secondStyle = cells[1]!.attributes('style') ?? '';
    expect(secondStyle).toContain('background-color: rgb(42, 157, 143)');
  });

  it('shows empty hint when no data', () => {
    const wrapper = mount(KcHeatmap, { props: { entries: [] } });
    expect(wrapper.text()).toContain('暂无 KC 数据');
  });
});
