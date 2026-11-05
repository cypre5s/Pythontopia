import { describe, expect, it, vi } from "vitest";
import { fromWorldCanvas, startedOnWorldCanvas } from "../src/game/pointerBoundary";

describe("DOM controls own their touch and mouse gestures", () => {
  it("keeps overlay presses from navigating underlying buildings while real canvas presses still work", () => {
    const canvas = document.createElement("canvas");
    const interact = document.createElement("button");
    const dialogue = document.createElement("p");
    const navigate = vi.fn();
    const worldPress = fromWorldCanvas(canvas, navigate);
    for (const element of [interact, dialogue]) {
      for (const eventName of ["touchstart", "mousedown"]) {
        const event = new Event(eventName, { bubbles: true });
        element.dispatchEvent(event);
        const pointer = { downElement: event.target };
        worldPress(pointer);
        expect(startedOnWorldCanvas(pointer, canvas)).toBe(false);
      }
    }
    expect(navigate).not.toHaveBeenCalled();
    const touch = new Event("touchstart");
    canvas.dispatchEvent(touch);
    worldPress({ downElement: touch.target });
    expect(navigate).toHaveBeenCalledTimes(1);
    expect(startedOnWorldCanvas({ downElement: touch.target }, canvas)).toBe(true);
  });
});
