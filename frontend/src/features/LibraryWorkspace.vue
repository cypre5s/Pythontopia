<template>
  <section class="workspace-padding library-workspace">
    <p class="eyebrow">图书馆 · 已开放资料</p>
    <h2>留一页，慢慢读。</h2>
    <p class="muted">
      这里展示你当前班级已开放的材料；课程工作区使用同一套阅读器。
    </p>
    <p v-if="error" class="error">{{ error }}</p>
    <div class="library-list">
      <button
        v-for="material in materials"
        :key="`${material.id}-${material.version}`"
        @click="open(material)"
      >
        <span class="book-spine">▤</span
        ><span
          ><strong>{{ material.title }}</strong
          ><small
            >{{ material.kind }} · 版本 {{ material.version }}</small
          ></span
        ><span>阅读 ↗</span>
      </button>
    </div>
    <p v-if="!materials.length" class="notice">教师尚未为这个班级开放材料。</p>
  </section>
</template>
<script setup lang="ts">
import { onMounted, ref } from "vue";
import { api } from "../shared/api";
import { useSession } from "../shared/session";
import type { Material } from "../shared/types";
const session = useSession(),
  materials = ref<Material[]>([]),
  error = ref("");
function open(material: Material) {
  session.open({
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
  }
});
</script>
