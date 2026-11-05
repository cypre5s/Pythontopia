# Super Retro World 本机源素材记录

2026-11-05，用户提供本机 `D:\godot-one`，并确认素材由协作找素材的朋友取得、已购且可用于项目。本目录仅保留来源与技术核对记录，不复制完整素材包或Godot导入缓存。

- 作者：Gif（Jeff）。
- 官方产品页：https://store.godotengine.org/asset/gif/super-retro-world/
- 官方页面标注：Custom Commercial license；链接：https://storage.rpgpowerforge.com/super-retro-world.license
- 本机目录没有随附README/LICENSE；“用户确认已购可用”与上游许可原文分别记录，不将本记录表述成CC授权或原始许可证副本。

与现有运行资源比对：32张角色表、12栋房屋、121张非火把道具的SHA256逐项一致；18张火把按现有规则取第一帧后像素一致；3张源Tileset与Godot `.tres` 能重建512×112的220格图集，RGBA与现有town_tiles.png逐字节一致。因此现有184个SRW PNG都可由本机源包重现。

本机源包还含门、书、箱、火、水、动物、作物和农事动作，按实际功能选择性集成。本轮图书馆使用原始Farming图集的墙地板、桌椅、书柜与书本帧，以及一个原始门动画表；只复制实际需要的原PNG，布局/帧坐标放在地图JSON，不修改或拼接PNG。

`Icon` 下70张1024×1024的3D渲染图标属于另一种视觉风格，不整组混入当前16像素小镇，也不假定它们与SRW切片具有相同来源。本轮唯一例外为原始`simple_arrow_up.png`：在用户确认本机整包已购可用后，原样复制为`frontend/public/assets/pixel/ui/portal-chevron-up.png`，按8×8逻辑像素显示为出入口小chevron。其本机元数据只标识Blender图标场景，未找到作者字段，不将其归到Gif/SRW作者名下。详见该目录`PORTAL-SOURCE.md`。

只读预检（不会写入或覆盖图片）：

```powershell
python scripts/build_srw_assets.py --source D:\godot-one --check
```

脚本兼容 `Characters/Characters` 嵌套目录与大小写混合文件名。普通提取模式仍会重建运行切片和图集元数据，只有明确准备更新素材时才运行；不将本机绝对路径写成程序默认值。
