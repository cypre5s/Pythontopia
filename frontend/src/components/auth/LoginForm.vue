<script setup lang="ts">
import { nextTick, reactive, ref } from 'vue';
import type { LoginRequest } from '@api/dto/LoginRequest';

const props = defineProps<{
  loading?: boolean;
}>();

const emit = defineEmits<{
  submit: [value: LoginRequest];
}>();

const form = reactive<LoginRequest>({
  username: '',
  password: '',
});
const error = ref('');
const errorField = ref<'username' | 'password' | null>(null);
const usernameInput = ref<HTMLInputElement | null>(null);
const passwordInput = ref<HTMLInputElement | null>(null);

function submit(): void {
  error.value = '';
  errorField.value = null;
  if (form.username.trim().length < 3) {
    error.value = '用户名至少 3 个字符';
    errorField.value = 'username';
    void nextTick(() => usernameInput.value?.focus());
    return;
  }
  if (form.password.length < 8) {
    error.value = '密码至少 8 个字符';
    errorField.value = 'password';
    void nextTick(() => passwordInput.value?.focus());
    return;
  }
  emit('submit', { username: form.username.trim(), password: form.password });
}

defineExpose({
  fill(username: string, password: string): void {
    form.username = username;
    form.password = password;
  },
  submit,
});
</script>

<template>
  <form
    class="auth-form"
    @submit.prevent="submit"
  >
    <label>
      <span>用户名</span>
      <input
        ref="usernameInput"
        v-model="form.username"
        :class="{ 'has-error': errorField === 'username' }"
        name="username"
        autocomplete="username"
        placeholder="请输入用户名，例如 demo"
        :disabled="props.loading"
        :aria-invalid="errorField === 'username'"
        :aria-describedby="errorField === 'username' ? 'login-error' : undefined"
      >
    </label>
    <label>
      <span>密码</span>
      <input
        ref="passwordInput"
        v-model="form.password"
        :class="{ 'has-error': errorField === 'password' }"
        name="password"
        autocomplete="current-password"
        type="password"
        placeholder="密码至少 8 位"
        :disabled="props.loading"
        :aria-invalid="errorField === 'password'"
        :aria-describedby="errorField === 'password' ? 'login-error' : undefined"
      >
    </label>
    <p
      v-if="error"
      id="login-error"
      class="form-error"
      role="alert"
    >
      {{ error }}
    </p>
    <button
      type="submit"
      :disabled="props.loading"
    >
      {{ props.loading ? '登录中...' : '进入小镇' }}
    </button>
  </form>
</template>
