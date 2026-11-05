<script setup lang="ts">
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from "../ui/dialog";
withDefaults(
  defineProps<{
    open: boolean;
    title: string;
    description: string;
    wide?: boolean;
  }>(),
  { wide: false },
);
defineEmits<{ "update:open": [value: boolean] }>();
</script>

<template>
  <Dialog :open="open" @update:open="$emit('update:open', $event)">
    <DialogContent :class="['teacher-dialog', wide ? 'admin-dialog-wide' : '']">
      <div class="dialog-heading">
        <DialogTitle>{{ title }}</DialogTitle>
        <DialogDescription>{{ description }}</DialogDescription>
      </div>
      <slot />
    </DialogContent>
  </Dialog>
</template>
