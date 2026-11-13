<template>
  <section class="dev-tools-workspace">
    <div class="dev-tools-card">
      <template v-if="allowed">
        <dl class="dev-tools-context">
          <div><dt>当前账户</dt><dd>{{ session.user?.name }}<span>{{ session.user?.student_id }}</span></dd></div>
          <div><dt>当前班级</dt><dd>{{ classroom?.name ?? '尚未选择班级' }}</dd></div>
        </dl>
        <section class="dev-tools-reset" aria-labelledby="dev-tools-reset-title">
          <h3 class="game-card-title" id="dev-tools-reset-title"><GameIcon name="settings"/>重新开始当前班级</h3>
          <p>清除本账号在当前班级的任务与学习进度、练习和测试成绩，回到初入小镇的状态。阅读位置和练习草稿也会清空。</p>
          <p>账户、已加入班级、课程材料和界面偏好会保留，其他班级的学习记录不受影响。</p>
          <p v-if="!classroom" class="dev-tools-note">请先选择要重新开始的班级。</p>
          <p v-if="error" class="dev-tools-error" role="alert">{{ error }}</p>
          <p v-if="message" class="dev-tools-note" role="status">{{ message }}</p>
          <section v-if="confirming" class="dev-tools-confirm" aria-labelledby="dev-tools-confirm-title">
            <h4 id="dev-tools-confirm-title">确认重新开始“{{ classroom?.name }}”？</h4>
            <p>当前班级的进度和成绩清除后无法恢复。</p>
            <div class="dev-tools-actions">
              <button class="town-primary" :disabled="busy || !canReset" @click="restart">{{ busy ? '正在重新开始…' : '确认重新开始' }}</button>
              <button class="town-secondary" :disabled="busy" @click="cancel">取消</button>
            </div>
          </section>
          <button v-else class="town-primary" :disabled="busy || !canReset" @click="requestConfirmation">重新开始当前班级</button>
        </section>
      </template>
      <p v-else role="alert">当前账户未开放开发工具。</p>
    </div>
  </section>
</template>
<script setup lang="ts">
import GameIcon from "../shared/GameIcon.vue";
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { api } from "../shared/api";
import { canUseDevTools } from "../shared/devTools";
import { useSession } from "../shared/session";

const session = useSession();
const confirming = ref(false), pending = ref(false), error = ref(""), message = ref("");
const allowed = computed(() => canUseDevTools(session.user));
const classroom = computed(() => session.classes.find(item => item.id === session.classroomId));
const canReset = computed(() => allowed.value && Boolean(classroom.value));
const busy = computed(() => pending.value || session.resetBusy);
let disposed = false, operation = 0;

function cancel() {
  confirming.value = false;
  error.value = "";
  message.value = "";
}
function requestConfirmation() {
  if (busy.value || !canReset.value) return;
  cancel();
  confirming.value = true;
}
watch([() => session.user?.id, () => session.classroomId, () => session.user?.role, () => session.user?.dev_tools_enabled], () => {
  operation++;
  pending.value = false;
  cancel();
}, { flush: "sync" });
onBeforeUnmount(() => { disposed = true; operation++; });

async function restart() {
  if (!confirming.value || busy.value || !canReset.value) return;
  const owner = session.user?.id, classroomId = session.classroomId, token = api.token(), generation = ++operation;
  const current = () => !disposed && generation === operation && owner === session.user?.id && classroomId === session.classroomId && token === api.token() && allowed.value;
  pending.value = true;
  error.value = "";
  try {
    const completed = await session.restartCurrentClassroom();
    if (!current()) return;
    if (completed) {
      confirming.value = false;
      message.value = "已重新开始当前班级。";
    } else error.value = "重新开始未完成，请重试。";
  } catch (cause) {
    if (current()) error.value = cause instanceof Error ? cause.message : "重新开始失败，请重试。";
  } finally {
    if (!disposed && generation === operation) {
      pending.value = false;
      if (!current()) cancel();
    }
  }
}
</script>
<style scoped>
.dev-tools-workspace { flex:1; min-width:0; min-height:0; overflow:auto; }
.dev-tools-card { max-width:none; padding:var(--game-panel-padding); border:1px solid var(--game-line); border-radius:var(--game-radius); color:var(--game-ink); background:var(--game-paper); }
.dev-tools-context { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:12px; margin:0 0 16px; padding-bottom:12px; border-bottom:1px solid var(--game-line); }
.dev-tools-context > div { min-width:0; }.dev-tools-context dt { color:var(--game-muted); font-size:.875rem; margin-bottom:10px; }.dev-tools-context dd { margin:0; line-height:1.7; overflow-wrap:anywhere; }.dev-tools-context dd span { display:block; color:var(--game-muted); font-size:.875rem; margin-top:5px; }
.dev-tools-reset h3 { margin:0 0 16px; color:var(--game-forest); font-size:.875rem; line-height:1.5; }.dev-tools-reset > p,.dev-tools-confirm p { font-size:.9375rem; line-height:1.7; color:var(--game-muted); margin:0 0 16px; }
.dev-tools-reset > button { margin-top:8px; }.dev-tools-confirm { margin-top:16px; padding-top:16px; border-top:1px solid var(--game-line); }.dev-tools-confirm h4 { margin:0 0 12px; font-size:.875rem; line-height:1.7; overflow-wrap:anywhere; }
.dev-tools-actions { display:flex; align-items:center; flex-wrap:wrap; gap:12px; }.dev-tools-reset > .dev-tools-error { padding:12px 16px; border:1px solid #d6b09a; border-radius:var(--game-control-radius); color:#8d4935; background:#fae9dc; }.dev-tools-reset > .dev-tools-note { color:var(--game-forest); }
@media(max-width:700px) { .dev-tools-context { grid-template-columns:minmax(0,1fr); gap:20px; }.dev-tools-actions > button { flex:1 1 auto; } }
</style>
