<script setup lang="ts">
/** 5 个底部快捷槽的弹窗宿主：对话/背包/地图/技能面板。 */
import { computed, onBeforeUnmount, onMounted } from 'vue';
import { storeToRefs } from 'pinia';
import { useHudStore } from '@stores/hud';
import { useInventoryStore } from '@stores/inventory';
import { useDialogStore } from '@stores/dialog';
import { useLearningStore } from '@stores/learning';
import { useWorldStore } from '@stores/world';
import InventoryPanel from './InventoryPanel.vue';

const hud = useHudStore();
const inventory = useInventoryStore();
const dialog = useDialogStore();
const learning = useLearningStore();
const world = useWorldStore();

const { activeSlot } = storeToRefs(hud);
const showOverlay = computed<boolean>(() =>
  activeSlot.value === 'dialog'
  || activeSlot.value === 'inventory'
  || activeSlot.value === 'map'
  || activeSlot.value === 'skill',
);

function close(): void {
  hud.closeAll();
}

function onKey(event: KeyboardEvent): void {
  if (event.key === 'Escape' && showOverlay.value) {
    close();
    event.preventDefault();
  }
}

onMounted(() => {
  if (typeof window !== 'undefined') {
    window.addEventListener('keydown', onKey);
  }
});

onBeforeUnmount(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('keydown', onKey);
  }
});

// dialog.history 实际是当前会话的 nodeId 字符串数组（dialog tree 遍历路径）。
// 这里把它和当前对话上下文合成「最近 N 节」对话记录给玩家看。
const dialogContext = computed(() => {
  const tree = dialog.tree;
  if (!tree) {
    return {
      npcName: dialog.npcName,
      npcKey: dialog.npcKey,
      nodeCount: dialog.history.length,
      currentText: dialog.currentNode?.text ?? dialog.lines.join('\n'),
    };
  }
  return {
    npcName: dialog.npcName,
    npcKey: dialog.npcKey,
    nodeCount: dialog.history.length,
    currentText: dialog.currentNode?.text ?? '',
  };
});
const MAP_LIST = [
  { key: 'town_hub',    label: 'Pythontopia 小镇', icon: 'H' },
  { key: 'farm_north',  label: '北农场 · 棉麻试验田', icon: 'N' },
  { key: 'mine_south',  label: 'Bug 矿洞 · 织丝走廊', icon: 'S' },
  { key: 'campus_east', label: '东校园 · 镜月湖畔', icon: 'E' },
  { key: 'port_west',   label: '西港 · 棉布商埠', icon: 'W' },
];
const currentMapKey = computed<string>(() => world.player?.mapKey ?? 'town_hub');

function travelTo(mapKey: string): void {
  if (currentMapKey.value === mapKey) {
    close();
    return;
  }
  world.setPendingTravel(mapKey, { x: 25, y: 20 });
  close();
}

const profileDimensions = computed(() => {
  const p = learning.profile;
  if (!p) return null;
  return {
    major: p.major ?? '—',
    baseLevel: p.baseLevel?.pythonSelfRatedLevel ?? '—',
    goal: p.learningGoal?.kind ?? '—',
    sequential: p.cognitiveStyle?.sequentialVsHolistic ?? '—',
    media: (p.learningPreference?.mediaPriority ?? []).join(' · ') || '—',
    weakKcs: p.errorPatterns?.frequentKcCodes?.length ?? 0,
    success: p.practiceCapability?.successRate ?? '—',
    mood: p.emotionPersistence?.recentMood ?? '—',
  };
});
</script>

