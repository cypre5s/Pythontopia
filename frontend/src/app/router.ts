import { createRouter, createWebHistory } from "vue-router";
import { api } from "../shared/api";
export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/", component: () => import("../features/EntryPage.vue") },
    { path: "/world", component: () => import("../features/WorldPage.vue") },
    {
      path: "/teacher",
      component: () => import("../features/TeacherPage.vue"),
    },
    {
      path: "/credits",
      component: () => import("../features/CreditsPage.vue"),
    },
    { path: "/:pathMatch(.*)*", redirect: "/world" },
  ],
});
router.beforeEach((to) => {
  if (to.path !== "/" && to.path !== "/credits" && !api.token()) return "/";
});
