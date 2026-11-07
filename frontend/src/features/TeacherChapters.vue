<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { RefreshCw } from "@lucide/vue";
import { Button } from "../components/ui/button";
import { api, notify } from "../shared/api";
import { TIME_ZONE, formatTimestamp, localDateTimeToIso } from "../shared/time";
import type { Chapter } from "../shared/types";

interface Release {
  chapter_id: number;
  status: "published" | "paused";
  opens_at: string | null;
  prerequisite_chapter_id: number | null;
}
const props = defineProps<{
  classroomId: number | null;
  classroomName: string;
  chapters: Chapter[];
}>();
const releases = ref<Release[]>([]),
  dates = ref<Record<number, string>>({});
const loading = ref(false),
  saving = ref<number | null>(null),
  error = ref("");
const now = ref(Date.now());
const orderedChapters = computed(() =>
  [...props.chapters].sort((a, b) => a.order_index - b.order_index),
);
let generation = 0;
const timer = window.setInterval(() => {
  now.value = Date.now();
}, 30_000);
function releaseFor(id: number) {
  return releases.value.find((r) => r.chapter_id === id);
}
function localInput(iso: string | null | undefined) {
  if (!iso) return "";
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat("en-CA", {
      timeZone: TIME_ZONE,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      hourCycle: "h23",
    })
      .formatToParts(new Date(iso))
      .map((part) => [part.type, part.value]),
  );
  return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}`;
}
function status(id: number) {
  const item = releaseFor(id);
  if (!item) return { text: "未安排", kind: "" };
  if (item.status === "paused") return { text: "已暂停", kind: "paused" };
  if (item.opens_at && Date.parse(item.opens_at) > now.value)
    return { text: "待解锁", kind: "queued" };
  return { text: "已解锁", kind: "ready" };
}
function scheduleText(id: number) {
  const item = releaseFor(id);
  return !item
    ? "尚未设置解锁时间"
    : item.opens_at
      ? formatTimestamp(item.opens_at)
      : "已设为立即解锁";
}
async function load() {
  const classroom = props.classroomId,
    request = ++generation;
  error.value = "";
  if (!classroom) {
    releases.value = [];
    dates.value = {};
    loading.value = false;
    return;
  }
  loading.value = true;
  try {
    const rules = await api.get<Release[]>(`/teacher/classrooms/${classroom}/releases`);
    if (request !== generation) return;
    releases.value = rules;
    dates.value = Object.fromEntries(
      rules.map((rule) => [rule.chapter_id, localInput(rule.opens_at)]),
    );
  } catch (e) {
    if (request === generation)
      error.value = e instanceof Error ? e.message : "章节安排读取失败";
  } finally {
    if (request === generation) loading.value = false;
  }
}
async function save(chapterId: number, action: "schedule" | "now" | "pause") {
  const classroom = props.classroomId,
    request = generation;
  if (!classroom || saving.value !== null || loading.value) return;
  const existing = releaseFor(chapterId);
  error.value = "";
  saving.value = chapterId;
  try {
    if (action === "schedule" && !dates.value[chapterId])
      throw new Error("请先选择该章节的解锁日期和时间。");
    const opensAt =
      action === "now"
        ? null
        : action === "pause"
          ? (existing?.opens_at ?? null)
          : localDateTimeToIso(dates.value[chapterId]!);
    const rule = await api.put<Release>(
      `/teacher/classrooms/${classroom}/releases/${chapterId}`,
      {
        status: action === "pause" ? "paused" : "published",
        opens_at: opensAt,
        prerequisite_chapter_id: existing?.prerequisite_chapter_id ?? null,
      },
    );
    if (request !== generation || classroom !== props.classroomId) return;
    releases.value = [
      ...releases.value.filter((r) => r.chapter_id !== chapterId),
      rule,
    ];
    dates.value[chapterId] = localInput(rule.opens_at);
    now.value = Date.now();
    notify(action === "pause" ? "章节已暂停" : "章节安排已保存");
  } catch (e) {
    if (request === generation)
      error.value = e instanceof Error ? e.message : "章节安排保存失败";
  } finally {
    saving.value = null;
  }
}
watch(
  () => props.classroomId,
  () => {
    releases.value = [];
    dates.value = {};
    void load();
  },
  { immediate: true },
);
onBeforeUnmount(() => {
  generation++;
  window.clearInterval(timer);
});
</script>

<template>
  <section
    class="chapter-management"
    :aria-label="`${classroomName || '当前班级'}章节安排`"
  >
    <header class="table-toolbar list-toolbar">
      <span class="field-hint">时区：{{ TIME_ZONE }}</span>
      <Button
        variant="outline"
        :disabled="!classroomId || loading || saving !== null"
        @click="load"
        ><RefreshCw :size="15" />刷新</Button
      >
    </header>
    <p v-if="error" class="error schedule-error" role="alert">{{ error }}</p>
    <p v-if="!classroomId" class="admin-empty">请先创建或选择一个班级。</p>
    <p v-else-if="loading" class="admin-empty" role="status">
      正在读取章节安排…
    </p>
    <template v-else>
      <div class="admin-panel">
        <div class="admin-table-scroll">
          <table class="chapter-schedule-table">
            <thead>
              <tr>
                <th>章节</th>
                <th>解锁日期与时间</th>
                <th>当前状态</th>
                <th class="table-actions-heading">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="chapter in orderedChapters" :key="chapter.id">
                <td>
                  <span class="record-title">{{ chapter.title }}</span
                  ><small v-if="releaseFor(chapter.id)?.prerequisite_chapter_id"
                    >还需完成「{{
                      chapters.find(
                        (c) =>
                          c.id ===
                          releaseFor(chapter.id)?.prerequisite_chapter_id,
                      )?.title || "前置章节"
                    }}」</small
                  >
                </td>
                <td>
                  <input
                    v-model="dates[chapter.id]"
                    type="datetime-local"
                    :aria-label="`${chapter.title} 解锁时间`"
                    :disabled="!classroomId || saving !== null"
                  /><small>{{ scheduleText(chapter.id) }}</small>
                </td>
                <td>
                  <span
                    class="status-badge"
                    :data-status="status(chapter.id).kind"
                    >{{ status(chapter.id).text }}</span
                  >
                </td>
                <td>
                  <div class="row-actions">
                    <Button
                      size="sm"
                      :disabled="
                        !classroomId || saving !== null || !dates[chapter.id]
                      "
                      @click="save(chapter.id, 'schedule')"
                      >{{ saving === chapter.id ? "保存中…" : "保存" }}</Button
                    ><Button
                      variant="outline"
                      size="sm"
                      :disabled="
                        !classroomId ||
                        saving !== null ||
                        (status(chapter.id).kind === 'ready' &&
                          !releaseFor(chapter.id)?.opens_at)
                      "
                      @click="save(chapter.id, 'now')"
                      >立即解锁</Button
                    ><Button
                      v-if="releaseFor(chapter.id)?.status === 'published'"
                      variant="ghost"
                      size="sm"
                      :disabled="saving !== null"
                      @click="save(chapter.id, 'pause')"
                      >暂停</Button
                    >
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
      <p v-if="!orderedChapters.length" class="admin-empty">
        暂无章节，请先到“课程与知识点”添加章节。
      </p>
    </template>
  </section>
</template>
