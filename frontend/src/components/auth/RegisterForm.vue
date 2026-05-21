<script setup lang="ts">
import { reactive, ref } from 'vue';
import type { RegisterRequest } from '@api/dto/RegisterRequest';
import type { UserDto } from '@api/dto/UserDto';

const emit = defineEmits<{
  submit: [value: RegisterRequest];
}>();

const form = reactive<RegisterRequest>({
  username: '',
  password: '',
  displayName: '',
  role: 'STUDENT',
  email: '',
  teacherInviteCode: '',
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
  if (form.displayName.trim().length < 1) {
    error.value = '请填写显示名称';
    return;
  }
  const role: UserDto['role'] = form.role;
  emit('submit', {
    username: form.username.trim(),
    password: form.password,
    displayName: form.displayName.trim(),
    role,
    ...(form.email ? { email: form.email.trim() } : {}),
    ...(role === 'TEACHER' && form.teacherInviteCode
      ? { teacherInviteCode: form.teacherInviteCode.trim() }
      : {}),
  });
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
      <span>显示名称</span>
      <input
        v-model="form.displayName"
        autocomplete="nickname"
      >
    </label>
    <label>
      <span>密码</span>
      <input
        v-model="form.password"
        autocomplete="new-password"
        type="password"
      >
    </label>
    <label>
      <span>身份</span>
      <select v-model="form.role">
        <option value="STUDENT">学生</option>
        <option value="TEACHER">教师</option>
      </select>
    </label>
    <label>
      <span>邮箱</span>
      <input
        v-model="form.email"
        autocomplete="email"
        type="email"
      >
    </label>
    <label v-if="form.role === 'TEACHER'">
      <span>教师邀请码</span>
      <input v-model="form.teacherInviteCode">
    </label>
    <p
      v-if="error"
      class="form-error"
    >
      {{ error }}
    </p>
    <button type="submit">
      创建账号
    </button>
  </form>
</template>
