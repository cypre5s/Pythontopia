<template>
  <div class="license-content">
    <section>
      <component :is="headingTag">素材与许可</component>
      <p>Pythontopia 的代码与美术分别保留许可。代码许可见 LICENSE；商业游戏仅作为体验参考，未使用其官方素材。</p>
      <p>小镇地图、角色与道具沿用原仓库的 Super Retro World 素材。历史记录中的作者商用条款和书面许可证明尚需补全，不能据此宣称整套素材属于 CC 许可。背景音乐、字体和立绘分别遵循来源记录中的许可。</p>
      <p>完整署名与使用限制见 LICENSE-ART.md 和 assets/pixel/LICENSE.md。</p>
      <a href="https://github.com/cypre5s/Pythontopia/blob/reborn/LICENSE-ART.md" target="_blank" rel="noopener noreferrer">
        查看完整美术授权记录 <GameIcon name="external" />
      </a>
    </section>
    <section>
      <component :is="headingTag">课程来源</component>
      <p>Python语言基础，Alethicode 课程包 43。课件与原文档、页面编号保持关联；配套 PDF 和视频通过私有存储提供，原始 PPT/PPTX 未提供。</p>
    </section>
  </div>
</template>
<script setup lang="ts">
import GameIcon from "../shared/GameIcon.vue";
withDefaults(defineProps<{ headingTag?: "h2" | "h4" }>(), { headingTag: "h4" });
</script>
<style scoped>
.license-content { color:#6e5d49; line-height:1.7; }
.license-content section + section { margin-top:16px; padding-top:12px; border-top:1px solid #d6c6aa; }
.license-content h2,.license-content h4 { color:#3d5145; font-family:inherit; font-size:.875rem; margin:0 0 8px; }
.license-content p { margin:.65rem 0; font-size:.9375rem; }
.license-content a { display:inline-flex; align-items:center; gap:.55rem; min-height:44px; color:#305d4b; text-underline-offset:4px; }
.license-content a svg { width:18px; height:18px; }
</style>
