import { mount, flushPromises } from "@vue/test-utils";
import { expect, it, vi } from "vitest";
import CharacterSprite from "../src/shared/CharacterSprite.vue";
import { api } from "../src/shared/api";

it("uses the configured sheet and frame size, revoking private images on replacement and unmount", async () => {
  const blob = vi.spyOn(api, "blob").mockResolvedValue(new Blob(["fixture"]));
  const create = vi.fn().mockReturnValueOnce("blob:first").mockReturnValueOnce("blob:second");
  const revoke = vi.fn();
  vi.stubGlobal("URL", class extends URL { static createObjectURL = create; static revokeObjectURL = revoke; });
  const wrapper = mount(CharacterSprite, { props: { sprite: { id:"one", url:"/api/classrooms/1/character-assets/one", frame_width:16, frame_height:20 } } });
  await flushPromises();
  expect(wrapper.get("span").attributes("style")).toContain('blob:first');
  expect(wrapper.get("span").attributes("style")).toContain('height: 28px');
  await wrapper.setProps({ sprite: { id:"two", url:"/api/classrooms/2/character-assets/two", frame_width:32, frame_height:32 } });
  await flushPromises();
  expect(revoke).toHaveBeenCalledWith("blob:first");
  expect(wrapper.get("span").attributes("style")).toContain('width: 28px');
  wrapper.unmount();
  expect(revoke).toHaveBeenCalledWith("blob:second");
  blob.mockRestore(); vi.unstubAllGlobals();
});
