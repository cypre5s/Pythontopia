package com.pythontopia.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pythontopia.core.common.ApiError
import com.pythontopia.core.common.AppError
import com.pythontopia.core.common.Result
import com.pythontopia.feature.profile.data.DimensionUpdateRequest
import com.pythontopia.feature.profile.data.LearnerProfileDto
import com.pythontopia.feature.profile.data.ProfileDimensionDto
import com.pythontopia.feature.profile.data.ProfileDimensions
import com.pythontopia.feature.profile.data.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** R-12 demo 顶层 tab：左边看 8 维仪表，右边自评 4 主观。 */
enum class ProfileTab { DASHBOARD, SUBJECTIVE }

data class ProfileUiState(
    val tab: ProfileTab = ProfileTab.DASHBOARD,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val profile: LearnerProfileDto? = null,
    /**
     * 8 维真实得分快照（GET /api/learner/profile/dimensions）。
     * 仪表盘 score/confidence/evidenceCount/可自评 全部来自此处，替代旧的 placeholder 0.5。
     * 恒为 0 或 8 维（后端兜底），空表示尚未加载。
     */
    val dimensions: List<ProfileDimensionDto> = emptyList(),
    val error: AppError? = null,
    val savedDimensionKey: String? = null,
)

/**
 * Profile feature 的 ViewModel。
 *
 * MVI 单一 [StateFlow]：所有 action 通过 mutate state 反映。
 * 这里没用 sealed Intent，因为 demo 阶段只 2 个 action（loadProfile / updateSubjective），
 * 直接暴露 public 函数比 dispatch(intent) 更省样板，等接入访谈 / 自评 dialog 再升级。
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: ProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    fun selectTab(tab: ProfileTab) {
        _state.update { it.copy(tab = tab) }
    }

    fun dismissSavedHint() {
        _state.update { it.copy(savedDimensionKey = null) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /**
     * 同时拉取画像主体（GET /profile）与 8 维得分快照（GET /profile/dimensions）。
     *
     * 两个独立 GET 用 [async] 并发，缩短首屏等待；任一失败都 surface（profile 是教育核心，
     * 不静默降级，AGENTS 约束）。注意 GET /profile 的 204→Success(null) 是正常态（未完成访谈），
     * 此时 dimensions 仍会返回 8 维默认值，仪表盘照常渲染（中性 0.5 + 低置信度）。
     * 自评 PUT 成功后会再次调用本方法，使仪表盘的 score/证据数随之刷新。
     */
    fun loadProfile() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val profileDeferred = async { repository.fetchProfile() }
            val dimensionsDeferred = async { repository.fetchDimensions() }
            val profileResult = profileDeferred.await()
            val dimensionsResult = dimensionsDeferred.await()
            _state.update { current ->
                when {
                    profileResult is Result.Failure ->
                        current.copy(isLoading = false, error = profileResult.error)

                    dimensionsResult is Result.Failure ->
                        current.copy(isLoading = false, error = dimensionsResult.error)

                    profileResult is Result.Success && dimensionsResult is Result.Success ->
                        current.copy(
                            isLoading = false,
                            profile = profileResult.data,
                            dimensions = dimensionsResult.data,
                            error = null,
                        )

                    else -> current.copy(isLoading = false)
                }
            }
        }
    }

    /**
     * 学生 PUT 自评。
     *
     * 在调 repo 前先在客户端校验 [dimensionKey] 是否在 [ProfileDimensions.SELF_EDITABLE]，
     * 这样可以省一次 403 round-trip（后端仍有强校验，是底线）。
     * 成功后立刻刷新 GET，把后端写完的 `final_dimension` 同步到 UI。
     */
    fun updateSubjective(dimensionKey: String, request: DimensionUpdateRequest) {
        if (!ProfileDimensions.isSelfEditable(dimensionKey)) {
            _state.update {
                it.copy(
                    error = ApiError(
                        code = "PROFILE_DIMENSION_NOT_SELF_EDITABLE",
                        message = "学生只能自评 4 个主观维度（${ProfileDimensions.SELF_EDITABLE.joinToString()}）",
                    ),
                )
            }
            return
        }
        _state.update { it.copy(isSaving = true, error = null, savedDimensionKey = null) }
        viewModelScope.launch {
            when (val result = repository.updateDimension(dimensionKey, request)) {
                is Result.Success -> {
                    _state.update {
                        it.copy(isSaving = false, savedDimensionKey = dimensionKey)
                    }
                    loadProfile()
                }

                is Result.Failure -> _state.update {
                    it.copy(isSaving = false, error = result.error)
                }

                Result.Loading -> Unit
            }
        }
    }
}
