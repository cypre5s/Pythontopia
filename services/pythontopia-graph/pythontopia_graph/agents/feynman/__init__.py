"""费曼反演（AI 学弟）相关的内部工具包。

目前提供 :mod:`pythontopia_graph.agents.feynman.bug_bank`：一份「代码找茬」用的
精选 bug 样本库（curated），并可选地融合离线 LLM 大规模生成的样本（generated JSON）。

设计上本包**不反向依赖** :mod:`pythontopia_graph.agents.feynman_tutee_agent`，
以避免循环导入：agent 单向导入本包。
"""
