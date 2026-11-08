# 开发文档索引

日常开发先找对应主题的当前规范，再查看操作手册；按日期记录的验收和迁移过程用于追溯。当前分支为reborn，唯一内置主线为arrival。新增日常演出不增加任务。

## 按任务查阅

| 要做的工作 | 先读 | 实现入口 |
| --- | --- | --- |
| 了解产品范围和学习流程 | [产品说明](product.md) | `frontend/src/features`、`backend/app` |
| 修改学生界面 | [设计索引](design/README.md)、[游戏规范](design/game-interface.md) | `OverlayHost.vue`、共享布局和游戏主题 |
| 修改教师后台 | [后台规范](design/teacher-interface.md) | 教师组件和`teacherTheme.css` |
| 修改任务、学习目标或NPC对白 | [内容编写](content-authoring.md) | `backend/content/chapters`、`backend/content/ambient` |
| 修改数据、鉴权和业务接口 | [架构](architecture.md)、[API契约](api-contract.md) | `backend/app`、`backend/alembic` |
| 启动、导入材料、迁移或运行检查 | [开发说明](development.md) | `deploy`、锁文件、测试与脚本 |
| 确认通过项、跳过项和外部服务限制 | [实际验收](acceptance.md) | 文档所列的实际证据与验收用例 |
| 继续上次尚未完成的工作 | [续做记录](reborn-migration.md) | 最新记录和对应当前规范 |

## 文档职责

- **当前规范**：`product.md`、`architecture.md`、`api-contract.md`、`content-authoring.md`与`design/`说明现在应当如何工作；行为改变时直接更新对应章节。
- **操作手册**：`development.md`负责可执行的环境、启动、导入、迁移、测试和排查步骤。实际命令统一写在这里，其他文档链接引用。
- **验收证据**：`acceptance.md`区分实际通过、失败、跳过与未覆盖；旧轮次的结果只证明当时版本。
- **续做记录**：`reborn-migration.md`用于延续工作和追溯决策。按日期的旧记录不能替代当前规范，端口、镜像、迁移头和测试数量应重新核实。
- **项目约束**：[AGENTS.md](../AGENTS.md)保持简短规则与权威文档入口，不重复完整产品、设计或部署手册。

`development.md`、`acceptance.md`和`reborn-migration.md`当前仍保留多轮历史。不要从旧段落直接复制环境名或迁移版本；先确认其日期、环境与适用范围。后续归档时保留失败、跳过、来源与证据，并维护原入口链接。

## 维护规则

1. 一个规则保留一个主要说明位置，其他页面链接引用；不再把同一轮完整过程重复追加到多个文档。
2. 当前说明写行为和约束；验收记录写具体版本、环境、结果和限制；本机凭据、SQL备份、截图及trace继续放忽略目录。
3. 修改实现时同步相关规范与API；只有真实执行过的检查才能记为通过，使用当前YAML与测试用例作为可核对示例。
4. 保持现有主要文档路径稳定。归档历史时集中处理并验证链接，不另建平行计划或第二套规范。

项目和素材授权入口为根目录[LICENSE](../LICENSE)、[美术许可](../LICENSE-ART.md)及保留素材来源。`legal/open_source.md`含历史技术栈记录，核对当前第三方依赖时还须检查现有锁文件与上游许可，保留来源说明。
