package com.ahu.ahutong.next.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.graphics.Color
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
    val tint: Color? = null,
)

private data class DrawerGroup(
    val title: String,
    val entries: List<DrawerEntry>,
)

private val groups = listOf(
    DrawerGroup(
        "教务",
        listOf(
            DrawerEntry("成绩单", AppRoute.Grades, R.drawable.ic_grade, Color(0xFFFFC107)),
            DrawerEntry("考场查询", AppRoute.Exams, R.drawable.ic_exam, Color(0xFF4CAF50)),
            DrawerEntry("教评", AppRoute.Evaluation, R.drawable.ic_evaluation, Color(0xFF0D9488)),
            DrawerEntry("培养方案", AppRoute.Completion, R.drawable.ic_nav_degree_hat, Color(0xFF009688)),
            DrawerEntry("教务考勤", AppRoute.Attendance, R.drawable.ic_attendance, Color(0xFF3F51B5)),
            DrawerEntry("空闲教室", AppRoute.FreeRooms, R.drawable.ic_round_business_24, Color(0xFF03A9F4)),
            DrawerEntry("校历", AppRoute.SchoolCalendar, R.drawable.ic_schedule, Color(0xFF9C27B0)),
        ),
    ),
    DrawerGroup(
        "学习",
        listOf(
            DrawerEntry("课程任务", AppRoute.CourseTasks, R.drawable.ic_iconpark_assignment, Color(0xFF8B1E3F)),
            DrawerEntry("学习资料", AppRoute.Materials, R.drawable.ic_repository, Color(0xFF8D6E63)),
            DrawerEntry("学生邮箱", AppRoute.Mail, R.drawable.ic_student_mail, Color(0xFF3978D4)),
        ),
    ),
    DrawerGroup(
        "生活",
        listOf(
            DrawerEntry("电控缴费", AppRoute.RechargeElectricity, R.drawable.ic_electricity_pay, Color(0xFFFFB300)),
            DrawerEntry("浴室缴费", AppRoute.RechargeBathroom, R.drawable.ic_bathroom_pay, Color(0xFF26A69A)),
            DrawerEntry("网费充值", AppRoute.RechargeNetwork, R.drawable.ic_network_recharge, Color(0xFF1E88E5)),
            DrawerEntry("必吃榜", AppRoute.Canteen, R.drawable.ic_canteen_ranking, Color(0xFFFF6633)),
            DrawerEntry("失物招领", AppRoute.LostFound, R.drawable.lost_and_found, Color(0xFF1976D2)),
            DrawerEntry("电话本", AppRoute.PhoneBook, R.drawable.ic_phonebook, Color(0xFF009688)),
            DrawerEntry("校园通知", AppRoute.Notices, R.drawable.ic_campus_notice, Color(0xFF1976D2)),
            DrawerEntry("天气", AppRoute.Weather, R.drawable.ic_weather, Color(0xFFFFB300)),
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
        modifier = Modifier.width(280.dp).fillMaxHeight(),
        drawerContainerColor = NextPalette.Background,
    ) {
        Box(modifier = Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 104.dp),
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
            DrawerRow(
                label = "历史对话",
                icon = R.drawable.ic_iconpark_clock,
                selected = current == AppRoute.History,
                onClick = { onNavigate(AppRoute.History) },
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
                        tint = entry.tint,
                        selected = current == entry.route,
                        onClick = { onNavigate(entry.route) },
                    )
                }
            }

        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    if (current == AppRoute.Settings) NextPalette.AccentSoft
                    else NextPalette.Surface,
                )
                .clickable { onNavigate(AppRoute.Settings) }
                .padding(horizontal = 18.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_iconpark_setting),
                contentDescription = null,
                tint = if (current == AppRoute.Settings) NextPalette.AccentDeep else NextPalette.InkSecondary,
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "设置",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (current == AppRoute.Settings) NextPalette.AccentDeep else NextPalette.Ink,
            )
        }
        }
    }
}

@Composable
private fun DrawerRow(
    label: String,
    icon: Int,
    selected: Boolean,
    onClick: () -> Unit,
    tint: Color? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) NextPalette.AccentSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint ?: if (selected) NextPalette.AccentDeep else NextPalette.InkSecondary,
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
