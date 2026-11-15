<template>
  <div class="developer-panel">
    <dl class="developer-account">
      <div><dt>当前学生</dt><dd>{{ session.user?.name }}</dd></div>
      <div><dt>身份</dt><dd>{{ session.user?.town_preview ? '教师体验学生' : '学生' }}</dd></div>
      <div><dt>学号</dt><dd>{{ session.user?.student_id }}</dd></div>
      <div><dt>当前班级</dt><dd>{{ classroom?.name ?? '尚未选择班级' }}</dd></div>
    </dl>
    <section class="developer-reset" aria-labelledby="developer-reset-title">
      <h4 id="developer-reset-title" class="game-card-title"><GameIcon name="refresh" />重置当前学生进度</h4>
      <p class="developer-help">仅清除本学生在当前班级的任务、阅读进度、练习和测验成绩，以及对应的阅读位置和草稿。</p>
      <p class="developer-help">账户、班级关系、课程资料、界面偏好、已保存检查点和其他学生的记录会保留；其他班级不受影响。</p>
      <p v-if="!classroom" class="developer-help">请先选择已加入的班级。</p>
      <section v-if="confirming" class="developer-confirm" aria-labelledby="developer-reset-confirm-title">
        <h4 id="developer-reset-confirm-title">确认重置“{{ session.user?.name }}”的学生进度？</h4>
        <p>作用班级：{{ classroom?.name }}。本学生在此班级的进度和成绩将被清除，此操作无法撤销。</p>
        <div class="developer-actions">
          <button class="town-primary" :disabled="action.busy" @click="restart">
            {{ action.busy ? '正在重置…' : '确认重置学生进度' }}
          </button>
          <button class="town-secondary" :disabled="action.busy" @click="cancel">取消</button>
        </div>
      </section>
      <button v-else class="town-secondary" :disabled="action.busy || !classroom" @click="requestConfirmation">
        <GameIcon name="refresh" />重置当前学生进度
      </button>
      <ToolFeedback :error="action.error" :message="action.message" />
    </section>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from "vue";
import GameIcon from "../../shared/GameIcon.vue";
import ToolFeedback from "./ToolFeedback.vue";
import { useDeveloperTools, useToolAction } from "./useDeveloperTools";

const { session, classroom, activeTab } = useDeveloperTools();
const action = reactive(useToolAction());
const confirming = ref(false);
watch(activeTab, () => { confirming.value = false; });

function cancel() {
  confirming.value = false;
  action.clear();
}

function requestConfirmation() {
  action.clear();
  confirming.value = true;
}

function restart() {
  if (!confirming.value) return;
  return action.run("reset", async ({ current }) => {
    const completed = await session.restartCurrentClassroom();
    if (!current()) return;
    if (!completed) throw new Error("学生进度重置未完成，请重试。");
    confirming.value = false;
    action.message = "当前学生在本班的进度已重置。";
  });
}
</script>
