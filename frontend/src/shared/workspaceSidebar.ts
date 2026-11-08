import { inject, nextTick, provide, ref, type InjectionKey, type Ref } from "vue";

interface WorkspaceSidebar {
  collapsed: Ref<boolean>;
  collapse: () => void;
  expand: () => void;
}
const sidebarKey: InjectionKey<WorkspaceSidebar> = Symbol("workspace-sidebar");

export function createWorkspaceSidebar(container?: () => HTMLElement | undefined): WorkspaceSidebar {
  const collapsed = ref(false);
  const focus = async (selector: string) => {
    await nextTick();
    (container?.() ?? document).querySelector<HTMLElement>(selector)?.focus({ preventScroll: true });
  };
  return {
    collapsed,
    collapse() { collapsed.value = true; void focus(".workspace-sidebar-expand"); },
    expand() { collapsed.value = false; void focus(".sidebar-toggle"); },
  };
}

export function provideWorkspaceSidebar(sidebar: WorkspaceSidebar) { provide(sidebarKey, sidebar); }

/** Standalone previews/tests keep their own state; the real overlay owns one provider. */
export function useWorkspaceSidebar(): WorkspaceSidebar {
  const inherited = inject(sidebarKey, undefined);
  if (inherited) return inherited;
  const local = createWorkspaceSidebar();
  provideWorkspaceSidebar(local);
  return local;
}
