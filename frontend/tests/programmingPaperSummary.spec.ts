import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import PracticeQuestionCard from '../src/features/practice/PracticeQuestionCard.vue';
import type { AssistantLocalRun } from '../src/shared/assistantState';
import type { Submission } from '../src/shared/types';
const question = { id: 'py', title: '问好', kind: 'python' as const, prompt: '读取名字并问好。', starter_code: 'name = input()', samples: [{ input: '小林', output: '你好，小林！' }, { input: 'Python', output: '你好，Python！' }] };
const props = { question, index: 14, answer: '', code: question.starter_code, paperMode: true };
const run: AssistantLocalRun = { question_id: 'py', run_id: 'run', code: 'print(input())', completed_at: '', duration_ms: 1, error: '', stopped: false, cases: [{stdout: '', error: '', status: 'accepted'}, {stdout: '', error: '', status: 'wrong_answer'}] };
describe('programming paper summary', () => {
  it('shows an unanswered summary without editor, samples or detailed results and opens single mode', async () => {
    const wrapper = mount(PracticeQuestionCard, { props });
    expect(wrapper.text()).toContain('15. 问好');
    expect(wrapper.text()).toContain('未作答');
    expect(wrapper.find('.coding-workbench').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('小林');
    await wrapper.get('button').trigger('click');
    expect(wrapper.emitted('openSingle')).toEqual([[]]);
    wrapper.unmount();
  });
  it('separates public sample outcomes from grades and marks changed code', async () => {
    const wrapper = mount(PracticeQuestionCard, { props: {...props, code: run.code, assistantFeedback: {local_run: run}} });
    expect(wrapper.text()).toContain('已作答');
    expect(wrapper.text()).toContain('公开样例 · 通过 1 / 2');
    await wrapper.setProps({code: 'print("new")'});
    expect(wrapper.text()).toContain('代码已修改');
    await wrapper.setProps({assistantFeedback: {local_run: {...run, cases: run.cases.map(c=>({...c,status:'wrong_answer'}))}}});
    expect(wrapper.text()).toContain('全部未通过（0 / 2）');
    wrapper.unmount();
  });
  it('prefers formal results, keeps locked papers readonly, and never labels service failure as wrong', async () => {
    const result: Submission = { id:'s',activity_id:'a',question_id:'py',mode:'submit',status:'wrong_answer',created_at:'',feedback:{passed_count:2,total_count:4} };
    const wrapper = mount(PracticeQuestionCard, {props:{...props,readOnly:true,result,assistantFeedback:{local_run:run}}});
    expect(wrapper.text()).toContain('正式判题 · 通过 2 / 4');
    expect(wrapper.get('button').text()).toContain('查看单题详情');
    await wrapper.setProps({result:{...result,status:'submission_unknown'}});
    expect(wrapper.text()).toContain('判定状态待确认');
    expect(wrapper.text()).not.toContain('通过 2 / 4');
    wrapper.unmount();
  });
});
