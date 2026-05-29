declare module 'reveal.js' {
  interface RevealOptions {
    embedded?: boolean;
    hash?: boolean;
    controls?: boolean;
    progress?: boolean;
    keyboard?: boolean;
    slideNumber?: boolean | string;
    overview?: boolean;
    width?: number;
    height?: number;
    transition?: 'none' | 'fade' | 'slide' | 'convex' | 'concave' | 'zoom';
    center?: boolean;
    margin?: number;
    minScale?: number;
    maxScale?: number;
  }

  class Reveal {
    constructor(element: HTMLElement, options?: RevealOptions);
    initialize(options?: RevealOptions): Promise<void>;
    destroy(): void;
    slide(indexh: number, indexv?: number, indexf?: number): void;
    left(): void;
    right(): void;
    up(): void;
    down(): void;
    prev(): void;
    next(): void;
    getIndices(): { h: number; v: number; f: number };
    getTotalSlides(): number;
    isOverview(): boolean;
    toggleOverview(override?: boolean): void;
    on(event: string, callback: (...args: unknown[]) => void): void;
    off(event: string, callback: (...args: unknown[]) => void): void;
  }

  export default Reveal;
}

declare module 'reveal.js/dist/reveal.css' {
  const content: string;
  export default content;
}
