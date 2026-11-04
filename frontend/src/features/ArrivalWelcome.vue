<template>
  <Teleport to="body">
  <div class="arrival-welcome" role="dialog" aria-modal="true" aria-label="欢迎回到小镇" tabindex="-1" ref="panel" @click="proceed">

    <footer v-if="ready" class="welcome-footer">
    <strong class="arrival-brand">PYTHONTOPIA</strong>
    <section class="welcome-panel">
      <h1>欢迎回到小镇，{{ name }}。</h1>
      <button @click.stop="proceed">按任意键继续</button>
    </section>
    </footer>

  </div>
  </Teleport>
</template>
<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue';
const props = defineProps<{ready: boolean; name: string}>();
const emit = defineEmits<{continue: []}>();
const panel = ref<HTMLElement>();
function proceed() { if(props.ready) emit('continue'); }
function keydown(event: KeyboardEvent) {
  if (event.isComposing || event.repeat || event.ctrlKey || event.altKey || event.metaKey || ['Tab','Shift','Control','Alt','Meta'].includes(event.key)) return;
  event.preventDefault(); event.stopImmediatePropagation(); proceed();
}
onMounted(()=>{ panel.value?.focus(); window.addEventListener('keydown',keydown,true); });
onBeforeUnmount(()=>window.removeEventListener('keydown',keydown,true));
</script>
<style scoped>
.arrival-welcome { position: fixed; inset: 0; z-index: 60; outline: none; box-shadow: none; }
.welcome-footer {
  position: absolute;
  inset: auto 0 0;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding: 64px max(6vw, var(--safe-right)) max(24px, var(--safe-bottom)) max(6vw, var(--safe-left));
  background: linear-gradient(0deg, #102820b8, #10282045 55%, transparent);
  color: #fff3d5;
  animation: welcome-rise .5s both;
}
.welcome-footer .arrival-brand {
  position: static;
  flex-shrink: 0;
  margin: 0 0 12px;
  font-size: clamp(.9rem, 1.6vw, 1.3rem);
  letter-spacing: .08em;
  text-shadow: none;
}
.welcome-panel { min-width: 0; max-width: 60%; text-align: right; }
.welcome-panel h1 { font-size: clamp(1rem, 1.5vw, 1.2rem); line-height: 1.5; margin: 0; overflow-wrap: anywhere; }
.welcome-panel button { min-height: 44px; padding: 6px 0; color: #eed39b; background: none; border: 0; border-radius: 0; text-decoration: underline; text-underline-offset: 4px; font-size: 1rem; }
@keyframes welcome-rise { from {opacity: 0; transform: translateY(8px)} to {opacity: 1; transform: translateY(0)} }
@media(max-width: 560px) {
  .welcome-footer { gap: 12px; padding-inline: var(--safe-left) var(--safe-right); }
  .welcome-footer .arrival-brand { font-size: .75rem; letter-spacing: .04em; }
  .welcome-panel { max-width: 62%; }
  .welcome-panel h1, .welcome-panel button { font-size: .9rem; }
}
@media(prefers-reduced-motion: reduce) { .welcome-footer { animation: none; } }
:global([data-reduced-motion="true"]) .welcome-footer { animation: none; }
</style>
