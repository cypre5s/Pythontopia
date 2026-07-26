<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue';
import type { RegisterRequest } from '@api/dto/RegisterRequest';
import type { UserDto } from '@api/dto/UserDto';

const props = defineProps<{
  /**
   * 注册请求进行中（与 LoginForm 同款契约）：禁用提交按钮，避免双击创建两个账号 / 触发两次登录。
   * 父页尚未接线时，组件内部的连点锁（见 SUBMIT_LOCK_MS）仍能兜住同一瞬间的重复提交。
   */
  loading?: boolean;
}>();

const emit = defineEmits<{
  submit: [value: RegisterRequest];
}>();

/**
 * 连点锁窗口（毫秒）。父页未传 `loading` 时的兜底：emit 后短时间内禁用按钮，
 * 吞掉「创建账号」的双击；超时自动解锁，注册失败的学生仍能立刻重试（不会变成死路）。
 */
const SUBMIT_LOCK_MS = 1500;
const locked = ref(false);
let lockTimer: number | null = null;

const busy = computed<boolean>(() => Boolean(props.loading) || locked.value);

function lockBriefly(): void {
  locked.value = true;
  if (typeof window === 'undefined') return;
  if (lockTimer !== null) window.clearTimeout(lockTimer);
  lockTimer = window.setTimeout(() => {
    locked.value = false;
    lockTimer = null;
  }, SUBMIT_LOCK_MS);
}

onBeforeUnmount(() => {
  if (lockTimer !== null && typeof window !== 'undefined') {
    window.clearTimeout(lockTimer);
    lockTimer = null;
  }
});

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
  if (busy.value) return;
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
  lockBriefly();
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
    <button
      type="submit"
      data-testid="register-submit"
      :disabled="busy"
    >
      {{ busy ? '创建中...' : '创建账号' }}
    </button>
  </form>
</template>
