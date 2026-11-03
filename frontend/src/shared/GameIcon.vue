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
    | "journal"
    | "book"
    | "people"
    | "settings"
    | "compass"
    | "close"
    | "leaf"
    | "chevron";
}>();
const paths = {
  journal:
    "M6 3h14v18H6a3 3 0 0 1-3-3V6a3 3 0 0 1 3-3ZM6 3v18M10 8h6M10 12h6M10 16h3",
  book: "M12 5v16M12 5C9 2 4 3 2 4v15c3-1 7-1 10 2 3-3 7-3 10-2V4c-2-1-7-2-10 1Z",
  people:
    "M8 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8ZM2 21v-2a6 6 0 0 1 12 0v2M16 4a4 4 0 0 1 0 8M18 16a5 5 0 0 1 4 5",
  settings:
    "m9 3-1 3-3 1-2 4 2 3v4l4 3 3-1 3 1 4-3v-4l2-3-2-4-3-1-1-3ZM12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8Z",
  compass: "M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0ZM16 8l-3 5-5 3 3-5 5-3Z",
  close: "m6 6 12 12M6 18 18 6",
  leaf: "M19 3C10 2 3 6 5 13c2 6 11 4 14-10ZM4 21c1-6 5-10 11-14",
  chevron: "m9 5 7 7-7 7",
};
</script>
