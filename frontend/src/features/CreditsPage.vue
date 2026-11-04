<template>
  <main class="credits-page">
    <RouterLink to="/world"><GameIcon name="arrowLeft"/> 返回小镇</RouterLink>
    <p class="eyebrow">素材与课程来源</p>
    <h1>一座由许多人共同点亮的小镇。</h1>
    <p>
      Pythontopia
      代码许可与美术许可分层保留。商业游戏仅提供体验参考，未使用其官方素材。
    </p>
    <h2>使用中的素材</h2>
    <p>
      当前小镇地图、角色与道具沿用原仓库的 Super Retro World
      素材；历史记录中的作者商用条款和书面许可证明尚需补全，不能据此宣称全套素材属于
      CC 许可。背景音乐、字体及立绘分别适用来源记录中的许可。请在发布前核对
      LICENSE-ART.md 与 assets/pixel/LICENSE.md 的完整署名和限制，代码层遵循
      LICENSE。
    </p>
    <h2>课程内容</h2>
    <p>
      Python语言基础，Alethicode 课程包
      43。课件提取文本保留原文档及页码关联。现有仓库未附原始 PDF/PPT
      与课程视频文件；文本阅读会明确标识，上传的版本另行保存。
    </p>
    <a
      href="https://github.com/cypre5s/Pythontopia/blob/reborn/LICENSE-ART.md"
      target="_blank"
      rel="noopener"
      >完整美术授权记录 <GameIcon name="external"/></a
    >
  </main>
</template>
<script setup lang="ts">
import GameIcon from "../shared/GameIcon.vue";
import { RouterLink } from "vue-router";
</script>
