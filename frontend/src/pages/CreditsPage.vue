<script setup lang="ts">
import { useRouter } from 'vue-router';
const router = useRouter();
function back(): void {
  if (window.history.length > 1) {
    router.back();
  } else {
    void router.push('/town');
  }
}
</script>

<template>
  <section class="credits-page">
    <header class="hero">
      <button
        type="button"
        class="back"
        @click="back"
      >
        ← 返回
      </button>
      <h1 class="pyt-pixel-text">
        Pythontopia 致谢
      </h1>
      <p class="lead">
        本作品在游戏化学习闭环中使用了下列优秀的开源美术、音效与字体素材。
        谨在此向所有作者致谢。完整 license 请见 <code>LICENSE-ART.md</code>。
      </p>
    </header>

    <article class="credits-block">
      <h2>像素字体 (OFL-1.1)</h2>
      <ul>
        <li>
          <strong>Ark Pixel Font</strong> by TakWolf —
          <a
            href="https://github.com/TakWolf/ark-pixel-font"
            target="_blank"
            rel="noreferrer"
          >GitHub</a>
          — SIL Open Font License v1.1
        </li>
      </ul>

      <h2>图标 (CC-BY 3.0)</h2>
      <ul>
        <li>
          <strong>Game Icons</strong> by Lorc / Delapouite / Skoll / sbed / Carl Olsen —
          <a
            href="https://game-icons.net"
            target="_blank"
            rel="noreferrer"
          >game-icons.net</a> — CC BY 3.0
        </li>
      </ul>

      <h2>UI / Tiles / SFX (CC0)</h2>
      <ul>
        <li>
          <strong>Kenney Vleugels</strong> — UI Pack Adventure / Tiny Town / Tiny Dungeon /
          Roguelike Modern City / Roguelike Caves &amp; Dungeons / RPG Audio / Interface Sounds /
          Game Icons Expansion / Cursor Pack / Input Prompts —
          <a
            href="https://kenney.nl"
            target="_blank"
            rel="noreferrer"
          >kenney.nl</a> — CC0
        </li>
      </ul>

      <h2>角色 Sprite (CC-BY-SA 3.0+)</h2>
      <p class="hint">
        Liberated Pixel Cup (LPC) 系列素材以 CC-BY-SA 3.0+ 协议发布，本作品最终发布的素材层亦以兼容协议公开。
      </p>
      <ul>
        <li>
          <strong>LPC Sara / Medieval Fantasy / Peppercarrot 24x32</strong> 等 base &amp; expansion —
          作者群: bluecarrot16, Sharm, Reemax, daneeklu (Daniel Eddeland), Jetrel, J. Taylor,
          Ivan Voirol, TheraHedwig, David Revoy 等 —
          <a
            href="https://opengameart.org"
            target="_blank"
            rel="noreferrer"
          >OpenGameArt</a>
          — CC BY-SA 3.0+
        </li>
      </ul>

      <h2>BGM (CC0 / CC-BY 3.0)</h2>
      <ul>
        <li>
          <strong>RandomMind</strong> — Medieval &amp; Old Tower Inn loops — CC0
        </li>
        <li>
          <strong>MouthlessGames</strong> — Town &amp; Dungeon BGM — CC0
        </li>
        <li>
          <strong>MintoDog</strong> — Reflection / Quest jingles — CC0 / CC BY 3.0
        </li>
      </ul>

      <h2>特效 (CC0 / CC-BY 4.0)</h2>
      <ul>
        <li>codemanu — Pixel FX (CC0)</li>
        <li>jellyfizh — Confetti / Poof (CC0)</li>
        <li>john2k — Heal Effect (CC0)</li>
      </ul>

      <h2>OpenGameArt 农作物 / 季节场景 (混合协议)</h2>
      <ul>
        <li>
          <strong>Jana Ochse (2DPIXX)</strong> — farming-game-basics — CC BY 4.0
        </li>
        <li>
          <strong>marionline</strong> — Top-Down Season Environment Tileset — CC0
        </li>
        <li>
          <strong>josehzz</strong> — farming-crops-16x16 — CC0
        </li>
        <li>
          <strong>Marco L.</strong> — pixel-lightning — CC BY 4.0
        </li>
      </ul>
    </article>

    <footer class="meta">
      <p>
        本声明页面是软件运行时合规的一部分。代码层使用 MIT License；素材层使用上述各协议。
        构建版本 R-100 / 阶段 1 集成 (2026-05-22)。
      </p>
    </footer>
  </section>
</template>

<style scoped>
.credits-page {
  max-width: 880px;
  margin: 32px auto;
  padding: 24px 32px;
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-border-strong);
  border-radius: var(--pyt-radius-lg);
  color: var(--pyt-color-text-primary);
  box-shadow: var(--pyt-shadow-card);
}
.hero {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 24px;
}
.back {
  align-self: flex-start;
  background: transparent;
  border: 2px solid var(--pyt-color-border);
  padding: 6px 12px;
  border-radius: var(--pyt-radius-sm);
  font-family: var(--pyt-font-sans);
  cursor: pointer;
  color: var(--pyt-color-text-primary);
}
.back:hover {
  background: var(--pyt-color-bg-soft);
}
h1 {
  font-size: 24px;
  margin: 0;
}
.lead {
  color: var(--pyt-color-text-muted);
  font-size: 14px;
  line-height: 1.6;
}
.hint {
  color: var(--pyt-color-text-hint);
  font-size: 13px;
}
.credits-block h2 {
  margin-top: 24px;
  margin-bottom: 8px;
  font-size: 16px;
  color: var(--pyt-color-primary-dark);
  border-bottom: 1px solid var(--pyt-color-border-soft);
  padding-bottom: 4px;
}
ul {
  padding-left: 18px;
  line-height: 1.6;
  font-size: 14px;
}
li {
  margin-bottom: 4px;
}
a {
  color: var(--pyt-color-info);
}
.meta {
  margin-top: 32px;
  padding-top: 16px;
  border-top: 1px solid var(--pyt-color-border-soft);
  color: var(--pyt-color-text-hint);
  font-size: 12px;
}
code {
  font-family: var(--pyt-font-mono);
  background: var(--pyt-color-bg-soft);
  padding: 1px 4px;
  border-radius: 3px;
}
</style>
