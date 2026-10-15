# Web 系统安全、远征与学习分身审计设计

日期：2026-10-15

基线：`c95b54c4`（工作区干净）；范围限定为 Web、Spring Boot、Python Graph/RAG 与本地部署边界。Android、Electron、微信端和 ECS 不在本轮范围。

## 当前完成状态

- 导师已通过固定阶段 AutoGen 执行；答疑及训练工作流使用现有 API/SSE、V199 检查点和附件授权。Alethicode 的 AutoGen runtime/V124 迁移仍在未提交工作树，Codex 10/10 至 10/12 记录还标有 PostgreSQL round-trip、跨服务恢复和完整图片浏览器流等未验证项。不得将该工作树视为已发布基线或整仓复制。
- Learner Twin 已聚合真实掌握与复习事件，限制自评证据，按统一周窗口汇总；自适应选择器复用 KC 和 FSRS-lite。7 日延迟复测、旧新策略对照及效果校准尚无结果。
- 远征已使用服务端 run、动作和判题账本；图/模型失败可复用审核题或 Golden 题。Phaser Boss 受击演出已实现，最终 Boss 多阶段真实浏览器判题旅程仍未完成。
- 管理台已有运维、题目审核和用户管理三个真实 API 页面，布局与 Alethicode 主色、面板和表格密度大体一致。缺少的数据/API 不以占位页面补齐；当前差异主要是少数间距/宽度。
- DeepSeek 文本及合成 PNG 输入的真实 Tutor 请求已有 HTTP 200 与文字回答记录。图片是输入，输出仍为文本；TTS 与其他媒体生成入口保持关闭。此前运行日志出现过额外预生成模型请求，供应商账单/token 总量尚未对账。

## 安全审计结论

- 本轮静态审计未确认 P0；账号角色白名单、停用账号与 token 撤销、抽查的用户资源归属、媒体 HMAC 授权、服务端远征结算和本地 Java 子进程回退均未发现可复现绕过。
- 班级加入码与注册接口已经在公开 Nginx 入口配置按来源 IP 的请求限速（加入码 `5r/m`、突发 5；登录/注册 `5r/s`、突发 10）。因此不新增重复的 DB 限流表。当前证明来自配置静态检查，不代表已对公网运行验证。
- 已确认两项值得最小修复：CORS 接受任意非裸 `*` 的 origin pattern，并与凭据同用；故事板合成异常会把底层异常文本返回客户端。修复后端拒绝所有含通配符的来源模式，并将外部错误收敛为通用错误，详细原因仅留受控服务端日志。
- 生产 Piston 的 token 头不一定由服务本身验证；部署必须由可信代理鉴权或私网 ACL 隔离。实际生产网络未检查，本轮不访问 ECS。
- 公开教师邀请码只校验最小字符数，实际熵未检查；CORS/限速生产配置和完整 IDOR 路由矩阵也未作运行态验证。避免读取或记录秘密值。

## 设计选择与研究依据

1. **安全边界**：按 OWASP API Security 2023 将对象级权限、属性级授权和资源消耗作为逐端点检查项；JWT 验签、过期和主体约束继续留在服务端。不会把限速、角色、判题结果或学习证据交给浏览器决定。
2. **AutoGen**：保留固定顺序 AgentChat 与显式阶段终止。官方文档提供消息数/超时/取消的终止原语；Pythontopia 已有取消、阶段 checkpoint、恢复请求绑定和清理，不迁入 Alethicode 的第二套协议、V124 表或异步 WebSocket。
3. **个性化练习**：继续用可解释的服务端 KC 掌握度与 FSRS-lite。检索练习和间隔练习有长期记忆研究依据；BKT 可作为以后校准的简单比较基线，DKT/AKT 暂缓到有足量、同题标签稳定的长序列与离线校准结果后再评估。
4. **Roguelike**：仅借鉴可读分支、有限风险/回报和阶段反馈；不复制商业作品素材或专有内容。远征状态、HP、遗物、分数和结算继续以服务端记录为准。
5. **Web 可用性**：管理和学习操作保留键盘可达、清晰焦点、窄屏重排和明确异步状态；按 WCAG 2.2 检查焦点、重排和非文本替代。仅修改已定位的问题，不全站换主题或格式化。

## 已检索的主要来源

- [OWASP API Security Top 10 2023](https://api-security.owasp.org/editions/2023/en/0x11-t10/)，特别是 [BOLA](https://api-security.owasp.org/editions/2023/en/0xa1-broken-object-level-authorization/) 与 [资源消耗](https://api-security.owasp.org/editions/2023/en/0xa4-unrestricted-resource-consumption/)。
- [Spring Security JWT Resource Server 官方参考](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)。
- [AutoGen AgentChat 终止与取消文档](https://microsoft.github.io/autogen/stable/user-guide/agentchat-user-guide/tutorial/termination.html)。
- [FSRS 算法说明](https://github.com/open-spaced-repetition/fsrs4anki/wiki/The-Algorithm)；[检索测试改善长期保持的实验](https://pubmed.ncbi.nlm.nih.gov/16507066/)；[知识追踪 BKT 经典论文](https://link.springer.com/article/10.1007/BF01099821)。
- [W3C WCAG 2.2](https://www.w3.org/TR/wcag/)。

## 验收边界

- 先对 CORS 通配来源与故事板异常泄露做 RED/GREEN，再跑对应 Maven 测试。
- 对导师、远征、Learner Twin 与后台的改动逐项跑受影响测试；真实 DeepSeek 只使用明确的小样本文本/图片输入，不用模型请求做压测。图片请求必须验证文本响应，不期望图像输出。
- 远征完整 Boss 演出通过真实浏览器流程验证后才标记完成；Twin 学习效果必须等真实对照数据和 7 日复测，期间明确标成未验证。
- 性能置于功能和安全之后；报告 API/阶段、样本数、P50/P95、是否触发模型及清理结果。没有实测的数值标注待测。
