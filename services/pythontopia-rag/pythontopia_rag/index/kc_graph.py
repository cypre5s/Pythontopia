"""KC 前置关系图（GraphRAG 思想的零抽取自建版）。

业界 GraphRAG/LightRAG 最贵的一步是「用 LLM 从非结构化文本抽实体关系」；本项目的
课程知识图谱（65 KC + 前置边）在 ``course_kc.prerequisite_codes`` 里是**人工标注的
结构化数据**（V125 seed），直接加载即得图——零 LLM 抽取成本拿到图谱检索的核心收益：

    学生的困惑常源于前置 KC 没掌握。查询锚定 KC 后沿前置边扩展检索范围，
    「递归为什么不对」也能召回前置 KC（函数定义/返回值）的课件页。

fail-soft：DB 不可用时返回空图，检索行为退化为现状（不扩展），绝不阻塞启动。
"""

from __future__ import annotations

import logging
from dataclasses import dataclass, field

import psycopg
from psycopg.rows import dict_row

from pythontopia_rag.data.loader import normalize_db_url

logger = logging.getLogger(__name__)

_FETCH_SQL = """
    SELECT kc_code, prerequisite_codes
    FROM course_kc
    WHERE pack_id = 43
"""


@dataclass
class KcGraph:
    """KC → 直接前置 KC 列表的邻接表；空图时所有查询返回空（行为退化为不扩展）。"""

    prerequisites: dict[str, list[str]] = field(default_factory=dict)

    @property
    def size(self) -> int:
        return len(self.prerequisites)

    def prerequisites_of(self, kc_codes: list[str], *, depth: int = 1) -> list[str]:
        """求一组 KC 的前置闭包（BFS 至 ``depth`` 跳，带 visited 防环），**不含输入自身**。

        默认 1 跳：教育语义上「直接前置」最相关；更深的祖先离当前困惑过远，
        且会稀释主题页配额。结果按首次发现顺序去重输出（稳定可测）。
        """
        if depth < 1 or not self.prerequisites:
            return []
        seeds = [code for code in kc_codes if code]
        visited: set[str] = set(seeds)
        frontier = list(seeds)
        found: list[str] = []
        for _ in range(depth):
            next_frontier: list[str] = []
            for code in frontier:
                for prereq in self.prerequisites.get(code, []):
                    if prereq and prereq not in visited:
                        visited.add(prereq)
                        found.append(prereq)
                        next_frontier.append(prereq)
            if not next_frontier:
                break
            frontier = next_frontier
        return found


def fetch_kc_graph(db_url: str) -> KcGraph:
    """从 DB 加载前置邻接表；任何失败返回空图 + 告警（fail-soft，不阻塞启动）。"""
    try:
        with psycopg.connect(normalize_db_url(db_url), row_factory=dict_row) as conn:
            with conn.cursor() as cur:
                cur.execute(_FETCH_SQL)
                rows = cur.fetchall()
    except Exception as exc:  # noqa: BLE001 - DB 不可用时退化为不扩展
        logger.warning(
            "KC graph load failed; prerequisite expansion disabled; error_type=%s",
            type(exc).__name__,
        )
        return KcGraph()
    adjacency = {
        str(row["kc_code"]): [str(code) for code in (row["prerequisite_codes"] or []) if code]
        for row in rows
    }
    graph = KcGraph(prerequisites=adjacency)
    logger.info("KC graph loaded: %d nodes", graph.size)
    return graph
