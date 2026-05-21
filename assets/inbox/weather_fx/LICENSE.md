# Weather FX 素材包 License 清单

存放目录: `assets/inbox/weather_fx/`
采集时间: 2026-05-21
采集渠道: Kenney.nl、OpenGameArt.org（经 HTTP 代理 `127.0.0.1:7892`）

本目录用于 Pythontopia 小镇的天气与粒子特效，要求覆盖 **雨滴 / 雪花 / 太阳 / 云 / 雷电** 五种元素，全部使用 CC0 或 CC-BY 系列许可。下面逐包列出来源、作者、许可证与归属要求。

> 通用约定：
> - CC0 1.0 — 公共领域奉献，无需署名（推荐做法仍保留来源说明）。
> - CC-BY 4.0 — 必须署名作者与协议，需提供原始链接，可商用、可衍生。
> - 任何二次合并、改色、切片产生的派生素材，必须在最终游戏 `assets/CREDITS.md` 中保留同一署名链路。

---

## 1. Kenney Particle Pack

- 目录: `kenney_particle_pack/`
- 来源: <https://kenney.nl/assets/particle-pack>
- 直接下载: <https://kenney.nl/media/pages/assets/particle-pack/f8fe0f8cb8-1677578741/kenney_particle-pack.zip>
- 作者: Kenney Vleugels (Kenney.nl)
- 许可证: **CC0 1.0**（见 `kenney_particle_pack/License.txt`）
- 内容: 80 张通用粒子（火花 spark、闪光 flare、光晕 light、烟雾 smoke、火焰 flame/fire、星 star、痕迹 trace、魔法 magic 等），提供 PNG 透明与黑底两套，分辨率多为 256×256。
- Pythontopia 用途: 作为雷电闪光、阳光光晕、雨水溅射、烟雾、魔法收获等粒子的通用基底。
- 署名要求: CC0 无需署名，建议在致谢页保留 `Kenney.nl`。

## 2. Pixel Art Clouds (DustDFG)

- 目录: `pixel_art_clouds/pixel_art_clouds/`
- 来源: <https://opengameart.org/content/pixel-art-clouds>
- itch 镜像: <https://dustdfg.itch.io/pixel-art-clouds>
- 直接下载: <https://opengameart.org/sites/default/files/pixel_art_clouds.zip>
- 作者: Yevhen Babiichuk (DustDFG)
- 许可证: **CC0 1.0**（见 `pixel_art_clouds/pixel_art_clouds/CC0.txt`）
- 内容: 24×24 像素艺术云朵 (`clouds.png` + Aseprite 源文件 `clouds.ase`)。
- Pythontopia 用途: 天空层动态云、暴风雨前的乌云替换。
- 署名要求: CC0 无需署名。

## 3. Rain Particle Animated (donte)

- 目录: `oga_rain_particle/`
- 来源: <https://opengameart.org/content/rain-particle-animated>
- 直接下载: <https://opengameart.org/sites/default/files/rain_drop.zip>
- 作者: donte（OpenGameArt 用户）
- 许可证: **CC0 1.0**
- 作者注: "Feel free to use it even commercially. Credit NOT required."
- 内容: 10×10 像素 5 帧雨滴动画 `rain_drop_0.png` ~ `rain_drop_4.png`。
- Pythontopia 用途: 雨天 Tilesprite 循环，与浇水/作物生长事件耦合。
- 署名要求: CC0 无需署名。

## 4. Pixel Art Snowflakes (alxl)

- 目录: `oga_pixel_snowflakes/`
- 来源: <https://opengameart.org/content/pixel-art-snowflakes>
- 直接下载: <https://opengameart.org/sites/default/files/pixel_snowflakes.png>
- 作者: alxl（OpenGameArt 用户）
- 许可证: **CC0 1.0**（作者在 Copyright Notice 明确："Available under CC0; attribution is appreciated but not necessary."）
- 备注: OGA 上传表单同时勾选了 CC-BY 4.0 / CC-BY 3.0 / OGA-BY 3.0 选项；以作者文字声明（CC0）为最宽松授权，覆盖前者。
- 内容: 54×27 的 9×9 雪花 sprite sheet，共 18 个雪花。
- Pythontopia 用途: 冬季天气下的雪花粒子层。
- 署名要求: CC0 无需署名，建议致谢页保留 `alxl @ OpenGameArt`。

## 5. Pixel Art Cartoon Sun (bonzille)

