<template>
  <main class="credits-page">
    <RouterLink to="/world"><GameIcon name="arrowLeft" /> 返回小镇</RouterLink>
    <h1>关于 Pythontopia</h1>
    <LicenseContent heading-tag="h2" />
  </main>
</template>
<script setup lang="ts">
import GameIcon from "../shared/GameIcon.vue";
import { RouterLink } from "vue-router";
import LicenseContent from "./LicenseContent.vue";
</script>
