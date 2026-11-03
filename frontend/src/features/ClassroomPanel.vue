<template>
  <section class="workspace-padding">
    <p class="eyebrow">班级与课程</p>
    <h2>和同学一起出发</h2>
    <div class="class-list">
      <button
        v-for="classroom in session.classes"
        :key="classroom.id"
        :class="{ selected: session.classroomId === classroom.id }"
        @click="switchClass(classroom.id)"
      >
        <strong>{{ classroom.name }}</strong
        ><span>{{
          session.classroomId === classroom.id ? "当前班级" : "切换班级"
        }}</span>
      </button>
    </div>
    <form class="inline-form" @submit.prevent="join">
      <label
        >加入新的班级<input
          v-model="joinCode"
          placeholder="教师提供的加入码"
          required /></label
      ><button class="primary">加入</button>
    </form>
    <p class="notice">
      章节开放与学习记录按当前班级保存。切换班级将关闭工作区并重新载入存档。
    </p>
  </section>
</template>
<script setup lang="ts">
import { ref } from "vue";
import { useSession } from "../shared/session";
import { api, notify } from "../shared/api";
const session = useSession(),
  joinCode = ref("");
async function switchClass(id: number) {
  try {
    session.open(null);
    session.classroomId = id;
    session.drafts = {};
    await session.refresh();
  } catch (e) {
    notify(e instanceof Error ? e.message : "切换失败");
  }
}
async function join() {
  try {
    await api.post("/classrooms/join", { join_code: joinCode.value });
    await session.load();
    joinCode.value = "";
    notify("已加入班级");
  } catch (e) {
    notify(e instanceof Error ? e.message : "加入失败");
  }
}
</script>
