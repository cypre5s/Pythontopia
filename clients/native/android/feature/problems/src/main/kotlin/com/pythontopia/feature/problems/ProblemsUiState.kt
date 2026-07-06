package com.pythontopia.feature.problems

/**
 * 题库浏览状态：原始题目 + 过滤/排序条件，派生 [visible]（与 Web ProblemListPage 一致）。
 */
data class ProblemsUiState(
    val all: List<ProblemRow> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val search: String = "",
    val difficulty: ProblemDifficulty? = null,
    val status: ProblemStatus? = null,
    val sort: ProblemSort = ProblemSort.CHAPTER,
    val kcCode: String? = null,
) {
    /** 「至少 1 道题」的 KC 选项（kcCode → 显示名），按章节序 + KC 码排序。 */
    val kcOptions: List<Pair<String, String>>
        get() = all
            .distinctBy { it.kcCode }
            .sortedWith(compareBy({ it.chapterOrder ?: 99 }, { it.kcCode }))
            .map { it.kcCode to it.kcName }

    val visible: List<ProblemRow>
        get() {
            val q = search.trim().lowercase()
            val filtered = all.asSequence()
                .filter { row ->
                    q.isEmpty() ||
                        row.problemId.toString().contains(q) ||
                        row.title.lowercase().contains(q) ||
                        row.kcCode.lowercase().contains(q) ||
                        row.kcName.lowercase().contains(q) ||
                        row.statementMarkdown.lowercase().contains(q)
                }
                .filter { kcCode == null || it.kcCode == kcCode }
                .filter { difficulty == null || it.difficulty == difficulty }
                .filter { status == null || it.status == status }
                .toList()
            return when (sort) {
                ProblemSort.DIFFICULTY -> filtered.sortedBy { difficultyWeight(it.difficulty) }
                ProblemSort.ATTEMPTS -> filtered.sortedByDescending { it.attempts }
                ProblemSort.CHAPTER -> filtered.sortedWith(
                    compareBy({ it.chapterOrder ?: 99 }, { it.problemId }),
                )
            }
        }

    val hasActiveFilters: Boolean
        get() = search.isNotBlank() || difficulty != null || status != null || kcCode != null

    private fun difficultyWeight(d: ProblemDifficulty): Int = when (d) {
        ProblemDifficulty.LOW -> 1
        ProblemDifficulty.MEDIUM -> 2
        ProblemDifficulty.HIGH -> 3
        ProblemDifficulty.UNKNOWN -> 9
    }
}
