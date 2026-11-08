<script setup lang="ts">
import { ref, nextTick } from "vue";
import { ChevronsUpDown, KeyRound, LogOut } from "@lucide/vue";
import { DropdownMenuRoot, DropdownMenuTrigger, DropdownMenuPortal, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator } from "reka-ui";
import { useSession } from "../../shared/session";
import AdminDialog from "./AdminDialog.vue";
import PasswordForm from "../../features/PasswordForm.vue";
const emit = defineEmits<{ logout: [] }>();
const session = useSession(), passwordOpen = ref(false), trigger = ref<HTMLButtonElement | null>(null);
function openPassword() { passwordOpen.value = true; }
async function dialogChanged(open: boolean) {
  passwordOpen.value = open;
  if (!open) { await nextTick(); trigger.value?.focus(); }
}
</script>
<template>
  <DropdownMenuRoot>
    <DropdownMenuTrigger as-child>
      <button ref="trigger" class="teacher-account account-menu-trigger" :aria-label="`${session.user?.name ?? '教师'}，账号菜单`">
        <span class="account-avatar">{{ session.user?.name?.slice(0, 1) || '师' }}</span>
        <span>{{ session.user?.name || '教师' }}<small>{{ session.user?.role === 'admin' ? '管理员' : '教师账号' }}</small></span>
        <ChevronsUpDown :size="16" />
      </button>
    </DropdownMenuTrigger>
    <DropdownMenuPortal>
      <DropdownMenuContent class="teacher-dialog account-dropdown" side="top" align="start" :side-offset="8" @close-auto-focus="event => { if (passwordOpen) event.preventDefault(); }">
        <DropdownMenuItem class="account-menu-item" @select="openPassword"><KeyRound :size="16" />修改密码</DropdownMenuItem>
        <DropdownMenuSeparator class="account-menu-separator" />
        <DropdownMenuItem class="account-menu-item account-signout" @select="emit('logout')"><LogOut :size="16" />退出账号</DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenuPortal>
  </DropdownMenuRoot>
  <AdminDialog :open="passwordOpen" title="修改密码" description="修改当前登录账号的密码。" @update:open="dialogChanged">
    <PasswordForm v-if="passwordOpen" compact class="admin-password-form" />
  </AdminDialog>
</template>
