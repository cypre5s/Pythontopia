import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import PracticeList from '@/components/workshop/PracticeList.vue';

describe('PracticeList', () => {
  it('renders problems with sample IO but no full answers', () => {
    const wrapper = mount(PracticeList, {
      props: {
        content: {
          problems: [
            {
              title: '基础题',
              statement_md: '请输出列表元素之和。',
              hints: ['先把字符串转为数字。'],
              sample_io: [{ input: '1 2 3', output: '6' }],
              kc_codes: ['PY43-KC-001'],
              difficulty: 'basic',
            },
          ],
        },
      },
    });
    expect(wrapper.find('[data-testid="practice-list"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="sample-io"]').exists()).toBe(true);
    expect(wrapper.text()).not.toContain('answer_key');
    expect(wrapper.text()).not.toContain('hidden_test_cases');
  });
});