<template>
  <transition name="qslot-fade">
    <div
      v-if="showOverlay"
      class="qslot-overlay"
      data-testid="qslot-overlay"
      @click.self="close"
    >
      <!-- ─────── J · 对话历史 ─────── -->
      <section
        v-if="activeSlot === 'dialog'"
        class="qslot-card"
        data-testid="qslot-dialog"
      >
        <header class="qslot-head">
          <h2>对话历史</h2>
          <button
            type="button"
            class="qslot-close"
            @click="close"
          >
            ✕ 关闭 (ESC)
          </button>
        </header>
        <div
          v-if="!dialogContext.npcKey"
          class="qslot-empty"
        >
          暂无激活的对话。
          走到 NPC 旁边按 F 与他们交谈，本面板会显示当前会话节点链路 + 待选择分支。
        </div>
        <div
          v-else
          class="qslot-dialog-current"
        >
          <p
            class="qslot-dialog-npc-line"
          >
            正与 <b>{{ dialogContext.npcName || dialogContext.npcKey }}</b> 对话（已对话节点数 {{ dialogContext.nodeCount }}）
          </p>
          <p
            class="qslot-dialog-text"
          >
            {{ dialogContext.currentText || '（节点无文字内容）' }}
          </p>
          <p
            class="qslot-hint"
          >
            提示：按 Enter / E 推进打字机或选择分支；按 ESC 关闭。
          </p>
        </div>
      </section>

      <!-- ─────── I · 背包 ─────── -->
      <section
        v-else-if="activeSlot === 'inventory'"
        class="qslot-card qslot-card--wide"
        data-testid="qslot-inventory"
      >
        <header class="qslot-head">
          <h2>背包 · {{ inventory.items.length }} 件</h2>
          <button
            type="button"
            class="qslot-close"
            @click="close"
          >
            ✕ 关闭 (ESC)
          </button>
        </header>
        <InventoryPanel />
      </section>

      <!-- ─────── M · 大地图 ─────── -->
      <section
        v-else-if="activeSlot === 'map'"
        class="qslot-card"
        data-testid="qslot-map"
      >
        <header class="qslot-head">
          <h2>五张地图（点击传送）</h2>
          <button
            type="button"
            class="qslot-close"
            @click="close"
          >
            ✕ 关闭 (ESC)
          </button>
        </header>
        <ul class="qslot-map-list">
          <li
            v-for="m in MAP_LIST"
            :key="m.key"
            class="qslot-map-row"
            :class="{ 'qslot-map-row--current': currentMapKey === m.key }"
            @click="travelTo(m.key)"
          >
            <span class="qslot-map-icon">{{ m.icon }}</span>
            <span class="qslot-map-label">{{ m.label }}</span>
            <span class="qslot-map-state">
              {{ currentMapKey === m.key ? '当前位置' : '点击前往' }}
            </span>
          </li>
        </ul>
        <p class="qslot-hint">
          也可以按 T 打开快速旅行；走到地图边缘自动 fade 切场景。
        </p>
      </section>

      <!-- ─────── K · 技能 + 学生画像 ─────── -->
      <section
        v-else-if="activeSlot === 'skill'"
        class="qslot-card"
        data-testid="qslot-skill"
      >
        <header class="qslot-head">
          <h2>技能 · 学生画像（8 维度）</h2>
          <button
            type="button"
            class="qslot-close"
            @click="close"
          >
            ✕ 关闭 (ESC)
          </button>
        </header>
        <div
          v-if="!profileDimensions"
          class="qslot-empty"
        >
          画像尚未生成。前往「画像屋」与镜长 Py 做一次访谈即可解锁 8 维度数据。
        </div>
        <ul
          v-else
          class="qslot-skill-list"
        >
          <li><b>专业背景</b><span>{{ profileDimensions.major }}</span></li>
          <li><b>Python 自评</b><span>{{ profileDimensions.baseLevel }} / 5</span></li>
          <li><b>学习目标</b><span>{{ profileDimensions.goal }}</span></li>
          <li><b>认知风格 (序列/整体)</b><span>{{ profileDimensions.sequential }}</span></li>
          <li><b>学习偏好</b><span>{{ profileDimensions.media }}</span></li>
          <li><b>弱项 KC 数</b><span>{{ profileDimensions.weakKcs }}</span></li>
          <li><b>近况通过率</b><span>{{ profileDimensions.success }}</span></li>
          <li><b>最近情绪</b><span>{{ profileDimensions.mood }}</span></li>
        </ul>
        <p class="qslot-hint">
          这些维度会驱动闯关塔 / 无尽塔 / boss 副本的出题偏好（弱项 KC 优先 + 难度自适应）。
        </p>
      </section>
    </div>
  </transition>
</template>

