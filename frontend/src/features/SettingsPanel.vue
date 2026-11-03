<template>
  <section class="workspace-padding settings-workspace">
    <p class="eyebrow">居民设置</p>
    <h2>{{ session.user?.name }}，欢迎回来。</h2>
    <p class="muted">{{ session.user?.email }}</p>
    <div class="settings-links">
      <button @click="session.open({ kind: 'classroom' })">班级与课程 →</button
      ><button @click="session.open({ kind: 'library' })">已开放材料 →</button
      ><RouterLink v-if="session.user?.role !== 'student'" to="/teacher"
        >教师工作台 →</RouterLink
      ><RouterLink to="/credits">素材许可与来源 →</RouterLink>
    </div>
    <p class="notice">
      移动：WASD / 方向键 / 点击道路<br />互动：E 或点击附近人物<br />J
      打开任务日志，Esc 关闭当前工作区。编辑时世界暂停。
    </p>
    <label class="checkbox-label"
      ><input
        v-model="session.audioEnabled"
        type="checkbox"
      />播放小镇环境音乐</label
    ><br /><button class="danger" @click="logout">退出账号</button>
  </section>
</template>
<script setup lang="ts">
import { RouterLink, useRouter } from "vue-router";
import { useSession } from "../shared/session";
import { api } from "../shared/api";
const session = useSession(),
  router = useRouter();
async function logout() {
  try {
    await api.post("/auth/logout", {});
  } finally {
    session.reset();
    await router.push("/");
  }
}
</script>
