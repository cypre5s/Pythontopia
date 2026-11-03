import { describe, it, expect, vi, beforeEach } from "vitest";
import { mount, flushPromises } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import EntryPage from "../src/features/EntryPage.vue";
import { api } from "../src/shared/api";
vi.mock("vue-router", () => ({
  useRouter: () => ({ push: vi.fn() }),
  RouterLink: { template: "<a><slot /></a>" },
}));
describe("login wire contract", () => {
  beforeEach(() => setActivePinia(createPinia()));
  it("only sends email and password, never the registration name field", async () => {
    const request = vi.spyOn(api, "post").mockResolvedValue({
      access_token: "token",
      user: { id: 1, email: "a@b.test", name: "Student", role: "student" },
    });
    const wrapper = mount(EntryPage, { global: { plugins: [createPinia()] } });
    await wrapper.find('input[type="email"]').setValue("a@b.test");
    await wrapper.find('input[type="password"]').setValue("a-long-password");
    await wrapper.find("form").trigger("submit.prevent");
    await flushPromises();
    expect(request).toHaveBeenCalledWith("/auth/login", {
      email: "a@b.test",
      password: "a-long-password",
    });
    request.mockRestore();
  });
});
