package com.ahu.ahutong.next.ui.pages

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ahu.ahutong.next.BuildConfig
import com.ahu.ahutong.next.app.data.NextCampusRepository
import com.ahu.ahutong.next.reminder.CourseReminders
import com.ahu.ahutong.next.ui.components.FeatureScaffold
import com.ahu.ahutong.next.ui.components.FeatureScrollColumn
import com.ahu.ahutong.next.ui.components.SoftChip
import com.ahu.ahutong.next.ui.components.SoftPrimaryButton
import com.ahu.ahutong.next.ui.components.SoftSecondaryButton
import com.ahu.ahutong.next.ui.theme.NextPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RemindersPage(
    repository: NextCampusRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(CourseReminders.isEnabled(context)) }
    var minutes by remember { mutableIntStateOf(CourseReminders.minutesBefore(context)) }
    var electricityEnabled by remember { mutableStateOf(CourseReminders.electricityEnabled(context)) }
    var threshold by remember { mutableIntStateOf(CourseReminders.electricityThreshold(context)) }
    var permissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> permissionDenied = !granted }

    fun persist() {
        CourseReminders.save(context, enabled, minutes, electricityEnabled, threshold)
        CourseReminders.schedule(context, repository)
    }

    FeatureScaffold("提醒", onBack) {
        FeatureScrollColumn {
            PageCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "上课提醒",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        color = NextPalette.Ink,
                    )
                    Switch(
                        checked = enabled,
                        onCheckedChange = { checked ->
                            enabled = checked
                            if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS,
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            persist()
                        },
                    )
                }
                Text(
                    "课前 ${minutes} 分钟通知，按未来 7 天课表自动排程；重启手机后会自动恢复。",
                    style = MaterialTheme.typography.labelSmall,
                    color = NextPalette.InkSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15, 30).forEach { value ->
                        SoftChip(
                            "$value 分钟",
                            onClick = { minutes = value; persist() },
                            selected = minutes == value,
                        )
                    }
                }
                if (permissionDenied) {
                    Text(
                        "通知权限未开启，提醒不会显示。可在系统设置里允许通知。",
                        style = MaterialTheme.typography.labelSmall,
                        color = NextPalette.Danger,
                    )
                }
            }
            PageCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "电费提醒",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        color = NextPalette.Ink,
                    )
                    Switch(
                        checked = electricityEnabled,
                        onCheckedChange = { electricityEnabled = it; persist() },
                    )
                }
                Text(
                    "进入 App 时检查一次；预计可用天数不高于阈值时提醒，同一房间每天最多一次。",
                    style = MaterialTheme.typography.labelSmall,
                    color = NextPalette.InkSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 5, 7, 10).forEach { value ->
                        SoftChip(
                            "$value 天",
                            onClick = { threshold = value; persist() },
                            selected = threshold == value,
                        )
                    }
                }
            }
            PageCard {
                SoftSecondaryButton(
                    "立即按当前设置重排课程提醒",
                    onClick = { CourseReminders.schedule(context, repository) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "提醒使用本地通知，不依赖云端；课表变化后请重新排程。",
                    style = MaterialTheme.typography.labelSmall,
                    color = NextPalette.InkSecondary,
                )
            }
        }
    }
}

@Composable
fun UpdatesPage(onBack: () -> Unit) {
    FeatureScaffold("应用更新", onBack) {
        FeatureScrollColumn {
            PageCard {
                Text("当前版本", style = MaterialTheme.typography.labelSmall, color = NextPalette.InkSecondary)
                Text(
                    "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.headlineSmall,
                    color = NextPalette.Ink,
                )
                Text(
                    "build ${BuildConfig.BUILD_TYPE}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NextPalette.InkSecondary,
                )
            }
            PageCard {
                Text("更新方式", style = MaterialTheme.typography.titleSmall, color = NextPalette.Ink)
                Text(
                    "NEXT 目前走团队内测分发，APK 从内测渠道获取后覆盖安装即可，数据会保留。",
                    style = MaterialTheme.typography.bodySmall,
                    color = NextPalette.InkSecondary,
                )
                Text(
                    "自动检查更新需要配置正式发布通道，接入后这里会显示版本号和更新日志。",
                    style = MaterialTheme.typography.labelSmall,
                    color = NextPalette.InkSecondary,
                )
            }
        }
    }
}
