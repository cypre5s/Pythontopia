# Frontend

Vue 3 + Vite + TypeScript strict + Pinia + Vitest + Phaser 3 网页端，对应 `AGENTS.md` 的前端约束。Phaser 与第三方动态对象只能经 `src/game/adapters/` 进入业务代码。

```bash
npm ci
npm run typecheck
npm run lint
npm run test
```
