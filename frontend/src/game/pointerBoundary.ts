/** Phaser also sees window touches. Only a press that starts on the canvas owns a world action. */
export function fromWorldCanvas(canvas: HTMLCanvasElement, action: () => void) {
  return (pointer: { downElement?: unknown }) => {
    if (pointer.downElement === canvas) action();
  };
}

export function startedOnWorldCanvas(pointer: { downElement?: unknown }, canvas: HTMLCanvasElement) {
  return pointer.downElement === canvas;
}