<style scoped>
.qslot-overlay {
  position: fixed;
  inset: 0;
  z-index: 110;
  background: rgba(0, 0, 0, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 16px;
}
.qslot-fade-enter-active,
.qslot-fade-leave-active {
  transition: opacity 0.18s ease;
}
.qslot-fade-enter-from,
.qslot-fade-leave-to {
  opacity: 0;
}

.qslot-card {
  width: min(520px, 100%);
  max-height: 80vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background: var(--pyt-panel-bg);
  border: 3px solid var(--pyt-panel-border);
  border-radius: 2px;
  box-shadow: 4px 4px 0 var(--pyt-panel-border-dark), 0 8px 24px var(--pyt-panel-shadow);
  color: var(--pyt-text-primary);
  font-family: var(--pyt-font-pixel, 'Ark Pixel 12', monospace);
  image-rendering: pixelated;
}
.qslot-card--wide {
  width: min(720px, 100%);
}
.qslot-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  background: var(--pyt-panel-header-bg);
  border-bottom: 2px solid var(--pyt-panel-border);
}
.qslot-head h2 {
  margin: 0;
  font-size: 16px;
  color: var(--pyt-text-accent);
  text-shadow: 1px 1px 0 #2a1a05;
  letter-spacing: 1px;
}
.qslot-close {
  font-family: inherit;
  font-size: 11px;
  padding: 4px 10px;
  background: var(--pyt-btn-bg);
  border: 2px solid var(--pyt-btn-border);
  box-shadow: 2px 2px 0 var(--pyt-panel-border-dark);
  cursor: pointer;
  color: var(--pyt-text-primary);
}
.qslot-close:active {
  transform: translate(2px, 2px);
  box-shadow: 0 0 0 var(--pyt-panel-border-dark);
}

.qslot-empty {
  padding: 24px;
  text-align: center;
  color: var(--pyt-text-secondary);
  line-height: 1.6;
  font-size: 13px;
}

.qslot-skill-list,
.qslot-map-list {
  list-style: none;
  margin: 0;
  padding: 8px 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  overflow-y: auto;
  flex: 1 1 auto;
}

.qslot-dialog-current {
  padding: 14px 18px;
}
.qslot-dialog-npc-line {
  margin: 0 0 8px;
  font-size: 13px;
  color: var(--pyt-text-secondary);
}
.qslot-dialog-npc-line b {
  color: var(--pyt-text-accent);
}
.qslot-dialog-text {
  margin: 0 0 8px;
  padding: 10px 14px;
  background: rgba(0, 0, 0, 0.2);
  border: 2px solid var(--pyt-panel-border-dark);
  border-radius: 2px;
  font-size: 14px;
  line-height: 1.6;
  color: var(--pyt-text-primary);
  min-height: 60px;
}

.qslot-map-row {
  display: grid;
  grid-template-columns: 32px 1fr auto;
  gap: 10px;
  align-items: center;
  padding: 8px 12px;
  background: rgba(0, 0, 0, 0.2);
  border: 2px solid var(--pyt-panel-border-dark);
  border-radius: 2px;
  cursor: pointer;
  transition: transform 0.12s, background 0.15s;
}
.qslot-map-row:hover:not(.qslot-map-row--current) {
  background: var(--pyt-btn-hover-bg);
  transform: translateX(2px);
}
.qslot-map-row--current {
  background: rgba(107, 191, 89, 0.2);
  border-color: var(--pyt-bar-energy);
  cursor: default;
}
.qslot-map-icon {
  font-size: 22px;
  text-align: center;
}
.qslot-map-label {
  font-size: 14px;
  font-weight: 700;
  color: var(--pyt-text-accent);
}
.qslot-map-state {
  font-size: 11px;
  color: var(--pyt-text-secondary);
}

.qslot-skill-list li {
  display: grid;
  grid-template-columns: 130px 1fr;
  gap: 8px;
  padding: 5px 10px;
  background: rgba(0, 0, 0, 0.15);
  border-left: 3px solid var(--pyt-panel-border);
  font-size: 12px;
}
.qslot-skill-list li b {
  color: var(--pyt-text-accent);
}
.qslot-skill-list li span {
  color: var(--pyt-text-secondary);
}

.qslot-hint {
  margin: 8px 16px 12px;
  font-size: 11px;
  color: var(--pyt-text-secondary);
  font-style: italic;
}
</style>
