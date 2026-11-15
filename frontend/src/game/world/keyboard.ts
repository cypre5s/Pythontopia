import { isTextFocus } from "../pathfinding";
import type { WorldState } from "./types";

export function bindWorldKeyboard(
  root: HTMLElement,
  state: WorldState,
  interact: () => void,
) {
  const keyDown = (e: KeyboardEvent) => {
    if (
      state.paused ||
      e.isComposing ||
      e.ctrlKey ||
      e.metaKey ||
      e.altKey ||
      isTextFocus(document.activeElement)
    )
      return;
    // Movement returns focus to the world after closing a keyboard-accessible menu.
    if (
      [
        "w",
        "a",
        "s",
        "d",
        "arrowup",
        "arrowdown",
        "arrowleft",
        "arrowright",
      ].includes(e.key.toLowerCase()) &&
      document.activeElement?.closest(".world-ui")
    ) {
      root.focus({ preventScroll: true });
    }
    if (
      ["ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight", " "].includes(e.key)
    )
      e.preventDefault();
    state.pressed.add(e.key.toLowerCase());
    if (e.key.toLowerCase() === "e" && !e.repeat) interact();
  };
  const keyUp = (e: KeyboardEvent) => state.pressed.delete(e.key.toLowerCase());
  const blur = () => state.pressed.clear();
  window.addEventListener("keydown", keyDown);
  window.addEventListener("keyup", keyUp);
  window.addEventListener("blur", blur);

  return () => {
    state.pressed.clear();
    window.removeEventListener("keydown", keyDown);
    window.removeEventListener("keyup", keyUp);
    window.removeEventListener("blur", blur);
  };
}
