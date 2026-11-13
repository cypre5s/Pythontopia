import { onBeforeUnmount, ref, watch, type Ref } from "vue";
import { api } from "./api";

export function useCharacterImage(source: Ref<string | undefined>) {
  const url = ref("");
  let controller: AbortController | undefined;
  let objectUrl: string | undefined;
  function clear() {
    controller?.abort();
    if (objectUrl) URL.revokeObjectURL(objectUrl);
    objectUrl = undefined;
    url.value = "";
  }
  watch(source, (path) => {
    clear();
    if (!path) return;
    if (!path.startsWith("/api/")) { url.value = path; return; }
    const request = new AbortController();
    const token = api.token();
    controller = request;
    void api.blob(path, request.signal).then((blob) => {
      if (request.signal.aborted || token !== api.token()) return;
      objectUrl = URL.createObjectURL(blob);
      url.value = objectUrl;
    }).catch(() => { /* The image stays empty when access or loading fails. */ });
  }, { immediate: true });
  onBeforeUnmount(clear);
  return url;
}
