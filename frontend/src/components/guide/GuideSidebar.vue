<script setup lang="ts">
/** 指南左侧章节导航：分组展示全站主题，并在当前主题下展开页内小节。 */
import { computed } from 'vue';
import type {
  GuideNavGroup,
  GuideSectionContent,
} from '@/content/guideSections';

const props = defineProps<{
  sections: readonly GuideSectionContent[];
  groups: readonly GuideNavGroup[];
  activeId: string;
}>();

const emit = defineEmits<{
  (e: 'select', sectionId: string): void;
}>();

const sectionById = computed(() => new Map(props.sections.map((section) => [section.id, section])));
const groupedSections = computed(() => props.groups.map((group) => ({
  ...group,
  sections: group.sectionIds
    .map((sectionId) => sectionById.value.get(sectionId))
    .filter((section): section is GuideSectionContent => section !== undefined),
})));

function sectionNumber(sectionId: string): string {
  return String(props.sections.findIndex((section) => section.id === sectionId) + 1).padStart(2, '0');
}
</script>

<template>
  <nav
    id="guide-nav"
    class="guide-sidebar"
    aria-label="章节导航"
    data-testid="guide-sidebar"
  >
    <p class="guide-sidebar__label pyt-pixel-text">
      指南目录
    </p>
    <ol class="guide-sidebar__groups">
      <li
        v-for="group in groupedSections"
        :key="group.id"
        class="guide-sidebar__group"
        :data-testid="`guide-nav-group-${group.id}`"
      >
        <div class="guide-sidebar__group-head">
          <span class="guide-sidebar__group-label pyt-pixel-text">{{ group.label }}</span>
          <span class="guide-sidebar__group-desc">{{ group.description }}</span>
        </div>
        <ol class="guide-sidebar__list">
          <li
            v-for="section in group.sections"
            :key="section.id"
          >
            <a
              class="guide-sidebar__link"
              :class="{ 'guide-sidebar__link--active': section.id === activeId }"
              :href="`#${section.id}`"
              :aria-current="section.id === activeId ? 'location' : undefined"
              :data-testid="`guide-nav-link-${section.id}`"
              @click.prevent="emit('select', section.id)"
            >
              <span
                class="guide-sidebar__num pyt-pixel-text"
                aria-hidden="true"
              >{{ sectionNumber(section.id) }}</span>
              <span class="guide-sidebar__text">{{ section.navTitle }}</span>
            </a>
            <ol
              v-if="section.id === activeId"
              class="guide-sidebar__sublist"
            >
              <li
                v-for="subsection in section.subsections"
                :key="subsection.id"
              >
                <a
                  class="guide-sidebar__sublink"
                  :href="`#${subsection.id}`"
                  :data-testid="`guide-nav-sub-${subsection.id}`"
                  @click.prevent="emit('select', subsection.id)"
                >
                  {{ subsection.title }}
                </a>
              </li>
            </ol>
          </li>
        </ol>
      </li>
    </ol>
  </nav>
</template>

<style scoped>
.guide-sidebar {
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-2);
}

.guide-sidebar__label {
  margin: 0;
  font-size: 12px;
  letter-spacing: 2px;
  color: var(--pyt-color-text-muted);
}

.guide-sidebar__groups,
.guide-sidebar__list,
.guide-sidebar__sublist {
  list-style: none;
  margin: 0;
  padding: 0;
}

.guide-sidebar__groups {
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-3);
}

.guide-sidebar__group {
  padding-bottom: var(--pyt-space-2);
  border-bottom: 1px solid var(--pyt-color-border-soft);
}

.guide-sidebar__group:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}

.guide-sidebar__group-head {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 0 10px 5px;
}

.guide-sidebar__group-label {
  font-size: 11px;
  letter-spacing: 1.5px;
  color: var(--pyt-color-warning);
}

.guide-sidebar__group-desc {
  font-size: 12px;
  line-height: 1.45;
  color: var(--pyt-color-text-muted);
}

.guide-sidebar__list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.guide-sidebar__link {
  display: flex;
  align-items: center;
  gap: var(--pyt-space-2);
  padding: 7px 10px;
  font-size: 15px;
  line-height: 1.4;
  color: var(--pyt-color-text-hint);
  text-decoration: none;
  border-left: 3px solid transparent;
  border-radius: var(--pyt-radius-sm);
  cursor: pointer;
  transition:
    background var(--pyt-motion-base) ease,
    color var(--pyt-motion-base) ease,
    border-color var(--pyt-motion-base) ease;
}

.guide-sidebar__link:hover,
.guide-sidebar__link:focus-visible {
  background: var(--pyt-color-bg-soft);
  color: var(--pyt-color-text-primary);
}

.guide-sidebar__link--active {
  border-left-color: var(--pyt-color-gold);
  background: var(--pyt-color-bg-soft);
  color: var(--pyt-color-primary-dark);
  font-weight: 700;
}

.guide-sidebar__num {
  flex-shrink: 0;
  font-size: 11px;
  color: var(--pyt-color-warning);
}

.guide-sidebar__text {
  flex: 1;
}

.guide-sidebar__sublist {
  display: none;
  flex-direction: column;
  gap: 1px;
  margin: 2px 0 5px 32px;
  padding-left: 10px;
  border-left: 1px solid var(--pyt-color-border-soft);
}

.guide-sidebar__sublink {
  display: block;
  padding: 3px 4px;
  font-size: 12px;
  line-height: 1.45;
  color: var(--pyt-color-text-muted);
  text-decoration: none;
}

.guide-sidebar__sublink:hover,
.guide-sidebar__sublink:focus-visible {
  color: var(--pyt-color-primary-dark);
  background: rgba(241, 196, 83, 0.14);
}

@media (max-width: 1023px) {
  .guide-sidebar__sublist {
    display: flex;
  }
}
</style>
