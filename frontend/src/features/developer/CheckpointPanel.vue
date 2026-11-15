<template>
  <div class="developer-panel">
    <section class="developer-section" aria-labelledby="developer-checkpoint-save">
      <h4 id="developer-checkpoint-save" class="game-card-title"><GameIcon name="save" />保存当前体验</h4>
      <form class="developer-field-action" @submit.prevent="save">
        <label class="town-field" for="developer-checkpoint-name">检查点名称
          <input id="developer-checkpoint-name" v-model="name" maxlength="80" :disabled="action.busy" placeholder="例如：第一章练习前">
        </label>
        <button type="submit" class="town-primary" :disabled="action.busy || !name.trim() || full">
          <GameIcon name="save" />保存检查点
        </button>
      </form>
      <p class="developer-help">保存任务、对白、阅读位置、当前活动与角色位置。正式提交、成绩和助教会话保留，不随恢复回退。</p>
      <ToolFeedback v-if="action.name === 'save'" :error="action.error" :message="action.message" />
    </section>
    <section class="developer-section" aria-labelledby="developer-checkpoint-list">
      <div class="developer-list-heading">
        <h4 id="developer-checkpoint-list" class="game-card-title"><GameIcon name="journal" />已保存的检查点</h4>
        <span class="developer-help">{{ overview?.checkpoints.length ?? 0 }} / 20</span>
      </div>
      <p v-if="full" class="developer-help">检查点已满，请删除不再需要的记录后保存。</p>
      <p v-if="!overview?.checkpoints.length" class="town-empty">还没有检查点。</p>
      <ul v-else class="developer-checkpoints">
        <li v-for="item in overview.checkpoints" :key="item.id">
          <div class="developer-checkpoint-info">
            <strong :title="item.name">{{ item.name }}</strong>
            <span>{{ new Date(item.created_at).toLocaleString() }}</span>
            <span v-if="!item.valid">课程已变化，检查点失效</span>
          </div>
          <div class="developer-actions">
            <button class="town-secondary" :disabled="action.busy || !item.valid" @click="previewRestore(item.id)">恢复</button>
            <button class="town-secondary" :disabled="action.busy" @click="requestDelete(item.id, item.name)">删除</button>
          </div>
        </li>
      </ul>
      <ToolFeedback v-if="action.name === 'preview' || action.name === 'delete'" :error="action.error" :message="action.message" />
    </section>
    <section v-if="confirmation" class="developer-confirm" aria-label="确认体验调整">
      <h4 class="game-card-title"><GameIcon name="save" />{{ confirmation.kind === 'restore' ? '恢复' : '删除' }}“{{ confirmation.name }}”</h4>
      <p v-if="confirmation.kind === 'restore'">当前体验状态和阅读位置将被替换，当前对白重新建立。正式提交、成绩和助教会话不会回退。</p>
      <p v-else>仅删除此检查点，不改变当前学生进度。</p>
      <div class="developer-actions">
        <button class="town-primary" :disabled="action.busy" @click="confirm">{{ action.busy ? '处理中…' : confirmation.kind === 'restore' ? '确认恢复' : '确认删除' }}</button>
        <button class="town-secondary" :disabled="action.busy" @click="confirmation = undefined">取消</button>
      </div>
      <ToolFeedback v-if="action.name === 'restore'" :error="action.error" />
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import GameIcon from "../../shared/GameIcon.vue";
import { captureExperience } from "../../shared/devTools";
import { randomUUID } from "../../shared/uuid";
import type { Workspace } from "../../shared/types";
import ToolFeedback from "./ToolFeedback.vue";
import { useDeveloperTools, useToolAction } from "./useDeveloperTools";
import type { CheckpointInput } from "./types";

type Confirmation = { kind: "delete"; id: string; name: string }
  | { kind: "restore"; id: string; name: string; expected: string };
const { overview, session, load, activeTab } = useDeveloperTools();
const action = reactive(useToolAction());
const name = ref("");
const confirmation = ref<Confirmation>();
const full = computed(() => (overview.value?.checkpoints.length ?? 0) >= 20);
let pendingSave: CheckpointInput | undefined;

function savedWorkspace(owner: number, classroomId: number): Workspace {
  let workspace: Workspace = session.previousWorkspace ? { ...session.previousWorkspace } : null;
  try {
    workspace = JSON.parse(sessionStorage.getItem(`read:${owner}:${classroomId}:workspace`) ?? "null") as Workspace ?? workspace;
  } catch { /* A malformed local navigation hint is optional. */ }
  if (workspace && "page_no" in workspace) delete workspace.page_no;
  if (workspace && "video_time" in workspace) delete workspace.video_time;
  if (workspace && "question_no" in workspace) delete workspace.question_no;
  if (workspace && "story_session_id" in workspace) delete workspace.story_session_id;
  return workspace;
}

function save() {
  if (!name.value.trim() || full.value) return;
  return action.run("save", async ({ client, current, owner, classroomId }) => {
    await session.prepareExperience();
    if (!current()) return;
    // Reuse both payload and key after an uncertain request outcome.
    pendingSave ??= {
      request_id: randomUUID(),
      name: name.value.trim(),
      client_state: captureExperience(owner, classroomId, savedWorkspace(owner, classroomId)),
    };
    await client.saveCheckpoint(pendingSave);
    if (!current()) return;
    pendingSave = undefined;
    name.value = "";
    await load();
    if (current()) action.message = "检查点已保存。";
  });
}

function previewRestore(id: string) {
  confirmation.value = undefined;
  return action.run("preview", async ({ client, current }) => {
    await session.prepareExperience();
    if (!current()) return;
    const result = await client.previewCheckpoint(id);
    if (current() && activeTab.value === "checkpoints") confirmation.value = { kind: "restore", id, ...result };
  });
}

function requestDelete(id: string, checkpointName: string) {
  action.clear();
  confirmation.value = { kind: "delete", id, name: checkpointName };
}

function confirm() {
  const item = confirmation.value;
  if (!item) return;
  return action.run(item.kind, async ({ client, current }) => {
    if (item.kind === "delete") {
      await client.deleteCheckpoint(item.id);
      if (!current()) return;
      confirmation.value = undefined;
      await load();
      if (current()) action.message = "检查点已删除。";
    } else {
      const completed = await session.restartCurrentClassroom(client.restoreMutation(item.id, item.expected));
      if (current() && !completed) throw new Error("检查点恢复未完成，请重试。");
    }
  });
}

watch(name, () => { pendingSave = undefined; });
watch(activeTab, () => { confirmation.value = undefined; });
</script>
