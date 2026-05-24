<script setup lang="ts">
import { nextTick, ref, watch } from 'vue';
import gsap from 'gsap';

const props = defineProps<{
  stage: number;
  trigger: number;
}>();

const effectRef = ref<HTMLDivElement | null>(null);
const particles = ref<{ id: number; x: number; y: number; color: string }[]>([]);
let animId = 0;

const STAGE_EFFECTS: Record<number, { name: string; color: string; glow: string }> = {
  1: { name: 'wave', color: '#f1c453', glow: 'rgba(241,196,83,0.6)' },
  2: { name: 'fire', color: '#ef4444', glow: 'rgba(239,68,68,0.6)' },
  3: { name: 'frost', color: '#60a5fa', glow: 'rgba(96,165,250,0.6)' },
  4: { name: 'lightning', color: '#a855f7', glow: 'rgba(168,85,247,0.6)' },
};

function spawnParticles(count: number, color: string): void {
  const next: typeof particles.value = [];
  for (let i = 0; i < count; i++) {
    next.push({
      id: animId++,
      x: 30 + Math.random() * 40,
      y: 20 + Math.random() * 60,
      color,
    });
  }
  particles.value = next;
}

function playWave(): void {
  const el = effectRef.value;
  if (!el) return;
  spawnParticles(8, '#f1c453');
  gsap.fromTo(el, { scale: 0.8, opacity: 0 }, { scale: 1, opacity: 1, duration: 0.3, ease: 'back.out(2)' });
  const ring = el.querySelector('.effect-ring');
  if (ring) {
    gsap.fromTo(ring, { scale: 0, opacity: 1 }, { scale: 3, opacity: 0, duration: 1, ease: 'power2.out' });
  }
  el.querySelectorAll('.effect-particle').forEach((p, i) => {
    gsap.fromTo(p,
      { scale: 0, opacity: 1 },
      { scale: 1.5, opacity: 0, x: (Math.random() - 0.5) * 80, y: (Math.random() - 0.5) * 80, duration: 0.8 + i * 0.1, ease: 'power2.out' },
    );
  });
}

function playFire(): void {
  const el = effectRef.value;
  if (!el) return;
  spawnParticles(12, '#ef4444');
  gsap.fromTo(el, { opacity: 0 }, { opacity: 1, duration: 0.15 });
  const ring = el.querySelector('.effect-ring');
  if (ring) {
    gsap.fromTo(ring,
      { scale: 0, opacity: 1, rotation: 0 },
      { scale: 2.5, opacity: 0, rotation: 180, duration: 0.9, ease: 'power3.out' },
    );
  }
  el.querySelectorAll('.effect-particle').forEach((p, i) => {
    gsap.fromTo(p,
      { scale: 0.5, opacity: 1, y: 0 },
      { scale: 0, opacity: 0, y: -(40 + Math.random() * 60), x: (Math.random() - 0.5) * 40, duration: 0.6 + i * 0.08, ease: 'power1.out' },
    );
  });
}

function playFrost(): void {
  const el = effectRef.value;
  if (!el) return;
  spawnParticles(6, '#60a5fa');
  gsap.fromTo(el,
    { scale: 1.3, opacity: 0 },
    { scale: 1, opacity: 1, duration: 0.4, ease: 'power2.in' },
  );
  const ring = el.querySelector('.effect-ring');
  if (ring) {
    gsap.fromTo(ring,
      { scale: 2.5, opacity: 0 },
      { scale: 0.5, opacity: 0.6, duration: 0.7, ease: 'power3.in' },
    );
    gsap.to(ring, { opacity: 0, duration: 0.3, delay: 0.7 });
  }
  el.querySelectorAll('.effect-particle').forEach((p) => {
    gsap.fromTo(p,
      { scale: 1, opacity: 0.8, rotation: 0 },
      { scale: 0, opacity: 0, rotation: 360, duration: 1.2, ease: 'power2.inOut' },
    );
  });
}

function playLightning(): void {
  const el = effectRef.value;
  if (!el) return;
  spawnParticles(10, '#a855f7');
  gsap.fromTo(el,
    { opacity: 0 },
    { opacity: 1, duration: 0.05 },
  );
  gsap.to(el, { opacity: 0, duration: 0.05, delay: 0.1 });
  gsap.to(el, { opacity: 1, duration: 0.05, delay: 0.2 });
  gsap.to(el, { opacity: 0.3, duration: 0.4, delay: 0.4 });
  gsap.to(el, { opacity: 0, duration: 0.3, delay: 0.8 });
  el.querySelectorAll('.effect-particle').forEach((p, i) => {
    const delay = i * 0.03;
    gsap.fromTo(p,
      { scale: 2, opacity: 1 },
      { scale: 0, opacity: 0, duration: 0.4, delay, ease: 'power4.out' },
    );
  });
}

watch(
  () => props.trigger,
  async () => {
    await nextTick();
    switch (props.stage) {
      case 1: playWave(); break;
      case 2: playFire(); break;
      case 3: playFrost(); break;
      case 4: playLightning(); break;
      default: playWave();
    }
  },
);

const effect = STAGE_EFFECTS[props.stage] ?? STAGE_EFFECTS[1]!;
</script>

<template>
  <div
    ref="effectRef"
    class="boss-move-effect"
    data-testid="boss-move-effect"
  >
    <div
      class="effect-ring"
      :style="{ borderColor: effect.color, boxShadow: `0 0 20px ${effect.glow}` }"
    />
    <div
      v-for="p in particles"
      :key="p.id"
      class="effect-particle"
      :style="{ left: `${p.x}%`, top: `${p.y}%`, background: p.color }"
    />
  </div>
</template>

<style scoped>
.boss-move-effect {
  position: absolute;
  inset: -20px;
  pointer-events: none;
  z-index: 1;
  overflow: hidden;
}
.effect-ring {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 60px;
  height: 60px;
  margin: -30px 0 0 -30px;
  border: 3px solid currentColor;
  border-radius: 50%;
  opacity: 0;
}
.effect-particle {
  position: absolute;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  opacity: 0;
}
</style>