- 目录: `oga_pixel_sun/`
- 来源: <https://opengameart.org/content/pixel-heart-cartoon-sun>
- 直接下载: <https://opengameart.org/sites/default/files/sun_9.png>
- 作者: bonzille（OpenGameArt 用户）
- 许可证: **CC0 1.0**
- 内容: 226×222 卡通像素太阳 `sun.png`。
- Pythontopia 用途: 晴天 HUD 图标、白天昼夜系统的太阳本体（可叠加 Kenney 粒子作为光晕）。
- 署名要求: CC0 无需署名。

## 6. Pixel Lightning (Marco L.)

- 目录: `oga_pixel_lightning/`
- 来源: <https://opengameart.org/content/pixel-lightning>
- 直接下载:
  - <https://opengameart.org/sites/default/files/Lightning_1.png>
  - <https://opengameart.org/sites/default/files/Lightning_Yellow_0.png>
  - <https://opengameart.org/sites/default/files/Lightning_Cyan_0.png>
- 作者: Marco L. (OpenGameArt 用户 `marco-l`)
- 许可证: **CC-BY 4.0**（<https://creativecommons.org/licenses/by/4.0/>）
- 内容: 三套闪电 sprite sheet — 白色 `Lightning.png` (400×175)、黄色 `Lightning_Yellow.png` (600×350)、青色 `Lightning_Cyan.png` (600×350)。
- Pythontopia 用途: 雷暴天气矿洞事件、Bug 警示动效。
- 署名要求: **必须署名**，建议在 `frontend/assets/CREDITS.md` 中写入：
  ```
  Pixel Lightning © Marco L. — Licensed under CC-BY 4.0
  Source: https://opengameart.org/content/pixel-lightning
  ```

## 7. Weather Icons (Firefly in the Dusk)

- 目录: `oga_weather_icons/`
- 来源: <https://opengameart.org/content/weather-icons>
- 直接下载: <https://opengameart.org/sites/default/files/Weather%20Icon%20Set.zip>
- 作者: Firefly in the Dusk（OpenGameArt 用户）
- 许可证: **CC0 1.0**（作者注："No attribution required as per the CC0 license."）
- 内容: 100×100 像素 9 张天气状态图标（`sunnyWeather.png` / `cloudyWeather.png` / `rainyWeather.png` / `snowyWeather.png` / `stormyWeather.png` / `sunnyRainyWeather.png` / `snowySunnyWeather.png` / `variableWeather.png` / 拼图 `Set copy.png` 与 PSD 源 `Set.psd`）。
- Pythontopia 用途: HUD 天气切换按钮、班级仪表盘日程播报。
- 署名要求: CC0 无需署名。

---

## 覆盖矩阵

| 元素 | 主选素材 | 备选/合成素材 |
|---|---|---|
| 雨滴 | `oga_rain_particle/rain_drop_0..4.png` | `oga_weather_icons/rainyWeather.png` |
| 雪花 | `oga_pixel_snowflakes/pixel_snowflakes.png` | `oga_weather_icons/snowyWeather.png` |
| 太阳/阳光 | `oga_pixel_sun/sun.png` | `oga_weather_icons/sunnyWeather.png` + `kenney_particle_pack/.../light_01.png` 光晕 |
| 云 | `pixel_art_clouds/pixel_art_clouds/clouds.png` | `oga_weather_icons/cloudyWeather.png` |
| 雷电 | `oga_pixel_lightning/Lightning.png` | `oga_pixel_lightning/Lightning_Yellow.png`、`Lightning_Cyan.png`、`oga_weather_icons/stormyWeather.png` |
| 通用粒子（火花/光晕/烟雾/星） | `kenney_particle_pack/PNG (Transparent)/spark_*.png`、`light_*.png`、`smoke_*.png`、`star_*.png` | — |

5 种核心天气元素均双线覆盖（一份像素动画 + 一份图标/状态精灵），可在 Phaser 3 中分别接入 ParticleEmitter 与 SpriteSheet。

## 复核与下一步

1. 任何派生（合并 sprite、调色、缩放）产生的新文件，必须在 `frontend/assets/CREDITS.md` 中保留原作者署名与原始链接。
2. 对 CC-BY 4.0 的 `Pixel Lightning` 必须在游戏内或文档中显式署名 `Marco L.` 与协议名称。
3. 建议在 `frontend/src/game/weather` 子模块中按 `rain/snow/sun/cloud/thunder` 五个 enum 注册资源 key，并在 README 中链回本 LICENSE.md。
4. 如未来需要更大尺寸的暴风雨云层，可补 `https://opengameart.org/content/clouds-1`（rrcaseyr，**CC-BY-SA 3.0** — Share Alike，会传染许可，请确认接受后再纳入）。
