package com.pythontopia.feature.fields

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** 知识田主屏：HUD + 8 列 KC 网格 + 选中格操作栏；纯 Compose，不嵌 LibGDX。 */
@Composable
fun FieldsScreen(viewModel: FieldsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FieldsScreenContent(
        state = state,
        onSelectKc = viewModel::selectKc,
        onWater = viewModel::water,
        onHarvest = viewModel::harvest,
        onRefresh = viewModel::refresh,
        onDismissError = viewModel::dismissError,
    )
}

/** 无 ViewModel 依赖的内容层，便于 Preview 与截图测试复用。 */
@Composable
internal fun FieldsScreenContent(
    state: FieldsUiState,
    onSelectKc: (String?) -> Unit,
    onWater: (String) -> Unit,
    onHarvest: (String) -> Unit,
    onRefresh: () -> Unit,
    onDismissError: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FieldsHud(state = state)
            Box(modifier = Modifier.weight(1f)) {
                FieldsGrid(
                    state = state,
                    onTileClick = onSelectKc,
                )
            }
            FieldsActionBar(
                state = state,
                onWater = onWater,
                onHarvest = onHarvest,
                onCancel = { onSelectKc(null) },
                onRefresh = onRefresh,
                onDismissError = onDismissError,
            )
        }
    }
}

@Composable
internal fun FieldsGrid(
    state: FieldsUiState,
    onTileClick: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(items = state.tiles, key = { it.kcCode }) { tile ->
            KcTile(
                tile = tile,
                isSelected = state.selectedKc == tile.kcCode,
                isPending = state.pendingActionKc == tile.kcCode,
                onClick = { onTileClick(tile.kcCode) },
            )
        }
    }
}

@Composable
internal fun FieldsActionBar(
    state: FieldsUiState,
    onWater: (String) -> Unit,
    onHarvest: (String) -> Unit,
    onCancel: () -> Unit,
    onRefresh: () -> Unit,
    onDismissError: () -> Unit,
) {
    val selectedTile = state.tileFor(state.selectedKc)
    Surface(
        color = FieldsPalette.hudBackground,
        tonalElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(FOOTER_HEIGHT_DP.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectedTile == null) {
                IdleFooter(state = state, onRefresh = onRefresh)
            } else {
                SelectedFooter(
                    tile = selectedTile,
                    isBusy = state.isBusy,
                    onWater = { onWater(selectedTile.kcCode) },
                    onHarvest = { onHarvest(selectedTile.kcCode) },
                    onCancel = onCancel,
                    onDismissError = onDismissError,
                )
            }
        }
    }
}

@Composable
private fun RowScope.IdleFooter(state: FieldsUiState, onRefresh: () -> Unit) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "点一格 KC 浇水或收获",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "成熟 ${state.matureCount} · 幼苗 ${state.growingCount} · 已收 ${state.harvestedCount}",
            style = MaterialTheme.typography.bodySmall,
            color = FieldsPalette.hudAccent,
        )
    }
    OutlinedButton(onClick = onRefresh, enabled = !state.isBusy) {
        Text(if (state.isLoading) "拉取中..." else "刷新")
    }
}

@Composable
private fun RowScope.SelectedFooter(
    tile: KcTileState,
    isBusy: Boolean,
    onWater: () -> Unit,
    onHarvest: () -> Unit,
    onCancel: () -> Unit,
    onDismissError: () -> Unit,
) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = tile.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "${tile.kcCode} · 第 ${tile.chapterOrder} 章 · 掌握度 ${(tile.mastery * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = FieldsPalette.hudAccent,
        )
    }
    Button(
        onClick = onWater,
        enabled = !isBusy && tile.status != KcStatus.HARVESTED,
        colors = ButtonDefaults.buttonColors(containerColor = FieldsPalette.actionWater),
    ) {
        Text("浇水")
    }
    Button(
        onClick = onHarvest,
        enabled = !isBusy && tile.status == KcStatus.MATURE,
        colors = ButtonDefaults.buttonColors(
            containerColor = FieldsPalette.actionHarvest,
            contentColor = Color.Black,
        ),
    ) {
        Text("收获")
    }
    OutlinedButton(onClick = {
        onCancel()
        onDismissError()
    }) {
        Text("取消")
    }
}

const val GRID_COLUMNS: Int = 8
const val FOOTER_HEIGHT_DP: Int = 96
