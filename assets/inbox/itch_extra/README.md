# itch_extra 手动下载记录

状态：未下载，目录内无可使用素材。

## 候选素材

| Author | Pack | Public license note | Source |
| --- | --- | --- | --- |
| ansimuz | sunny-land-pixel-game-art | Creative Commons Zero v1.0 | <https://ansimuz.itch.io/sunny-land-pixel-game-art> |
| ansimuz | fantasy-weapons-set | Creative Commons Zero v1.0 | <https://ansimuz.itch.io/fantasy-weapons-set> |

## 阻塞原因

R-100 crawl 已确认两个包的 itch.io 页面为 CC0 候选，但 itch.io 公开详情页不暴露稳定 zip URL。下载需要浏览器 session 先 POST 获取 redirect/upload token；当前 agent 环境没有浏览器登录态，也不能伪造 session token。

因此本目录现在只保留手动补充占位说明。不要把本目录计入已入库素材，不要把上述两个包写入 runtime asset inventory，也不要生成 `LICENSE.md` 声称素材已下载。

## 手动补充步骤

1. 使用浏览器登录 itch.io。
2. 分别打开上表两个 source URL，下载免费 CC0 包。
3. 解压到：
   - `assets/inbox/itch_extra/sunny-land-pixel-game-art/`
   - `assets/inbox/itch_extra/fantasy-weapons-set/`
4. 为每个子目录写入 `LICENSE.md`，至少包含 author、source URL、license name/link、download time、download operator。
5. 仅在 license proof 与实际文件都存在后，再考虑同步到 `frontend/public/assets/pixel/` 或 Android asset pipeline。

参考记录：`.work/R-100/assets_crawl/09_itch_io_free.md`。
