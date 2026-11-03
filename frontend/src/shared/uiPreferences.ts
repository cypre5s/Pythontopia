import { defineStore } from "pinia";
import { computed, ref, watch } from "vue";

const key = "pythontopia-reborn-interface";
export const textScales = [1, 1.25, 1.5, 2] as const;
export const useUiPreferences = defineStore("ui-preferences", () => {
  let saved: { textScale?: number; reduceMotion?: boolean } = {};
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
      [textScale, reduceMotion, reducedMotion],
      () => {
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
              reduceMotion: reduceMotion.value,
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
  return { textScale, reduceMotion, reducedMotion, connect };
});
