package com.pythontopia.feature.dialog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DialogUiState(
    val visible: Boolean = false,
    val npcKey: String = DemoDialogTree.NPC_KEY,
    val npcDisplayName: String = DemoDialogTree.displayNameFor(DemoDialogTree.NPC_KEY),
    val currentNode: DialogNode? = null,
    val friendship: Int = 0,
    val isAdvancing: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class DialogViewModel @Inject constructor(
    private val repository: DialogRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DialogUiState())
    val state: StateFlow<DialogUiState> = _state.asStateFlow()

    fun openFor(npcKey: String) {
        val nodes = DemoDialogTree.nodesFor(npcKey)
        _state.update {
            it.copy(
                visible = true,
                npcKey = npcKey,
                npcDisplayName = DemoDialogTree.displayNameFor(npcKey),
                isAdvancing = true,
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            val remote = repository.loadState(npcKey)
            val nodeId = remote.currentNodeId ?: DemoDialogTree.ROOT_NODE_ID
            val node = nodes[nodeId] ?: nodes.getValue(DemoDialogTree.ROOT_NODE_ID)
            _state.update {
                it.copy(
                    currentNode = node,
                    friendship = remote.friendship,
                    isAdvancing = false,
                )
            }
        }
    }

    fun close() {
        _state.update { DialogUiState() }
    }

    fun choose(choice: DialogChoice) {
        val fromNodeId = _state.value.currentNode?.id ?: return
        if (choice.nextNodeId == null) {
            // 终结分支：本地先关闭，后台 fire-and-forget 推进，避免 UI 卡帧
            _state.update { it.copy(isAdvancing = true) }
            viewModelScope.launch {
                runCatching { repository.advance(_state.value.npcKey, fromNodeId, choice) }
                _state.update { DialogUiState() }
            }
            return
        }
        val nodes = DemoDialogTree.nodesFor(_state.value.npcKey)
        val next = nodes[choice.nextNodeId]
        if (next == null) {
            _state.update {
                it.copy(errorMessage = "对话节点 \"${choice.nextNodeId}\" 未挂载")
            }
            return
        }
        _state.update { it.copy(isAdvancing = true, errorMessage = null) }
        viewModelScope.launch {
            val updated = repository.advance(_state.value.npcKey, fromNodeId, choice)
            _state.update {
                it.copy(
                    currentNode = next,
                    friendship = updated.friendship,
                    isAdvancing = false,
                )
            }
        }
    }
}
