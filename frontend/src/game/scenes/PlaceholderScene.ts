export function createPlaceholderScene(key: string, title: string) {
  return {
    key,
    create(this: { add: { text(x: number, y: number, text: string, style?: Record<string, string>): { setOrigin(x: number, y?: number): void } } }) {
      this.add
        .text(480, 300, `${title}\n敬请期待`, {
          color: '#fff8e1',
          fontFamily: 'monospace',
          fontSize: '24px',
          align: 'center',
        })
        .setOrigin(0.5);
    },
  };
}
