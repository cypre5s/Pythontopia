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
    // 卡片式重构后热力色加在掌握度数值上：低分桶 #c0392b、高分桶 #2f8f6f。
    const values = wrapper.findAll('.cell__value');
    expect(values).toHaveLength(2);
    expect(values[0]!.attributes('style') ?? '').toContain('color: rgb(192, 57, 43)');
    expect(values[1]!.attributes('style') ?? '').toContain('color: rgb(47, 143, 111)');
  });

  it('shows empty hint when no data', () => {
    const wrapper = mount(KcHeatmap, { props: { entries: [] } });
    expect(wrapper.text()).toContain('暂无 KC 数据');
  });
});
