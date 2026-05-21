<script setup lang="ts">
import { reactive, ref } from 'vue';
import type { LoginRequest } from '@api/dto/LoginRequest';

const emit = defineEmits<{
  submit: [value: LoginRequest];
}>();

const form = reactive<LoginRequest>({
  username: '',
  password: '',
});
const error = ref('');

function submit(): void {
  error.value = '';
  if (form.username.trim().length < 3) {
    error.value = '用户名至少 3 个字符';
    return;
  }
  if (form.password.length < 8) {
    error.value = '密码至少 8 个字符';
    return;
  }
  emit('submit', { username: form.username.trim(), password: form.password });
}
</script>

<template>
  <form
    class="auth-form"
    @submit.prevent="submit"
  >
    <label>
      <span>用户名</span>
      <input
        v-model="form.username"
        autocomplete="username"
      >
    </label>
    <label>
      <span>密码</span>
      <input
        v-model="form.password"
        autocomplete="current-password"
        type="password"
      >
    </label>
    <p
      v-if="error"
      class="form-error"
    >
      {{ error }}
    </p>
    <button type="submit">
      进入小镇
    </button>
  </form>
</template>
