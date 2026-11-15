import { computed, inject, onBeforeUnmount, provide, ref, watch, type InjectionKey } from "vue";
import { api } from "../../shared/api";
import { canUseDevTools } from "../../shared/devTools";
import { useSession } from "../../shared/session";
import { experienceApi } from "./api";
import type { DeveloperTab, ExperienceOverview } from "./types";

const toolsKey: InjectionKey<ReturnType<typeof createDeveloperTools>> = Symbol("developer-tools");

export function createDeveloperTools() {
  const session = useSession();
  const activeTab = ref<DeveloperTab>("account");
  const overview = ref<ExperienceOverview>();
  const loading = ref(false);
  const loadError = ref("");
  const pending = ref(false);
  const allowed = computed(() => canUseDevTools(session.user));
  const classroom = computed(() => session.classes.find(item => item.id === session.classroomId));
  const busy = computed(() => pending.value || session.resetBusy);
  const identity = computed(() => [
    session.user?.id,
    session.classroomId,
    session.user?.role,
    session.user?.dev_tools_enabled,
    session.user?.town_preview,
  ].join(":"));
  let disposed = false;
  let generation = 0;
  let loadRequest = 0;

  function capture() {
    const owner = session.user?.id;
    const classroomId = session.classroomId;
    if (!allowed.value || !owner || !classroomId || !classroom.value) return;
    const token = api.token();
    const ticket = generation;
    const current = () => !disposed && ticket === generation && allowed.value
      && token === api.token() && owner === session.user?.id && classroomId === session.classroomId;
    return {
      owner,
      classroomId,
      current,
      client: experienceApi(classroomId, Boolean(session.user?.town_preview)),
    };
  }

  async function load() {
    const scope = capture();
    if (!scope) return;
    const request = ++loadRequest;
    loading.value = true;
    loadError.value = "";
    try {
      const result = await scope.client.overview();
      if (scope.current() && request === loadRequest) overview.value = result;
    } catch (cause) {
      if (scope.current() && request === loadRequest) {
        loadError.value = cause instanceof Error ? cause.message : "体验工具加载失败。";
      }
    } finally {
      if (scope.current() && request === loadRequest) loading.value = false;
    }
  }

  watch(identity, () => {
    generation++;
    loadRequest++;
    pending.value = false;
    loading.value = false;
    loadError.value = "";
    overview.value = undefined;
    activeTab.value = "account";
  }, { flush: "sync" });
  watch(activeTab, tab => {
    if (tab !== "account" && !overview.value && !loading.value) void load();
  });
  onBeforeUnmount(() => { disposed = true; generation++; });

  const tools = { session, activeTab, overview, loading, loadError, allowed, classroom, busy, pending, identity, capture, load };
  provide(toolsKey, tools);
  return tools;
}

export function useDeveloperTools() {
  const tools = inject(toolsKey);
  if (!tools) throw new Error("开发工具面板必须位于开发工作区内。");
  return tools;
}

type Scope = NonNullable<ReturnType<ReturnType<typeof createDeveloperTools>["capture"]>>;

/** Panels own their feedback; the workspace owns mutation exclusion and identity. */
export function useToolAction() {
  const tools = useDeveloperTools();
  const error = ref("");
  const message = ref("");
  const name = ref("");
  let disposed = false;
  onBeforeUnmount(() => { disposed = true; });

  function clear() {
    error.value = "";
    message.value = "";
  }

  async function run(action: string, task: (scope: Scope) => Promise<void>) {
    if (tools.busy.value) return;
    const captured = tools.capture();
    if (!captured) return;
    const current = () => !disposed && captured.current();
    tools.pending.value = true;
    name.value = action;
    clear();
    try {
      await task({ ...captured, current });
    } catch (cause) {
      if (current()) error.value = cause instanceof Error ? cause.message : "操作失败，请重试。";
    } finally {
      if (captured.current()) tools.pending.value = false;
    }
  }

  return { busy: tools.busy, error, message, name, clear, run };
}
