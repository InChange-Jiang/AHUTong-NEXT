package com.ahu.ahutong.next.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.model.CampusDataItem
import com.ahu.ahutong.data.model.ElectricityController
import com.ahu.ahutong.data.model.RoomSelectionInfo
import com.ahu.ahutong.legacy.LegacyCampusApi
import com.ahu.ahutong.legacy.toChinese
import com.ahu.ahutong.next.ui.theme.NextPalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 电费查询必须先选一次房间：上游只接受「校区/楼栋/楼层/房间」的选项 ID，
 * 选择结果由旧数据层持久化，之后的电费工具直接复用。
 */
@Composable
fun ElectricityRoomDialog(
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val saved = remember { LegacyCampusApi.savedElectricityRoom() }
    var controller by remember { mutableStateOf(saved?.controller ?: LegacyCampusApi.electricityController()) }
    var campus by remember { mutableStateOf(saved?.campus) }
    var building by remember { mutableStateOf(saved?.building) }
    var floor by remember { mutableStateOf(saved?.floor) }
    var room by remember { mutableStateOf(saved?.room) }
    var campusOptions by remember { mutableStateOf(emptyList<CampusDataItem>()) }
    var buildingOptions by remember { mutableStateOf(emptyList<CampusDataItem>()) }
    var floorOptions by remember { mutableStateOf(emptyList<CampusDataItem>()) }
    var roomOptions by remember { mutableStateOf(emptyList<CampusDataItem>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun clearBelow(level: Int) {
        if (level <= 0) { campus = null; campusOptions = emptyList() }
        if (level <= 1) { building = null; buildingOptions = emptyList() }
        if (level <= 2) { floor = null; floorOptions = emptyList() }
        room = null
        roomOptions = emptyList()
    }

    suspend fun failOrThrow(block: suspend () -> Unit) {
        loading = true
        error = null
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "加载房间选项失败"
        } finally {
            loading = false
        }
    }

    suspend fun loadRooms(c: ElectricityController, restore: RoomSelectionInfo?) {
        val base = RoomSelectionInfo(campus, building, floor, null, c)
        roomOptions = LegacyCampusApi.electricityRooms(c, base).valueOrThrow()
        room = restore?.room?.let { target -> roomOptions.firstOrNull { it.value == target.value } }
    }

    suspend fun loadFloors(c: ElectricityController, restore: RoomSelectionInfo?) {
        val base = RoomSelectionInfo(campus, building, null, null, c)
        floorOptions = LegacyCampusApi.electricityFloors(c, base).valueOrThrow()
        floor = restore?.floor?.let { target -> floorOptions.firstOrNull { it.value == target.value } }
            ?: floorOptions.firstOrNull()
        if (floor != null) loadRooms(c, restore)
    }

    suspend fun loadBuildings(c: ElectricityController, restore: RoomSelectionInfo?) {
        val selectedCampus = campus ?: return
        buildingOptions = LegacyCampusApi.electricityBuildings(c, selectedCampus).valueOrThrow()
        building = restore?.building?.let { target -> buildingOptions.firstOrNull { it.value == target.value } }
            ?: buildingOptions.firstOrNull()
        if (building != null) loadFloors(c, restore)
    }

    suspend fun loadInitial(c: ElectricityController, restore: RoomSelectionInfo?) {
        val initial = LegacyCampusApi.electricityInitialOptions(c).valueOrThrow()
        if (c.requiresCampus) {
            campusOptions = initial
            campus = restore?.campus?.let { target -> initial.firstOrNull { it.value == target.value } }
            if (campus != null) loadBuildings(c, restore)
        } else {
            buildingOptions = initial
            building = restore?.building?.let { target -> initial.firstOrNull { it.value == target.value } }
            if (building != null) loadFloors(c, restore)
        }
    }

    LaunchedEffect(Unit) {
        failOrThrow { loadInitial(controller, saved) }
    }

    val selection = RoomSelectionInfo(campus, building, floor, room, controller)
    val ready = room != null && building != null && floor != null &&
        (!controller.requiresCampus || campus != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("宿舍电费房间") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "电费系统按房间查余额。选一次后会记住，聊天里查电费直接用这个房间。",
                    style = MaterialTheme.typography.bodySmall,
                    color = NextPalette.InkSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ElectricityController.entries.forEach { item ->
                        FilterChip(
                            selected = controller == item,
                            onClick = {
                                if (controller != item) {
                                    controller = item
                                    clearBelow(0)
                                    scope.launch { failOrThrow { loadInitial(item, null) } }
                                }
                            },
                            label = { Text(item.displayName) },
                        )
                    }
                }
                if (controller.requiresCampus) {
                    OptionRow(
                        label = "校区",
                        value = campus?.name,
                        options = campusOptions,
                        enabled = !loading,
                    ) { picked ->
                        campus = picked
                        clearBelow(1)
                        scope.launch { failOrThrow { loadBuildings(controller, null) } }
                    }
                }
                OptionRow(
                    label = "楼栋",
                    value = building?.name,
                    options = buildingOptions,
                    enabled = !loading,
                ) { picked ->
                    building = picked
                    clearBelow(2)
                    scope.launch { failOrThrow { loadFloors(controller, null) } }
                }
                OptionRow(
                    label = "楼层",
                    value = floor?.name,
                    options = floorOptions,
                    enabled = !loading && building != null,
                ) { picked ->
                    floor = picked
                    clearBelow(3)
                    scope.launch { failOrThrow { loadRooms(controller, null) } }
                }
                OptionRow(
                    label = "房间",
                    value = room?.name,
                    options = roomOptions,
                    enabled = !loading && floor != null,
                ) { picked -> room = picked }

                if (loading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = NextPalette.Accent,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "正在读取选项…",
                            style = MaterialTheme.typography.labelSmall,
                            color = NextPalette.InkSecondary,
                        )
                    }
                }
                error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = NextPalette.Danger,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = ready && !loading,
                onClick = {
                    LegacyCampusApi.saveElectricitySelection(controller, selection)
                    onSaved(selection.displayLabel())
                    onDismiss()
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun OptionRow(
    label: String,
    value: String?,
    options: List<CampusDataItem>,
    enabled: Boolean,
    onSelect: (CampusDataItem) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(15.dp))
                .background(NextPalette.SurfaceSoft)
                .clickable(enabled = enabled) { expanded = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) NextPalette.Ink else NextPalette.InkSecondary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                value ?: "请选择",
                style = MaterialTheme.typography.bodyMedium,
                color = if (value != null) NextPalette.Ink else NextPalette.InkSecondary,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (options.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("暂无可选项") },
                    onClick = { expanded = false },
                    enabled = false,
                )
            } else {
                options.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item.name) },
                        onClick = {
                            expanded = false
                            onSelect(item)
                        },
                    )
                }
            }
        }
    }
}

fun RoomSelectionInfo.displayLabel(): String =
    listOfNotNull(campus?.name, building?.name, floor?.name, room?.name).joinToString(" ")

private fun <T> AhuResult<T>.valueOrThrow(): T = when (this) {
    is AhuResult.Success -> value
    is AhuResult.Failure -> throw IllegalStateException(error.toChinese())
}
