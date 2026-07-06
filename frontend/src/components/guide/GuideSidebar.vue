<script setup lang="ts">
/**
 * 指南左侧章节导航：分组手风琴——同一时刻只展开一个分组（一级），其余折叠。
 * 默认展开「当前主题所属分组」，切换到其它分组的主题时自动跟随展开；当前主题下再展开页内小节。
 */
import { computed, ref, watch } from 'vue';
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

/** 当前主题（二级）所属的分组（一级）；找不到时回退首个分组。 */
const activeGroupId = computed<string>(() => {
  const owner = props.groups.find((group) => group.sectionIds.includes(props.activeId));
  return owner?.id ?? props.groups[0]?.id ?? '';
});

/** 单开手风琴当前展开的分组：默认当前主题所属分组，并随主题跨组切换自动跟随。 */
const expandedGroupId = ref<string>(activeGroupId.value);
watch(activeGroupId, (groupId) => {
  expandedGroupId.value = groupId;
});

/** 点分组头：展开它并收起其它分组；再点已展开的分组则收起。 */
function toggleGroup(groupId: string): void {
  expandedGroupId.value = expandedGroupId.value === groupId ? '' : groupId;
}

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
        :class="{ 'guide-sidebar__group--open': group.id === expandedGroupId }"
        :data-testid="`guide-nav-group-${group.id}`"
      >
        <button
          type="button"
          class="guide-sidebar__group-head"
          :aria-expanded="group.id === expandedGroupId"
          :aria-controls="`guide-nav-group-list-${group.id}`"
          :data-testid="`guide-nav-group-toggle-${group.id}`"
          @click="toggleGroup(group.id)"
        >
          <span class="guide-sidebar__group-headings">
            <span class="guide-sidebar__group-label pyt-pixel-text">{{ group.label }}</span>
            <span class="guide-sidebar__group-desc">{{ group.description }}</span>
          </span>
          <span
            class="guide-sidebar__group-caret"
            aria-hidden="true"
          />
        </button>
        <ol
          v-show="group.id === expandedGroupId"
          :id="`guide-nav-group-list-${group.id}`"
          class="guide-sidebar__list"
        >
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
  align-items: center;
  justify-content: space-between;
  gap: var(--pyt-space-2);
  width: 100%;
  padding: 4px 10px 5px;
  font: inherit;
  text-align: left;
  color: inherit;
  background: none;
  border: 0;
  border-radius: var(--pyt-radius-sm);
  cursor: pointer;
  transition: background var(--pyt-motion-base) ease;
}

.guide-sidebar__group-head:hover,
.guide-sidebar__group-head:focus-visible {
  background: var(--pyt-color-bg-soft);
}

.guide-sidebar__group-headings {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.guide-sidebar__group-caret {
  flex-shrink: 0;
  width: 0;
  height: 0;
  border-left: 5px solid transparent;
  border-right: 5px solid transparent;
  border-top: 6px solid var(--pyt-color-text-muted);
  transition: transform var(--pyt-motion-base) ease;
}

.guide-sidebar__group--open .guide-sidebar__group-caret {
  transform: rotate(180deg);
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

/* 二级目录直接嵌在一级目录（当前主题）下，所有宽度常显——不再依赖独立的右侧/顶部大纲块。 */
.guide-sidebar__sublist {
  display: flex;
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

@media (prefers-reduced-motion: reduce) {
  .guide-sidebar__group-head,
  .guide-sidebar__group-caret {
    transition: none;
  }
}

</style>
