package com.ahu.ahutong.next.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 当前主题选择：要么跟随系统，要么记住手动选定的明暗。
 * 由 MainActivity 在启动时从设置里装载，切换时同步写回设置。
 */
object AppThemeState {
    var followSystem by mutableStateOf(true)
    var dark by mutableStateOf(false)
}
