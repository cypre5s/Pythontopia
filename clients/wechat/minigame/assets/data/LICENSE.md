# 随包数据授权（assets/data）

| 文件 | 内容 | 来源 | 授权 |
| --- | --- | --- | --- |
| `npcs.json` | 小镇 NPC 名字 + 一句对白 | Pythontopia 项目自有内容，由仓库 `data/npcs/<key>/dialog.json` 的 greet 节点与 `index.json` 名字**精简改写**而来（把 `{{playerName}}`、`{{stats.*}}`、`{{profileAdaptive:…}}` 等模板占位解析为纯文本一行） | CC-BY-SA-4.0（项目自有内容，非第三方素材） |

说明：

- 本目录仅含**文本数据**，不含任何第三方像素美术；像素素材授权见 `assets/tilesets/License.txt`。
- 渲染端仅取「名字 + 一句对白」用于交互横幅，不复制完整对话树（多分支对话与画像/统计模板属于配套学习小程序的范畴）。
- 新增/修改本目录文件时，请同步更新本表。
