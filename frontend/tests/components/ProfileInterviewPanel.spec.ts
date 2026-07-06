import { createPinia, setActivePinia } from 'pinia';
import { mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import ProfileInterviewPanel from '@/components/profile/ProfileInterviewPanel.vue';
import { useLearningStore } from '@/stores/learning';

describe('ProfileInterviewPanel', () => {
  it('shows completion action when all eight dimensions are filled', async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const store = useLearningStore();
    store.profileInterview.sessionId = 'session-1';
    store.profileInterview.done = true;
    store.profileInterview.dimensionsFilled = [
      'major',
      'base_level',
      'learning_goal',
      'cognitive_style',
      'learning_preference',
      'error_patterns',
      'practice_capability',
      'emotion_persistence',
    ];
    store.finishInterview = vi.fn(async () => undefined);

    const wrapper = mount(ProfileInterviewPanel, {
      global: { plugins: [pinia] },
    });

    expect(wrapper.text()).toContain('8 / 8');
    expect(wrapper.get('button.finish').text()).toContain('完成访谈');
  });
});
