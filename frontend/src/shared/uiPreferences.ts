import { defineStore } from "pinia";
import { computed, ref, watch } from "vue";

const key = "pythontopia-reborn-interface";
export const textScales = [1, 1.25, 1.5, 2] as const;
export const subtitleSizes = [0.875, 1, 1.125, 1.25, 1.5] as const;
export const useUiPreferences = defineStore("ui-preferences", () => {
  let saved: { textScale?: number; subtitleSize?: number; reduceMotion?: boolean; musicVolume?: number } = {};
  try {
    saved = JSON.parse(localStorage.getItem(key) ?? "{}");
  } catch {
    /* Optional device preference. */
  }
  const textScale = ref(
    textScales.some((scale) => scale === saved?.textScale)
      ? saved.textScale!
      : 1,
  );
  const reduceMotion = ref(saved?.reduceMotion === true);
  const subtitleSize = ref(subtitleSizes.some(size => size === saved?.subtitleSize) ? saved.subtitleSize! : 1);
  const boundedVolume = (value: unknown) =>
    typeof value === "number" && Number.isFinite(value)
      ? Math.max(0, Math.min(1, value))
      : 0.15;
  const musicVolume = ref(boundedVolume(saved?.musicVolume));
  const systemMotion = ref(false);
  const reducedMotion = computed(
    () => reduceMotion.value || systemMotion.value,
  );
  function connect() {
    const media = window.matchMedia("(prefers-reduced-motion: reduce)");
    systemMotion.value = media.matches;
    const update = () => {
      systemMotion.value = media.matches;
    };
    media.addEventListener("change", update);
    const stop = watch(
      [textScale, subtitleSize, reduceMotion, reducedMotion, musicVolume],
      () => {
        musicVolume.value = boundedVolume(musicVolume.value);
        document.documentElement.style.setProperty(
          "--text-scale",
          String(textScale.value),
        );
        document.documentElement.dataset.reducedMotion = String(
          reducedMotion.value,
        );
        try {
          localStorage.setItem(
            key,
            JSON.stringify({
              textScale: textScale.value,
              subtitleSize: subtitleSize.value,
              reduceMotion: reduceMotion.value,
              musicVolume: musicVolume.value,
            }),
          );
        } catch {
          /* Browsing still works when storage is unavailable. */
        }
      },
      { immediate: true },
    );
    return () => {
      stop();
      media.removeEventListener("change", update);
    };
  }
  return { textScale, subtitleSize, reduceMotion, reducedMotion, musicVolume, connect };
});
