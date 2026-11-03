<template><RouterView /><ToastLayer /></template>
<script setup lang="ts">
import { RouterView } from "vue-router";
import ToastLayer from "../shared/ToastLayer.vue";
import { onBeforeUnmount } from "vue";
import { useUiPreferences } from "../shared/uiPreferences";
const disconnect = useUiPreferences().connect();
onBeforeUnmount(disconnect);
</script>
