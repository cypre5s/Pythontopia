<script setup lang="ts">
/**
 * PyAccordion：像素皮手风琴（reka-ui Accordion 底座）。items 驱动；每项内容默认渲染
 * item.content 文本，也可用具名插槽 `#<item.value>="{ item }"` 自定义。
 * 默认单开（type=single, collapsible）。
 */
import {
  AccordionContent,
  AccordionHeader,
  AccordionItem,
  AccordionRoot,
  AccordionTrigger,
} from 'reka-ui';

interface AccItem {
  value: string;
  title: string;
  content?: string;
}

withDefaults(
  defineProps<{
    items: AccItem[];
    type?: 'single' | 'multiple';
  }>(),
  {
    type: 'single',
  },
);
</script>

<template>
  <AccordionRoot
    class="py-accordion"
    :type="type"
    :collapsible="type === 'single' ? true : undefined"
  >
    <AccordionItem
      v-for="item in items"
      :key="item.value"
      class="py-accordion__item"
      :value="item.value"
    >
      <AccordionHeader class="py-accordion__header">
        <AccordionTrigger class="py-accordion__trigger">
          <span class="py-accordion__title">{{ item.title }}</span>
          <span
            class="py-accordion__chevron"
            aria-hidden="true"
          >▾</span>
        </AccordionTrigger>
      </AccordionHeader>
      <AccordionContent class="py-accordion__content">
        <slot
          :name="item.value"
          :item="item"
        >
          <div class="py-accordion__body">
            {{ item.content }}
          </div>
        </slot>
      </AccordionContent>
    </AccordionItem>
  </AccordionRoot>
</template>

<style scoped>
.py-accordion {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.py-accordion__item {
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  overflow: hidden;
  background: var(--pyt-color-bg-elevated);
}
.py-accordion__header {
  margin: 0;
}
.py-accordion__trigger {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 12px;
  background: var(--pyt-color-bg-soft);
  border: none;
  cursor: pointer;
  font-family: var(--pyt-font-pixel);
  font-size: var(--pyt-font-size-base);
  font-weight: 600;
  color: var(--pyt-color-text-primary);
  text-align: left;
}
.py-accordion__trigger:hover {
  background: var(--pyt-color-warning-bg);
}
.py-accordion__chevron {
  flex: none;
  transition: transform var(--pyt-motion-base);
  color: var(--pyt-color-text-hint);
}
.py-accordion__trigger[data-state='open'] .py-accordion__chevron {
  transform: rotate(180deg);
}
.py-accordion__content {
  overflow: hidden;
}
.py-accordion__body {
  padding: 10px 12px;
  font-family: var(--pyt-font-read);
  font-size: var(--pyt-font-size-base);
  color: var(--pyt-color-text-primary);
  line-height: 1.6;
  white-space: pre-wrap;
}
</style>
