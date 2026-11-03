<template>
  <main class="entry-page">
    <div class="entry-art">
      <p class="eyebrow">PYTHONTOPIA · REBORN</p>
      <h1>在小镇里，<br />写下你的<br /><em>第一行 Python。</em></h1>
      <p>认识居民，接受委托，寻找知识。<br />每一步探索，都来自真实的学习。</p>
      <span class="entry-seal">一座小镇 · 一个新的开始</span>
    </div>
    <section class="entry-form">
      <p class="eyebrow">欢迎来到小镇</p>
      <h2>{{ register ? "领取你的居民证" : "继续你的旅程" }}</h2>
      <form @submit.prevent="submit">
        <label v-if="register"
          >名字<input
            v-model="name"
            autocomplete="name"
            required
            maxlength="80" /></label
        ><label
          >邮箱<input
            v-model="email"
            type="email"
            autocomplete="username"
            required /></label
        ><label
          >密码<input
            v-model="password"
            type="password"
            :autocomplete="register ? 'new-password' : 'current-password'"
            required
            minlength="10"
        /></label>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <button class="primary" :disabled="busy">
          {{ busy ? "正在进入…" : register ? "注册并进入" : "进入小镇" }}
        </button>
      </form>
      <button class="text-button" @click="register = !register">
        {{ register ? "已有居民证？登录" : "初次到访？注册居民证" }}
      </button>
      <p class="muted">学生可直接领取居民证；教师请使用学校提供的账号登录。</p>
      <RouterLink to="/credits">素材许可与来源</RouterLink>
    </section>
  </main>
</template>
<script setup lang="ts">
import { ref } from "vue";
import { useRouter, RouterLink } from "vue-router";
import { api } from "../shared/api";
import { useSession } from "../shared/session";
import type { User } from "../shared/types";
const register = ref(false),
  name = ref(""),
  email = ref(""),
  password = ref(""),
  error = ref(""),
  busy = ref(false),
  router = useRouter(),
  session = useSession();
async function submit() {
  busy.value = true;
  error.value = "";
  try {
    session.reset();
    const result = await api.post<{ access_token: string; user: User }>(
      `/auth/${register.value ? "register" : "login"}`,
      register.value
        ? { email: email.value, password: password.value, name: name.value }
        : { email: email.value, password: password.value },
    );
    api.setToken(result.access_token);
    session.user = result.user;
    await router.push(result.user.role === "student" ? "/world" : "/teacher");
  } catch (e) {
    error.value = e instanceof Error ? e.message : "进入失败";
  } finally {
    busy.value = false;
  }
}
</script>
