<template>
  <div
    ref="panel"
    class="dialogue-layer"
    role="dialog"
    aria-modal="true"
    aria-label="剧情对白"
    tabindex="-1"
    @keydown="navigateResponses"
  >
    <div class="dialogue-stage" aria-hidden="true">
    <img
      v-if="portrait"
      class="portrait"
      :src="portrait"
      alt=""
      @error="imageError = true"
    />
    </div>
    <section class="dialogue-box" tabindex="0" aria-label="对白内容，点击或按回车继续" @click="clickDialogue" @keydown.enter.self.prevent="continueDialogue" @keydown.space.self.prevent="continueDialogue">
      <div class="dialogue-heading">
        <p class="eyebrow">{{ speaker }}</p>
      </div>
      <p class="dialogue-text" :aria-label="line?.text">
        {{ displayed }}
      </p>
      <p v-if="error" class="error">{{ error }}</p>
      <p v-if="session.storyBusy" class="dialogue-saving" role="status">
        正在记录这次对话…
      </p>
      <div v-if="!typing" class="dialogue-actions" :aria-busy="session.storyBusy">
        <button
          v-for="(choice, index) in typing ? [] : (line?.choices ?? [])"
          :key="choice.id"
          class="dialogue-response"
            :disabled="session.storyBusy"
          @click="advance(choice.id)"
        >
          <span class="response-number" aria-hidden="true">{{ index + 1 }}.</span>
          <span>{{ choice.label }}</span></button
        >
      </div>
      <footer class="dialogue-footer">
        <span>{{ typing ? "点击显示全文" : line?.choices?.length ? "↑ ↓ 选择 · Enter 确认" : "点击继续 · Enter" }}</span>
        <button v-if="story" class="text-button" :disabled="session.storyBusy" @click="endInteraction">结束本次互动</button>
        <button class="text-button" @click="close">暂时离开 <kbd>Esc</kbd></button>
      </footer>
    </section>
  </div>
</template>
<script setup lang="ts">
import { computed, ref, watch, onBeforeUnmount } from "vue";
import type { StoryView } from "../shared/types";
import { useCharacterImage } from "../shared/characterImage";
import { useSession } from "../shared/session";
import { useModalFocus } from "../shared/modalFocus";
import { useUiPreferences } from "../shared/uiPreferences";
import { displayWorldText } from "../game/config/worldConfig";
const props = defineProps<{ story?: StoryView }>();
const emit = defineEmits<{ close: [] }>();
const session = useSession(), displayed = ref(""), typing = ref(false), imageError = ref(false), error = ref("");
const panel = ref<HTMLElement>();
const preferences = useUiPreferences();
useModalFocus(panel, () => props.story?.id ?? "interaction", close);
const line = computed(() => {
    const current = props.story?.node;
    return current ? { ...current, text: displayWorldText(current.text) } : undefined;
  }),
  speaker = computed(() => line.value?.speaker ?? "小镇互动");
const portrait = useCharacterImage(computed(() => imageError.value ? undefined : line.value?.portrait));
let timer: ReturnType<typeof setInterval> | undefined;
function close() { emit("close"); }
async function endInteraction() {
  try { if (await session.endStory()) close(); }
  catch (failure) { error.value = failure instanceof Error ? failure.message : "未能结束互动，请重试。"; }
}
function continueDialogue() {
  if (session.storyBusy) return;
  if (typing.value) reveal();
  else if (!line.value?.choices?.length) void advance();
}
function clickDialogue(event: MouseEvent) {
  if ((event.target as HTMLElement).closest("button")) return;
  continueDialogue();
}
function reveal() {
  clearInterval(timer);
  displayed.value = line.value?.text ?? "";
  typing.value = false;
}
function type() {
  clearInterval(timer);
  imageError.value = false;
  displayed.value = "";
  typing.value = true;
  let i = 0;
  const text = line.value?.text ?? "";
  if (preferences.reducedMotion) {
    reveal();
    return;
  }
  timer = setInterval(() => {
    displayed.value = text.slice(0, ++i);
    if (i >= text.length) reveal();
  }, 20);
}
function navigateResponses(event: KeyboardEvent) {
  if (!['ArrowUp', 'ArrowDown'].includes(event.key) || event.altKey || event.ctrlKey || event.metaKey) return;
  const buttons = Array.from(panel.value?.querySelectorAll<HTMLButtonElement>('.dialogue-actions button:not(:disabled)') ?? []);
  if (!buttons.length) return;
  event.preventDefault();
  event.stopPropagation();
  const index = buttons.indexOf(document.activeElement as HTMLButtonElement);
  const next = index < 0 ? 0 : (index + (event.key === 'ArrowDown' ? 1 : -1) + buttons.length) % buttons.length;
  buttons[next]?.focus();
}
async function advance(choice?: string) {
  if (session.storyBusy) return;
  if (typing.value) { reveal(); return; }
  error.value = "";
  try { await session.advanceStory(choice); }
  catch (failure) { error.value = failure instanceof Error ? failure.message : "对白推进失败，请重试。"; }
}
// Start after the complete prop update, including a new round on the same node.
watch(
  [() => props.story?.id, () => props.story?.revision,
    () => line.value?.id, () => line.value?.text],
  type,
  { immediate: true },
);
onBeforeUnmount(() => {
  clearInterval(timer);
});
</script>

