export const BootScene = {
  key: 'BootScene',
  preload(this: { load?: { on(event: string, callback: (value: number) => void): void; image(key: string, url: string): void } }) {
    this.load?.on('progress', (value: number) => {
      window.dispatchEvent(new CustomEvent('pythontopia:load-progress', { detail: { progress: value } }));
    });
    this.load?.image('tileset_farm', '/assets/pixel/vendor/opengameart/tileset_farm.png');
    this.load?.image('vegetables', '/assets/pixel/vendor/opengameart/vegetables.png');
    this.load?.image('cereals', '/assets/pixel/vendor/opengameart/cereals.png');
    this.load?.image('crates_and_sacks', '/assets/pixel/vendor/opengameart/crates_and_sacks.png');
    this.load?.image('student_default', '/assets/pixel/characters/student_default.svg');
  },
  create(this: { scene: { start(key: string): void } }) {
    window.dispatchEvent(new CustomEvent('pythontopia:load-progress', { detail: { progress: 1 } }));
    this.scene.start('TownScene');
  },
};
