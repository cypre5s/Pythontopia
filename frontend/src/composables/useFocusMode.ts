import { computed } from 'vue';
import { useGameSettingsStore } from '@stores/gameSettings';

export function useFocusMode() {
  const settings = useGameSettingsStore();
  const active = computed(() => settings.education.focusMode);
  return { focusModeActive: active };
}
