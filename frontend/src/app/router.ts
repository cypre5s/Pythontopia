import { createRouter, createWebHistory } from "vue-router";
import { api } from "../shared/api";
import { useSession } from "../shared/session";
import type { User } from "../shared/types";
export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/change-password", component: () => import("../features/ChangePasswordPage.vue") },
    { path: "/", component: () => import("../features/EntryPage.vue") },
    { path: "/world", component: () => import("../features/WorldPage.vue") },
    {
      path: "/teacher/:pathMatch(.*)*",
      component: { render: () => null },
      beforeEnter: (to) => { window.location.assign(to.fullPath === "/teacher" ? "/teacher/" : to.fullPath); return false; },
    },
    {
      path: "/credits",
      component: () => import("../features/CreditsPage.vue"),
    },
    { path: "/:pathMatch(.*)*", redirect: "/world" },
  ],
});
router.beforeEach(async (to) => {
  if (to.path !== "/" && to.path !== "/credits" && !api.token()) return "/";
  if (api.token() && to.path !== "/" && to.path !== "/credits") {
    const session = useSession();
    try { session.user = await api.get<User>("/auth/me"); }
    catch { session.reset(); return "/"; }
    if (session.user.must_change_password && to.path !== "/change-password") return "/change-password";
  }
});
