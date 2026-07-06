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

/** 画像页顶层 tab：仪表盘展示 8 维，自评 tab 编辑 4 主观维。 */
enum class ProfileTab { DASHBOARD, SUBJECTIVE }

data class ProfileUiState(
    val tab: ProfileTab = ProfileTab.DASHBOARD,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val profile: LearnerProfileDto? = null,
    /** 8 维得分快照（GET /api/learner/profile/dimensions）；空表示尚未加载。 */
    val dimensions: List<ProfileDimensionDto> = emptyList(),
    val error: AppError? = null,
    val savedDimensionKey: String? = null,
)

/** 画像 feature ViewModel；单一 [StateFlow] 承载 UI 状态。 */
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
     * 并发拉取画像主体（GET /profile）与 8 维快照（GET /profile/dimensions）；任一失败均上报错误。
     * GET /profile 返回 204 时映射为 Success(null)（未完成访谈），dimensions 仍返回 8 维默认快照。
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

    /** 学生 PUT 自评；客户端先校验 [ProfileDimensions.SELF_EDITABLE]，成功后刷新快照。 */
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
