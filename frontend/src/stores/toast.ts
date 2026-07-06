import { defineStore } from 'pinia';

export type ToastKind = 'info' | 'error' | 'success';

export interface ToastPayload {
  text: string;
  kind?: ToastKind;
}

export const useToastStore = defineStore('toast', {
  state: () => ({
    message: '',
    kind: 'info' as ToastKind,
    visible: false,
  }),
  actions: {
    show(message: string, kind: ToastKind = 'info'): void {
      this.message = message;
      this.kind = kind;
      this.visible = true;
      window.setTimeout(() => {
        this.visible = false;
      }, 3000);
    },
    push(payload: ToastPayload): void {
      this.show(payload.text, payload.kind ?? 'info');
    },
    clear(): void {
      this.visible = false;
      this.message = '';
    },
  },
});
