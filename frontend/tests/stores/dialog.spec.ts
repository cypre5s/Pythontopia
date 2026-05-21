import { createPinia, setActivePinia } from 'pinia';
import { describe, expect, it } from 'vitest';
import { useDialogStore } from '@/stores/dialog';

describe('dialog store', () => {
  it('opens known placeholder NPC dialog and closes it', () => {
    setActivePinia(createPinia());
    const store = useDialogStore();

    store.open('mayor_py');

    expect(store.isOpen).toBe(true);
    expect(store.lines[0]).toContain('[占位]');
    store.close();
    expect(store.isOpen).toBe(false);
  });
});
