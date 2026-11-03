import { describe, it, expect } from "vitest";
import { findPath, isTextFocus } from "../src/game/pathfinding";
describe("world movement boundary", () => {
  it("walks around a collision barrier and cannot reach sealed destinations", () => {
    const path = findPath(
      { x: 0, y: 0 },
      { x: 3, y: 0 },
      4,
      3,
      (x, y) => x === 1 && y < 2,
    );
    expect(path).toContainEqual({ x: 1, y: 2 });
    expect(path.at(-1)).toEqual({ x: 3, y: 0 });
    expect(
      findPath({ x: 0, y: 0 }, { x: 3, y: 0 }, 4, 3, (x) => x === 1),
    ).toEqual([]);
  });
  it("prevents world shortcuts while editing code or inputs", () => {
    document.body.innerHTML =
      '<div class="cm-editor"><div contenteditable="true" id="code"></div></div><input id="input"><button id="button"></button>';
    expect(isTextFocus(document.querySelector("#code"))).toBe(true);
    expect(isTextFocus(document.querySelector("#input"))).toBe(true);
    expect(isTextFocus(document.querySelector("#button"))).toBe(false);
  });
});
