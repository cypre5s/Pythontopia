import { nextTick, onBeforeUnmount, onMounted, watch, type Ref } from "vue";

const controls =
  'button,a[href],input,textarea,select,summary,audio[controls],video[controls],[tabindex],[contenteditable="true"]';

export function focusableControls(root: HTMLElement): HTMLElement[] {
  return Array.from(root.querySelectorAll<HTMLElement>(controls)).filter(
    (el) => {
      if (
        (el.tabIndex < 0 &&
          (el.hasAttribute("tabindex") ||
            !el.matches("audio[controls],video[controls]"))) ||
        el.matches(':disabled,[type="hidden"]') ||
        el.closest('[hidden],[inert],[aria-hidden="true"]')
      )
        return false;
      let details = el.parentElement?.closest<HTMLDetailsElement>(
        "details:not([open])",
      );
      while (details) {
        const summary = Array.from(details.children).find(
          (child) => child.tagName === "SUMMARY",
        );
        if (!summary?.contains(el)) return false;
        details = details.parentElement?.closest<HTMLDetailsElement>(
          "details:not([open])",
        );
      }
      let parent: HTMLElement | null = el;
      while (parent) {
        const style = getComputedStyle(parent);
        if (style.display === "none" || style.visibility === "hidden")
          return false;
        parent = parent.parentElement;
      }
      return true;
    },
  );
}

/** Only the visible layer owns focus; closing returns to its invoker or the HUD. */
export function useModalFocus(
  panel: Ref<HTMLElement | undefined>,
  state: () => unknown,
  close: () => void,
) {
  let previous: HTMLElement | null = null;
  const focusFirst = () =>
    (focusableControls(panel.value!)[0] ?? panel.value)?.focus({
      preventScroll: true,
    });
  async function restore() {
    await nextTick();
    if (document.querySelector('[aria-modal="true"]')) return;
    const target =
      previous?.isConnected &&
      previous !== document.body &&
      !previous.closest("[inert]")
        ? previous
        : document.querySelector<HTMLElement>("[data-world-focus]");
    target?.focus({ preventScroll: true });
  }
  watch(
    state,
    async (value, old) => {
      if (value) {
        if (!old) previous = document.activeElement as HTMLElement | null;
        await nextTick();
        if (state() && panel.value) focusFirst();
      } else if (old) void restore();
    },
    { immediate: true },
  );
  function keydown(event: KeyboardEvent) {
    if (!state() || !panel.value || event.isComposing || event.defaultPrevented)
      return;
    if (event.key === "Escape") {
      event.preventDefault();
      event.stopImmediatePropagation();
      if (!event.repeat) close();
    } else if (event.key === "Tab") {
      const items = focusableControls(panel.value);
      const index = items.indexOf(document.activeElement as HTMLElement);
      if (
        !items.length ||
        index < 0 ||
        (!event.shiftKey && index === items.length - 1) ||
        (event.shiftKey && index === 0)
      ) {
        event.preventDefault();
        (event.shiftKey ? items.at(-1) : items[0])?.focus();
        if (!items.length) panel.value.focus();
      }
    }
  }
  function focusin(event: FocusEvent) {
    if (
      state() &&
      panel.value &&
      event.target instanceof Node &&
      !panel.value.contains(event.target)
    )
      focusFirst();
  }
  onMounted(() => {
    window.addEventListener("keydown", keydown, true);
    document.addEventListener("focusin", focusin);
  });
  onBeforeUnmount(() => {
    window.removeEventListener("keydown", keydown, true);
    document.removeEventListener("focusin", focusin);
    if (state()) void restore();
  });
}
