import { afterEach, expect, it, vi } from "vitest";
import { defineComponent, h, nextTick, ref } from "vue";
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { api } from "../src/shared/api";
import { useCharacterImage } from "../src/shared/characterImage";

let wrapper: VueWrapper | undefined;
afterEach(() => { wrapper?.unmount(); wrapper = undefined; vi.restoreAllMocks(); vi.unstubAllGlobals(); });
function screen(source = ref<string | undefined>("/api/classrooms/1/character-assets/a?v=first")) {
  const create = vi.fn().mockReturnValueOnce("blob:first").mockReturnValueOnce("blob:second");
  const revoke = vi.fn();
  vi.stubGlobal("URL", { createObjectURL: create, revokeObjectURL: revoke });
  wrapper = mount(defineComponent({ setup() { const url = useCharacterImage(source); return () => h("span", url.value); } }));
  return { source, create, revoke };
}

it("releases the replaced private picture and the current one on unmount", async () => {
  vi.spyOn(api, "token").mockReturnValue("current");
  vi.spyOn(api, "blob").mockResolvedValue(new Blob(["image"]));
  const { source, revoke } = screen();
  await flushPromises();
  expect(wrapper!.text()).toBe("blob:first");
  source.value = "/api/classrooms/2/character-assets/b?v=replacement";
  await flushPromises();
  expect(revoke).toHaveBeenCalledWith("blob:first");
  expect(wrapper!.text()).toBe("blob:second");
  wrapper!.unmount(); wrapper = undefined;
  expect(revoke).toHaveBeenCalledWith("blob:second");
});

it("ignores a late private response after account change and aborts on exit", async () => {
  const token = vi.spyOn(api, "token").mockReturnValue("old-user");
  let resolve!: (value: Blob) => void;
  const read = vi.spyOn(api, "blob").mockReturnValue(new Promise<Blob>((done) => { resolve = done; }));
  const { source, create } = screen();
  token.mockReturnValue("new-user");
  resolve(new Blob(["private"]));
  await flushPromises();
  expect(create).not.toHaveBeenCalled();
  expect(wrapper!.text()).toBe("");
  const signal = read.mock.calls[0][1]!;
  source.value = undefined;
  await nextTick();
  expect(signal.aborted).toBe(true);
});
