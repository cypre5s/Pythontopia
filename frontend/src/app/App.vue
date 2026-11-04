<template><TownBackdrop v-if="showTown" /><RouterView /><ToastLayer /></template>
<script setup lang="ts">
import "./arrival.css";
import { RouterView, useRoute } from "vue-router";
import ToastLayer from "../shared/ToastLayer.vue";
import TownBackdrop from "../features/TownBackdrop.vue";
import { arrivalActive } from "../shared/arrival";
import { useSession } from "../shared/session";
import { computed, onBeforeUnmount } from "vue";
import { useUiPreferences } from "../shared/uiPreferences";
const route = useRoute();
const session = useSession();
const showTown = computed(() => route.path === "/" || arrivalActive.value || (route.path === "/world" && !session.world));
const disconnect = useUiPreferences().connect();
onBeforeUnmount(disconnect);
</script>
