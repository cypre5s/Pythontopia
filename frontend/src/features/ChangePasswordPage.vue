<template>
  <main class="arrival-page"><section class="arrival-panel password-required">
    <h2>请先设置自己的密码</h2>
    <p>当前使用的是初始密码。设置完成后即可继续学习。</p>
    <PasswordForm @changed="continueSession" />
    <button @click="logout">退出账号</button>
  </section></main>
</template>
<script setup lang="ts">
import { useRouter } from "vue-router";
import { useSession } from "../shared/session";
import { api } from "../shared/api";
import PasswordForm from "./PasswordForm.vue";
const router = useRouter(), session = useSession();
async function continueSession() {
  if (session.user?.role === "student") await router.replace("/world");
  else window.location.assign("/teacher/");
}
async function logout() {
  try { await api.post("/auth/logout", {}); }
  finally { session.reset(); await router.replace("/"); }
}
</script>
<style scoped>
.password-required { max-height:95vh; overflow:auto; }
</style>
