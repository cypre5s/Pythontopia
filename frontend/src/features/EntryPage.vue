<template>
  <main class="arrival-page">

    <strong class="arrival-brand">PYTHONTOPIA</strong>
    <section class="arrival-panel" :class="{ departing }" :inert="departing">
      <p class="eyebrow">欢迎来到小镇</p>
      <h2>{{ register ? "注册" : "登录" }}</h2>
      <form @submit.prevent="submit">
        <label v-if="register"
          >名字<input
            v-model="name"
            autocomplete="name"
            required
            maxlength="80" /></label
        ><label
          >学号<input
            v-model="student_id"
            type="text"
            inputmode="numeric"
            pattern="[0-9]{1,32}"
            maxlength="32"
            autocomplete="username"
            required /></label
        ><label
          >密码<input
            v-model="password"
            type="password"
            :autocomplete="register ? 'new-password' : 'current-password'"
            required
            :minlength="register ? 6 : 1"
        /></label>
        <p v-if="error || resourceError" class="error" role="alert">{{ error || resourceError }}</p>
        <button class="primary" :disabled="busy || !backdropReady || !worldAssetsReady">
          {{ !backdropReady || !worldAssetsReady ? "正在准备小镇…" : busy ? "正在进入…" : register ? "注册并进入" : "进入小镇" }}
        </button>
      </form>
      <button class="text-button" :disabled="busy" @click="register = !register; error = ''">
        {{ register ? "已有居民证？登录" : "初次到访？注册居民证" }}
      </button>
      <p class="muted">学生可直接领取居民证；教师请使用学校提供的账号登录。</p>
      <RouterLink to="/credits">素材许可与来源</RouterLink>
    </section>

  </main>
</template>
<script setup lang="ts">
import { ref, onMounted } from "vue";
import { preloadWorld, startArrival, endArrival, backdropReady, worldAssetsReady, resourceError } from "../shared/arrival";
import { useRouter, RouterLink } from "vue-router";
import { api } from "../shared/api";
import { useSession } from "../shared/session";
import type { User } from "../shared/types";
const register = ref(false),
  name = ref(""),
  student_id = ref(""),
  password = ref(""),
  error = ref(""),
  busy = ref(false),
  departing = ref(false),
  router = useRouter(),
  session = useSession();
onMounted(() => { void preloadWorld().catch(() => { resourceError.value = "游戏资源加载失败，请刷新重试。"; }); });
async function submit() {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  try {
    session.reset();
    const result = await api.post<{ access_token: string; user: User }>(
      `/auth/${register.value ? "register" : "login"}`,
      register.value
        ? { student_id: student_id.value, password: password.value, name: name.value }
        : { student_id: student_id.value, password: password.value },
    );
    api.setToken(result.access_token);
    session.user = result.user;
    if (result.user.must_change_password) { await router.push("/change-password"); return; }
    if (result.user.role === "student") {
      sessionStorage.setItem("reborn-arrival", "yes");
      await preloadWorld();
      await session.load();
      startArrival();
      departing.value = true;
      await new Promise(resolve => setTimeout(resolve, 450));
    }
    await router.push(result.user.role === "student" ? "/world" : "/teacher");
  } catch (e) {
    endArrival();
    departing.value = false;
    error.value = e instanceof Error ? e.message : "进入失败";
  } finally {
    busy.value = false;
  }
}
</script>
