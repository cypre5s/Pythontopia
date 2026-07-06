# Pythontopia PPT 大纲（阶段 14 草稿，阶段 15 出终稿）

> 共 ≤ 20 页，与 `demo_script.md` 完全配套。

## 章节安排

1. **封面**：项目名、副标题、团队、日期、Logo。
2. **背景与痛点**：高校 Python 初学者三大痛点 + 现有平台局限。
3. **教育闭环**：画像 → 路径 → 资源 → 练习 → 评估 → 复盘 的循环图。
4. **小镇游戏化承载**：星露谷式像素 + 教育映射对照表（种子=KC、浇水=资源、除草=练习、病害=Bug）。
5. **技术架构**：浏览器→Nginx→Backend→(Graph, RAG, Postgres, Redis) 的 mermaid 图（与 `docs/architecture/system_design.md` 同源）。
6. **多智能体协作**：11 个 Agent 矩阵 + Grounding/Safety 二次审查。
7. **课件 RAG 与防幻觉**：chunker / embedder / safety + 数字/KC 严格 grounding 例子。
8. **学生端核心场景**：画像 / 知识田 / 资源工坊 / 训练场 / Bug 矿洞 / 复盘大厅 截图。
9. **教师端轻量班级**：班级看板 6 tabs + KC 热力图截图。
10. **视频演示**：嵌入 7 分钟演示视频的 30 秒精剪片段。
11. **工程质量**：229 测试通过、TS strict、DTO、Agent 输出结构化、grounding / safety 拦截。
12. **AI 协作披露**：哪些代码由 AI 协助生成、哪些由人工编写、AI Coding 工具与场景。
13. **比赛合规**：素材 License、依赖清单、不复制官方素材、AI 披露完整。
14. **已知风险 / 演示备用**：LLM 限流、Pyodide 首加载、narrative grounding 失败。
15. **未来工作**：多 pack、移动端、长视频、学情诊断。
16. **致谢与团队**：成员、贡献、导师、致谢资源。
17. **附录 A**：API & 路由清单。
18. **附录 B**：测试统计与覆盖范围。
19. **附录 C**：演示账号与一次性密码（演示后随 `teardown_demo.sh` 清理）。
20. **结束语**。

## 视觉规范

- 主色：像素小镇棕黄系（与 tokens.css 同源 `#6e4f1d / #c9a86b / #fff7e6 / #4d3613`）。
- 字体：标题霞鹜文楷，正文 PingFang SC，代码 JetBrains Mono。
- 每页右下角放统一页码 + Pythontopia logo。

## 嵌入素材清单

- 7 分钟演示视频精剪版（`docs/release/demo_v1.mp4`）。
- 架构图 PNG（mermaid 导出）。
- 学生端 7 张截图 + 教师端 3 张截图（演示账号录制，无真实 PII）。

阶段 15 把本大纲扩成 `.pptx` 与 `.pdf`，存放在 `docs/release/`。
