<template>
  <section class="workspace-padding settings-workspace">
    <p class="eyebrow">居民设置</p>
    <h2>{{ session.user?.name }}，欢迎回来。</h2>
    <p class="muted">{{ session.user?.email }}</p>
    <div class="settings-links">
      <button @click="session.navigate({ kind: 'classroom' })">
        班级与课程 →</button
      ><button @click="session.navigate({ kind: 'library' })">
        已开放材料 →</button
      ><RouterLink v-if="session.user?.role !== 'student'" to="/teacher"
        >教师工作台 →</RouterLink
      ><RouterLink to="/credits">素材许可与来源 →</RouterLink>
    </div>
    <fieldset class="interface-settings">
      <legend>让小镇更适合你</legend>
      <label class="text-size-setting"
        >文字大小<select v-model="preferences.textScale">
          <option v-for="scale in textScales" :key="scale" :value="scale">
            {{ scale * 100 }}%{{ scale === 1 ? " · 标准" : "" }}
          </option>
        </select></label
      >
      <label class="checkbox-label"
        ><input
          v-model="preferences.reduceMotion"
          type="checkbox"
        />减少界面动效</label
      >
      <p class="muted">
        减少过渡动画，对话直接显示全文；同时遵循设备的减少动态效果设置。
      </p>
      <label class="checkbox-label"
        ><input
          v-model="session.audioEnabled"
          type="checkbox"
        />播放小镇环境音乐</label
      >
    </fieldset>
    <details class="controls-guide">
      <summary>操作指南</summary>
      <dl>
        <div>
          <dt>移动</dt>
          <dd>WASD / 方向键，或点击道路</dd>
        </div>
        <div>
          <dt>互动</dt>
          <dd>E / 点击附近人物 / 触屏“互动”</dd>
        </div>
        <div>
          <dt>菜单</dt>
          <dd>J 打开任务日志；Esc 返回上一级或打开设置</dd>
        </div>
      </dl>
      <p>学习与对话期间暂停移动。正式进度由服务端保存。</p>
    </details>
    <footer class="settings-footer">
      <span>界面偏好保存在当前设备</span
      ><button class="danger" @click="logout">退出账号</button>
    </footer>
  </section>
</template>
<script setup lang="ts">
import { RouterLink, useRouter } from "vue-router";
import { useSession } from "../shared/session";
import { api } from "../shared/api";
import { useUiPreferences, textScales } from "../shared/uiPreferences";
const preferences = useUiPreferences();
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
