package com.pythontopia.feature.classroom

import com.pythontopia.core.common.learner.StudentRole
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * 测试 [ClassroomViewModel] 三条核心路径：
 *  1. 学生 bind 后 myClassrooms → quests 顺序拉取，state 装配到 [ClassroomUiState.StudentState]
 *  2. 加入新班级 join → 班级追加 + 自动选中 + 触发该班 quests 拉取
 *  3. 提交 evidence 成功 → 对应 quest 更新 + `message` 置位 + `submittingQuestId` 清空
 *  4. 提交 evidence 失败 → state 退化到 [ClassroomUiState.Failure]，不影响其他 quest
 *
 * 测试用 [UnconfinedTestDispatcher] 接管 `Dispatchers.Main`，让 `viewModelScope.launch`
 * 在测试线程同步执行，避免引入 advanceUntilIdle 噪音。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClassroomViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val repository: ClassroomRepository = mockk(relaxUnitFun = true)
    private lateinit var viewModel: ClassroomViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = ClassroomViewModel(repository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `student bind loads classrooms and quests`() = runTest {
        coEvery { repository.myClassrooms() } returns listOf(SAMPLE_CLASSROOM)
        coEvery { repository.quests(SAMPLE_CLASSROOM.classroomId, asTeacher = false) } returns
            listOf(SAMPLE_QUEST)

        viewModel.bind(StudentRole.STUDENT)

        val state = viewModel.state.value
        assertTrue(state is ClassroomUiState.StudentState, "Expected StudentState but was $state")
        val student = state as ClassroomUiState.StudentState
        assertEquals(1, student.classrooms.size)
        assertEquals(SAMPLE_CLASSROOM.classroomId, student.selectedClassroomId)
        assertEquals(1, student.quests.size)
        assertEquals(SAMPLE_QUEST.id, student.quests.first().id)
        coVerify(exactly = 1) { repository.myClassrooms() }
        coVerify(exactly = 1) { repository.quests(SAMPLE_CLASSROOM.classroomId, asTeacher = false) }
    }

    @Test
    fun `teacher bind loads heatmap and quests concurrently`() = runTest {
        coEvery { repository.myClassrooms() } returns listOf(SAMPLE_CLASSROOM)
        coEvery { repository.quests(SAMPLE_CLASSROOM.classroomId, asTeacher = true) } returns
            listOf(SAMPLE_QUEST)
        coEvery { repository.heatmap(SAMPLE_CLASSROOM.classroomId) } returns listOf(SAMPLE_HEATMAP)
        coEvery { repository.errorHotspots(SAMPLE_CLASSROOM.classroomId) } returns
            listOf(SAMPLE_HOTSPOT)

        viewModel.bind(StudentRole.TEACHER)

        val state = viewModel.state.value as ClassroomUiState.TeacherState
        assertEquals(1, state.classrooms.size)
        assertEquals(1, state.heatmap.size)
        assertEquals(1, state.errorHotspots.size)
        assertEquals(1, state.quests.size)
    }

    @Test
    fun `student join appends classroom and selects it`() = runTest {
        coEvery { repository.myClassrooms() } returns emptyList()
        viewModel.bind(StudentRole.STUDENT)

        coEvery { repository.join("ABC123") } returns SAMPLE_CLASSROOM
        coEvery { repository.quests(SAMPLE_CLASSROOM.classroomId, asTeacher = false) } returns
            listOf(SAMPLE_QUEST)

        viewModel.join("ABC123")

        val state = viewModel.state.value as ClassroomUiState.StudentState
        assertEquals(1, state.classrooms.size)
        assertEquals(SAMPLE_CLASSROOM.classroomId, state.selectedClassroomId)
        assertEquals(false, state.pendingJoin)
        assertEquals("已加入：${SAMPLE_CLASSROOM.name}", state.message)
        assertEquals(1, state.quests.size)
    }

    @Test
    fun `student join with short code shortcuts to failure`() = runTest {
        coEvery { repository.myClassrooms() } returns emptyList()
        viewModel.bind(StudentRole.STUDENT)

        viewModel.join("ABC")

        val state = viewModel.state.value as ClassroomUiState.Failure
        assertEquals("INVALID_JOIN_CODE", state.code)
        coVerify(exactly = 0) { repository.join(any()) }
    }

    @Test
    fun `submit evidence updates quest and sets message on success`() = runTest {
        coEvery { repository.myClassrooms() } returns listOf(SAMPLE_CLASSROOM)
        coEvery { repository.quests(SAMPLE_CLASSROOM.classroomId, asTeacher = false) } returns
            listOf(SAMPLE_QUEST)
        viewModel.bind(StudentRole.STUDENT)

        val completed = SAMPLE_QUEST.copy(studentQuestStatus = "COMPLETED")
        coEvery { repository.submitEvidence(99L, any()) } returns completed

        viewModel.submitEvidence(SAMPLE_QUEST, QuestEvidence(submissionId = 5001L))

        val state = viewModel.state.value as ClassroomUiState.StudentState
        assertNull(state.submittingQuestId)
        assertEquals("COMPLETED", state.quests.first().studentQuestStatus)
        assertEquals("已提交：${SAMPLE_QUEST.title}", state.message)
    }

    @Test
    fun `submit evidence without student quest id falls to failure`() = runTest {
        coEvery { repository.myClassrooms() } returns listOf(SAMPLE_CLASSROOM)
        coEvery { repository.quests(SAMPLE_CLASSROOM.classroomId, asTeacher = false) } returns
            listOf(SAMPLE_QUEST.copy(studentQuestId = null))
        viewModel.bind(StudentRole.STUDENT)

        viewModel.submitEvidence(
            SAMPLE_QUEST.copy(studentQuestId = null),
            QuestEvidence(note = "noop"),
        )

        val state = viewModel.state.value as ClassroomUiState.Failure
        assertEquals("STUDENT_QUEST_MISSING", state.code)
        coVerify(exactly = 0) { repository.submitEvidence(any(), any()) }
    }

    @Test
    fun `submit evidence failure exposes Failure state`() = runTest {
        coEvery { repository.myClassrooms() } returns listOf(SAMPLE_CLASSROOM)
        coEvery { repository.quests(SAMPLE_CLASSROOM.classroomId, asTeacher = false) } returns
            listOf(SAMPLE_QUEST)
        viewModel.bind(StudentRole.STUDENT)

        coEvery { repository.submitEvidence(99L, any()) } throws RuntimeException("422 invalid evidence")

        viewModel.submitEvidence(SAMPLE_QUEST, QuestEvidence(submissionId = 5001L))

        val state = viewModel.state.value as ClassroomUiState.Failure
        assertEquals("EVIDENCE_REJECTED", state.code)
        assertNotNull(state.message)
    }

    @Test
    fun `consumeMessage clears toast text`() = runTest {
        coEvery { repository.myClassrooms() } returns listOf(SAMPLE_CLASSROOM)
        coEvery { repository.quests(SAMPLE_CLASSROOM.classroomId, asTeacher = false) } returns
            listOf(SAMPLE_QUEST)
        coEvery { repository.join("ABC123") } returns SAMPLE_CLASSROOM
        viewModel.bind(StudentRole.STUDENT)
        viewModel.join("ABC123")

        val withMsg = viewModel.state.value as ClassroomUiState.StudentState
        assertNotNull(withMsg.message)

        viewModel.consumeMessage()

        val cleared = viewModel.state.value as ClassroomUiState.StudentState
        assertNull(cleared.message)
    }

    companion object {
        private val SAMPLE_CLASSROOM = Classroom(
            classroomId = 7L,
            name = "Python 周一班",
            description = "示例班级",
            teacherId = 100L,
            packId = 43L,
            status = "ACTIVE",
            joinCode = "ABC123",
        )

        private val SAMPLE_QUEST = ClassroomQuest(
            id = 11L,
            classroomId = 7L,
            title = "完成 for 循环练习",
            description = null,
            kcCodes = listOf("KC_FOR_LOOP"),
            nodeType = "SOLVE_PROBLEM",
            dueDate = "2026-06-01",
            status = "PUBLISHED",
            targetProblemId = 2001L,
            targetResourceType = null,
            studentQuestId = 99L,
            studentQuestStatus = "ASSIGNED",
            requiredEvidenceType = "submission",
        )

        private val SAMPLE_HEATMAP = KcHeatmapEntry(
            kcCode = "KC_FOR_LOOP",
            kcName = "for 循环",
            chapterId = 4L,
            chapterOrder = 4,
            avgMastery = "0.65",
            studentCount = 40,
            masteredCount = 12,
            learningCount = 22,
            strugglingCount = 6,
        )

        private val SAMPLE_HOTSPOT = ErrorHotspot(
            kcCode = "KC_FOR_LOOP",
            kcName = "for 循环",
            failedSubmissionCount = 38,
            affectedStudentCount = 15,
            categories = listOf(
                ErrorHotspot.ErrorCategory(category = "INDEX_OUT_OF_RANGE", count = 20),
                ErrorHotspot.ErrorCategory(category = "INFINITE_LOOP", count = 18),
            ),
        )
    }
}
