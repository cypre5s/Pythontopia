<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router';
import LoginForm from '@/components/auth/LoginForm.vue';
import { ApiClientError } from '@api/http';
import type { LoginRequest } from '@api/dto/LoginRequest';
import { useSessionStore } from '@stores/session';
import { useToastStore } from '@stores/toast';

const session = useSessionStore();
const toast = useToastStore();
const router = useRouter();
const route = useRoute();

async function submit(req: LoginRequest): Promise<void> {
  try {
    await session.login(req);
    await router.push(typeof route.query.redirect === 'string' ? route.query.redirect : '/town');
  } catch (error) {
    const message = error instanceof ApiClientError ? error.message : '登录失败';
    toast.show(message, 'error');
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-copy">
      <h1>Pythontopia</h1>
      <p>像素小镇里的 Python 语言基础学习入口。</p>
    </section>
    <section class="auth-card">
      <h2>登录</h2>
      <LoginForm @submit="submit" />
      <RouterLink to="/register">
        创建新账号
      </RouterLink>
    </section>
  </main>
</template>
