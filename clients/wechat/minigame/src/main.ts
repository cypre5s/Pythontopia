// 小游戏入口逻辑（被根目录 game.js -> js/main.js 引导）。
import { Game } from './core/Game';
import { logError } from './runtime/wx';

const game = new Game();
game.start().catch((err: unknown) => {
  logError('[pythontopia] 启动失败', err);
});
