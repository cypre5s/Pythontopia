import { onBeforeUnmount, ref, type Ref } from 'vue';
import { getPyodideStatus, onPyodideStatus, type PyodideStatus } from '@game/adapters/pyodide';

/**
 * 订阅 Pyodide 运行时加载状态（idle/loading/ready/error）的响应式封装。
 * 供「运行代码」类面板在首次加载（约十几秒、~10MB）时展示进度/骨架，提升感知性能。
 */
export function usePyodideStatus(): Ref<PyodideStatus> {
  const status = ref<PyodideStatus>(getPyodideStatus());
  const off = onPyodideStatus((next) => {
    status.value = next;
  });
  onBeforeUnmount(off);
  return status;
}
