<script setup lang="ts">
import { useRouter } from 'vue-router';
import RegisterForm from '@/components/auth/RegisterForm.vue';
import { ApiClientError } from '@api/http';
import type { RegisterRequest } from '@api/dto/RegisterRequest';
import { useSessionStore } from '@stores/session';
import { useToastStore } from '@stores/toast';

const session = useSessionStore();
const toast = useToastStore();
const router = useRouter();

async function submit(req: RegisterRequest): Promise<void> {
  try {
    await session.register(req);
    await session.login({ username: req.username, password: req.password });
    await router.push('/town');
  } catch (error) {
    const message = error instanceof ApiClientError ? error.message : '注册失败';
    toast.show(message, 'error');
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-copy">
      <h1>创建居民档案</h1>
      <p>学生账号会进入小镇；教师账号需要邀请码。</p>
    </section>
    <section class="auth-card">
      <h2>注册</h2>
      <RegisterForm @submit="submit" />
      <RouterLink to="/login">
        已有账号，去登录
      </RouterLink>
    </section>
  </main>
</template>
