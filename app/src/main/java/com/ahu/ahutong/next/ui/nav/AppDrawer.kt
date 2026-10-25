package com.ahu.ahutong.next.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.legacy.LegacyCampusApi
import com.ahu.ahutong.next.R
import com.ahu.ahutong.next.ui.components.AgentAvatar
import com.ahu.ahutong.next.ui.theme.NextPalette

private data class DrawerEntry(
    val label: String,
    val route: AppRoute,
    val icon: Int,
)

private data class DrawerGroup(
    val title: String,
    val entries: List<DrawerEntry>,
)

private val groups = listOf(
    DrawerGroup(
        "教务",
        listOf(
            DrawerEntry("课表", AppRoute.Schedule, R.drawable.ic_iconpark_calendar),
            DrawerEntry("成绩", AppRoute.Grades, R.drawable.ic_iconpark_grades),
            DrawerEntry("考试", AppRoute.Exams, R.drawable.ic_iconpark_exam),
            DrawerEntry("评教", AppRoute.Evaluation, R.drawable.ic_iconpark_assignment),
            DrawerEntry("培养方案", AppRoute.Completion, R.drawable.ic_iconpark_book),
            DrawerEntry("考勤", AppRoute.Attendance, R.drawable.ic_iconpark_clock),
            DrawerEntry("空闲教室", AppRoute.FreeRooms, R.drawable.ic_iconpark_search),
            DrawerEntry("校历", AppRoute.SchoolCalendar, R.drawable.ic_iconpark_calendar),
        ),
    ),
    DrawerGroup(
        "学习",
        listOf(
            DrawerEntry("作业", AppRoute.Assignments, R.drawable.ic_iconpark_assignment),
            DrawerEntry("课程任务", AppRoute.CourseTasks, R.drawable.ic_iconpark_assignment),
            DrawerEntry("学习资料", AppRoute.Materials, R.drawable.ic_iconpark_book),
            DrawerEntry("学生邮箱", AppRoute.Mail, R.drawable.ic_iconpark_mail),
            DrawerEntry("我的日程", AppRoute.CalendarEvents, R.drawable.ic_iconpark_calendar),
        ),
    ),
    DrawerGroup(
        "校园卡",
        listOf(
            DrawerEntry("校园卡", AppRoute.Card, R.drawable.ic_iconpark_wallet),
            DrawerEntry("账单与消费", AppRoute.Bills, R.drawable.ic_iconpark_bill),
            DrawerEntry("校园卡充值", AppRoute.RechargeCard, R.drawable.ic_iconpark_wallet),
        ),
    ),
    DrawerGroup(
        "生活",
        listOf(
            DrawerEntry("电费", AppRoute.Electricity, R.drawable.ic_iconpark_electricity),
            DrawerEntry("电费充值", AppRoute.RechargeElectricity, R.drawable.ic_iconpark_electricity),
            DrawerEntry("浴室缴费", AppRoute.RechargeBathroom, R.drawable.ic_iconpark_wallet),
            DrawerEntry("网费充值", AppRoute.RechargeNetwork, R.drawable.ic_iconpark_wallet),
            DrawerEntry("食堂", AppRoute.Canteen, R.drawable.ic_iconpark_grades),
            DrawerEntry("失物招领", AppRoute.LostFound, R.drawable.ic_iconpark_search),
            DrawerEntry("电话本", AppRoute.PhoneBook, R.drawable.ic_iconpark_user),
            DrawerEntry("校园通知", AppRoute.Notices, R.drawable.ic_iconpark_bell),
            DrawerEntry("天气", AppRoute.Weather, R.drawable.ic_iconpark_weather),
        ),
    ),
    DrawerGroup(
        "系统",
        listOf(
            DrawerEntry("偏好设置", AppRoute.Preferences, R.drawable.ic_iconpark_setting),
            DrawerEntry("提醒", AppRoute.Reminders, R.drawable.ic_iconpark_bell),
            DrawerEntry("外观与显示", AppRoute.Appearance, R.drawable.ic_iconpark_setting),
            DrawerEntry("账号与资料", AppRoute.Accounts, R.drawable.ic_iconpark_user),
            DrawerEntry("应用更新", AppRoute.Updates, R.drawable.ic_iconpark_info),
            DrawerEntry("设置", AppRoute.Settings, R.drawable.ic_iconpark_setting),
            DrawerEntry("关于", AppRoute.About, R.drawable.ic_iconpark_info),
        ),
    ),
)

@Composable
fun AppDrawer(
    nav: AppNavState,
    current: AppRoute,
    onNewChat: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
) {
    val name = LegacyCampusApi.currentUserName()
    val studentId = LegacyCampusApi.currentStudentId()

    ModalDrawerSheet(
        modifier = Modifier.width(318.dp).fillMaxHeight(),
        drawerContainerColor = NextPalette.Background,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(NextPalette.Surface)
                    .clickable { onNavigate(AppRoute.Profile) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AgentAvatar(size = 46.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        name?.takeIf { it.isNotBlank() } ?: "未登录",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = NextPalette.Ink,
                    )
                    Text(
                        studentId?.takeIf { it.isNotBlank() } ?: "点此登录或查看资料",
                        style = MaterialTheme.typography.labelSmall,
                        color = NextPalette.InkSecondary,
                    )
                }
            }

            DrawerRow(
                label = "新建对话",
                icon = R.drawable.ic_iconpark_plus,
                selected = false,
                onClick = onNewChat,
            )

            groups.forEach { group ->
                Text(
                    group.title,
                    modifier = Modifier.padding(start = 10.dp, top = 12.dp, bottom = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = NextPalette.InkSecondary,
                )
                group.entries.forEach { entry ->
                    DrawerRow(
                        label = entry.label,
                        icon = entry.icon,
                        selected = current == entry.route,
                        onClick = { onNavigate(entry.route) },
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = NextPalette.Line,
            )
            Text(
                "安大通 NEXT · 对话是入口，页面是兜底",
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 12.dp),
                style = MaterialTheme.typography.labelSmall,
                color = NextPalette.InkSecondary,
            )
        }
    }
}

@Composable
private fun DrawerRow(
    label: String,
    icon: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) NextPalette.AccentSoft else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (selected) NextPalette.AccentDeep else NextPalette.InkSecondary,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) NextPalette.AccentDeep else NextPalette.Ink,
        )
    }
}
