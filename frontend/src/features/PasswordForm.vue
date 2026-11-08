<template>
  <form class="password-form" @submit.prevent="submit">
    <h3>修改密码</h3>
    <p>新密码至少10位，不能与学号或当前密码相同。修改后其他设备需要重新登录。</p>
    <label>当前密码<input v-model="currentPassword" type="password" autocomplete="current-password" maxlength="200" required :disabled="busy" /></label>
    <label>新密码<input v-model="newPassword" type="password" autocomplete="new-password" minlength="10" maxlength="200" required :disabled="busy" /></label>
    <label>确认新密码<input v-model="confirmation" type="password" autocomplete="new-password" minlength="10" maxlength="200" required :disabled="busy" /></label>
    <p v-if="error" role="alert">{{ error }}</p>
    <p v-if="success" role="status">密码已修改，其他设备的登录已失效。</p>
    <button type="submit" :disabled="busy">{{ busy ? '正在保存…' : '保存新密码' }}</button>
  </form>
</template>
<script setup lang="ts">
import { ref } from "vue";
import { api } from "../shared/api";
import { useSession } from "../shared/session";
import type { User } from "../shared/types";
const emit = defineEmits<{ changed: [] }>();
const session = useSession();
const currentPassword = ref(""), newPassword = ref(""), confirmation = ref(""), error = ref(""), success = ref(false), busy = ref(false);
async function submit() {
  if (busy.value) return;
  error.value = ""; success.value = false;
  if (newPassword.value !== confirmation.value) { error.value = "两次输入的新密码不一致"; return; }
  busy.value = true;
  try {
    const result = await api.post<{ access_token: string; user: User }>("/auth/password", { current_password: currentPassword.value, new_password: newPassword.value });
    api.setToken(result.access_token); session.user = result.user;
    currentPassword.value = ""; newPassword.value = ""; confirmation.value = "";
    success.value = true; emit("changed");
  } catch (e) { error.value = e instanceof Error ? e.message : "修改失败"; }
  finally { busy.value = false; }
}
</script>
<style scoped>
.password-form { display:grid; gap:14px; max-width:520px; padding:20px; }
.password-form h3, .password-form p { margin:0; }
.password-form label { display:grid; gap:6px; }
.password-form input { width:100%; min-height:44px; }
.password-form button { min-height:44px; }
</style>