<style scoped>
.dialogue-layer { position:fixed;inset:0;transform:none;width:100%;display:flex;align-items:flex-end;justify-content:center;padding:var(--safe-top) max(24px,var(--safe-right)) max(26px,var(--safe-bottom)) max(24px,var(--safe-left));background:linear-gradient(0deg,#17221b66,transparent 75%);pointer-events:auto; }
.dialogue-stage { position:absolute;left:max(24px,calc(50% - 640px));bottom:0;width:min(30vw,440px);height:min(76dvh,780px);z-index:2;overflow:hidden;pointer-events:none; }
.dialogue-layer .portrait { display:block;position:absolute;left:50%;transform:translateX(-50%);width:auto;height:130%;max-width:none;max-height:none;object-fit:contain;object-position:left top;filter:drop-shadow(4px 8px 10px #10171b88); }
.dialogue-box { position:relative;flex:none;width:100%;max-width:1280px;max-height:63dvh;min-height:220px;overflow:auto;padding:16px 20px 14px max(min(30vw,440px),220px);border:1px solid #af8b50;border-radius:8px;background:radial-gradient(ellipse at 20% 0,#45392eeb,#242020f5 80%);box-shadow:0 8px 24px #10191840;color:#f0dfbb; }
.dialogue-box:focus { outline:none; }
.dialogue-box:focus-visible { outline:none;border-color:#e7c884; }
.dialogue-heading { display:flex;align-items:center;justify-content:space-between;margin-bottom:14px; }
.dialogue-box .eyebrow { margin:0;display:inline-block;background:none;border:0;border-radius:0;padding:0;color:#f0ce8d;font-size:.9375rem;letter-spacing:0;box-shadow:none; }
.dialogue-box .eyebrow span { color:#c3ad87;font-size:.75rem; }


.dialogue-text { color:#f0dfbb;font-size:.9375rem;line-height:1.8;min-height:3.6em;margin:0 0 18px;cursor:pointer;white-space:pre-wrap; }
.dialogue-actions { display:flex;flex-direction:column;gap:4px;padding:0;margin:0;border:0;animation:responses-in .24s ease-out; }
.dialogue-actions:empty { display:none; }
.dialogue-actions button { display:flex;align-items:center;gap:14px;width:100%;min-height:34px;text-align:left;padding:6px 10px;border:1px solid transparent;border-radius:3px;background:transparent;box-shadow:none;color:#e6d4b0;font-size:.875rem;line-height:1.5; }
.dialogue-actions button:hover,.dialogue-actions button:focus-visible { background:linear-gradient(90deg,#b185424d,#a9843910);border-color:#c89c52;color:#ffe1a1;box-shadow:none; }
.response-number { color:#dcb979;font-family:inherit;font-size:.875rem;min-width:1.3em; }
.dialogue-footer { display:flex;align-items:center;justify-content:space-between;gap:14px;margin-top:12px;padding-top:10px;border-top:1px solid #a98c5733;color:#bba580;font-size:.78rem; }
.dialogue-footer button { color:#e2c99d;min-height:34px;background:transparent;border:0; }
.dialogue-footer button:hover { background:#b58c4326;color:#ffe1a1; }
.dialogue-box button:disabled { opacity:.5;cursor:wait; }
.dialogue-box .error { color:#ffb39e; }.dialogue-saving { color:#cdb986; }
@keyframes responses-in { from {opacity:0;transform:translateY(6px)} to {opacity:1;transform:none} }
@media(max-width:700px) { .dialogue-layer { padding:12px 12px 18px; }.dialogue-stage { left:0;width:44%;height:44dvh;bottom:42dvh; }.dialogue-layer .portrait { height:120%; }.dialogue-box { padding:18px;max-height:60dvh;min-height:220px;z-index:3; }.dialogue-box .eyebrow { padding:0;font-size:.875rem; }.dialogue-text { font-size:1rem; }.dialogue-actions button { font-size:.95rem; }.dialogue-box .eyebrow span { display:none; } }
@media(max-height:540px) and (min-width:701px) { .dialogue-stage { height:80dvh;width:25%; }.dialogue-box { padding:16px 22px 12px 28%;max-height:80dvh;min-height:180px; }.dialogue-text { font-size:1rem;min-height:0;margin-bottom:10px; }.dialogue-heading { margin-bottom:8px; }.dialogue-box .eyebrow { font-size:.9375rem;padding:0; } }
@media(prefers-reduced-motion:reduce) { .dialogue-actions { animation:none; } }
:global([data-reduced-motion="true"]) .dialogue-actions { animation:none; }
@media(pointer:coarse) { .dialogue-actions button,.dialogue-footer button { min-height:44px; } }
</style>
