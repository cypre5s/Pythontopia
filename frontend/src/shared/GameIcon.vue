<template>
  <svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.7"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    class="game-icon"
  >
    <path :d="paths[name]" />
  </svg>
</template>
<script setup lang="ts">
defineProps<{
  name:
    | "message"
    | "journal"
    | "book"
    | "people"
    | "settings"
    | "compass"
    | "building"
    | "close"
    | "leaf"
    | "music"
    | "video"
    | "practice"
    | "chevron" | "grid" | "folder" | "activity" | "chart" | "publish" | "logout" | "plus" | "copy" | "refresh" | "arrowLeft" | "arrowRight" | "arrowUp" | "arrowDown" | "external";
}>();
const paths = {message: "M4 4h16v12H9l-5 4V4ZM8 8h8M8 12h5", grid: "M3 3h7v7H3ZM14 3h7v7h-7ZM3 14h7v7H3ZM14 14h7v7h-7Z",
music: "M9 18V5l11-2v13M9 8l11-2M9 18a3 3 0 1 1-3-3c1.7 0 3 1.3 3 3ZM20 16a3 3 0 1 1-3-3c1.7 0 3 1.3 3 3Z",
video: "M3 4h18v16H3ZM10 8l6 4-6 4Z",
practice: "M8 3H3v18h18v-5M10 14l1-4 9-9 3 3-9 9-4 1ZM17 4l3 3",
folder: "M3 7V4h6l3 3h9v13H3Z",
activity: "M3 12h4l3-8 4 16 3-8h4",
chart: "M4 3v18h17M8 17v-5M13 17V7M18 17v-8",
publish: "M12 16V3m-5 5 5-5 5 5M4 15v6h16v-6",
logout: "M9 4H3v16h6M9 12h12m-5-5 5 5-5 5",
plus: "M12 5v14M5 12h14",
copy: "M9 9h12v12H9ZM5 15H3V3h12v2",
refresh: "M20 8a9 9 0 1 0 1 7M20 3v5h-5",
arrowLeft: "M20 12H4m6-6-6 6 6 6",
arrowRight: "M4 12h16m-6-6 6 6-6 6",
arrowUp: "M12 20V4m-6 6 6-6 6 6",
arrowDown: "M12 4v16m-6-6 6 6 6-6",
external: "M14 3h7v7m0-7L10 14M10 3H3v18h18v-7",
  journal:
    "M6 3h14v18H6a3 3 0 0 1-3-3V6a3 3 0 0 1 3-3ZM6 3v18M10 8h6M10 12h6M10 16h3",
  book: "M12 5v16M12 5C9 2 4 3 2 4v15c3-1 7-1 10 2 3-3 7-3 10-2V4c-2-1-7-2-10 1Z",
  people:
    "M8 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8ZM2 21v-2a6 6 0 0 1 12 0v2M16 4a4 4 0 0 1 0 8M18 16a5 5 0 0 1 4 5",
  settings:
    "m9 3-1 3-3 1-2 4 2 3v4l4 3 3-1 3 1 4-3v-4l2-3-2-4-3-1-1-3ZM12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8Z",
  compass: "M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0ZM16 8l-3 5-5 3 3-5 5-3Z",
  building: "M3 21h18M5 21V9l7-6 7 6v12M9 21v-6h6v6M9 10h1M14 10h1",
  close: "m6 6 12 12M6 18 18 6",
  leaf: "M19 3C10 2 3 6 5 13c2 6 11 4 14-10ZM4 21c1-6 5-10 11-14",
  chevron: "m9 5 7 7-7 7",
};
</script>
