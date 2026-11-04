<template>
  <section ref="root" class="workspace-padding library-workspace">
    <p class="eyebrow">图书馆 · 已开放资料</p>
    <h2>留一页，慢慢读。</h2>
    <p class="muted library-heading-note">
      浏览当前班级已开放的材料。阅读时会保留所在页，可以随时返回继续寻找。
    </p>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="loading" class="notice" role="status">正在整理已开放的书目…</p>
    <div class="library-list">
      <button
        v-for="material in materials"
        :key="`${material.id}-${material.version}`"
        :disabled="material.status !== 'ready'"
        @click="open(material)"
      >
        <span class="book-spine" aria-hidden="true"><GameIcon name="book"/></span
        ><span
          ><strong>{{ material.title }}</strong
          ><small
            >{{ kindLabels[material.kind] ?? "课程资料" }} · 版本
            {{ material.version }}</small
          ></span
        ><span>{{
          material.status === "ready"
            ? "阅读"
            : material.status === "failed"
              ? "处理失败"
              : "处理中"
        }}</span>
      </button>
    </div>
    <p v-if="!loading && !materials.length" class="notice">
      教师尚未为这个班级开放材料。
    </p>
  </section>
</template>
<script setup lang="ts">
import GameIcon from "../shared/GameIcon.vue";
import { onMounted, onBeforeUnmount, nextTick, ref } from "vue";
import { api } from "../shared/api";
import { useSession } from "../shared/session";
import type { Material } from "../shared/types";
import "./learningUi.css";
const session = useSession(),
  root = ref<HTMLElement>(),
  materials = ref<Material[]>([]),
  error = ref(""),
  loading = ref(true),
  scrollKey = `library-scroll:${session.user?.id}:${session.classroomId}`;
const kindLabels: Record<string, string> = {
  text: "文本资料",
  pdf: "PDF 课件",
  presentation: "转换后的课件",
  video: "课程视频",
};
function open(material: Material) {
  session.navigate({
    kind: "material",
    material_id: material.id,
    version: material.version,
  });
}
onMounted(async () => {
  try {
    materials.value = await api.get<Material[]>(
      `/library?classroom_id=${session.classroomId}`,
    );
  } catch (e) {
    error.value = e instanceof Error ? e.message : "图书馆读取失败";
  } finally {
    loading.value = false;
  }
  await nextTick();
  if (root.value)
    root.value.scrollTop = Number(sessionStorage.getItem(scrollKey) ?? 0);
});
onBeforeUnmount(() => {
  if (root.value)
    sessionStorage.setItem(scrollKey, String(root.value.scrollTop));
});
</script>
