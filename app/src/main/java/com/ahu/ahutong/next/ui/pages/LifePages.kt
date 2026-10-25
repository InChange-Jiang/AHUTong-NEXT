package com.ahu.ahutong.next.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.canteen.CanteenApi
import com.ahu.ahutong.data.canteen.InsightsResponse
import com.ahu.ahutong.data.crawler.model.adwnh.CampusItem
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundItem
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundPublishRequest
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundTypeItem
import com.ahu.ahutong.data.notice.CampusNotice
import com.ahu.ahutong.data.notice.CampusNoticeRepository
import com.ahu.ahutong.data.notice.CampusNoticeSnapshot
import com.ahu.ahutong.data.weather.WeatherApi
import com.ahu.ahutong.legacy.LegacyCampusApi
import com.ahu.ahutong.next.ui.components.AsyncContent
import com.ahu.ahutong.next.ui.components.EmptyState
import com.ahu.ahutong.next.ui.components.FeatureScaffold
import com.ahu.ahutong.next.ui.components.FeatureScrollColumn
import com.ahu.ahutong.next.ui.components.LoadState
import com.ahu.ahutong.next.ui.components.SoftChip
import com.ahu.ahutong.next.ui.components.SoftPrimaryButton
import com.ahu.ahutong.next.ui.components.SoftSecondaryButton
import com.ahu.ahutong.next.ui.components.toLoadFailure
import com.ahu.ahutong.next.ui.theme.NextPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val sourceLabels = mapOf(
    "university" to "安徽大学",
    "jwc" to "教务处",
    "postgraduate" to "研究生院",
    "college" to "学院",
)

@Composable
fun NoticesPage(onBack: () -> Unit) {
    val accountId = LegacyCampusApi.currentStudentId()
    val snapshotFlow = remember { CampusNoticeRepository.snapshot }
    val snapshot by snapshotFlow.collectAsStateWithLifecycle(initialValue = null)
    val syncing by CampusNoticeRepository.syncingAccountId.collectAsStateWithLifecycle(initialValue = null)
    var sourceFilter by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(reload, accountId) {
        if (!accountId.isNullOrBlank()) {
            CampusNoticeRepository.open(accountId)
            CampusNoticeRepository.requestSyncIfDue(force = reload > 0)
        }
    }

    FeatureScaffold(
        title = "校园通知",
        onBack = onBack,
        actions = {
            TextButton(onClick = { reload++ }) { Text("刷新") }
        },
    ) {
        if (accountId.isNullOrBlank()) {
            EmptyState("登录后可以同步教务处、学院与研究生院通知。")
            return@FeatureScaffold
        }
        val current = snapshot
        if (current == null || (current.notices.isEmpty() && syncing == accountId)) {
            LoadState.Loading.let { AsyncContent(it) {} }
            return@FeatureScaffold
        }
        val allSources = listOf("university", "jwc", "college", "postgraduate")
        val sources = (allSources + current.notices.map { it.sourceId }).distinct()
        val notices = current.notices
            .filter { sourceFilter == null || it.sourceId == sourceFilter }
            .sortedByDescending { it.publishedOn }
        FeatureScrollColumn {
            PageCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("未读 ${current.unreadCount} 条", style = MaterialTheme.typography.titleSmall, color = NextPalette.Ink)
                        Text(
                            "前台每日自动检查一次；来源：官网 / 教务处 / 学院 / 研究生院",
                            style = MaterialTheme.typography.labelSmall,
                            color = NextPalette.InkSecondary,
                        )
                    }
                    Switch(
                        checked = current.notificationsEnabled,
                        onCheckedChange = {
                            CampusNoticeRepository.setNotificationsEnabled(accountId, it)
                        },
                    )
                }
            }
            val sourceErrors = current.sourceStatuses.mapNotNull { (id, status) ->
                val error = status.lastError ?: status.historyError
                error?.takeIf { it.isNotBlank() }?.let { "${sourceLabels[id] ?: id}：$it" }
            }
            if (sourceErrors.isNotEmpty()) {
                PageCard {
                    Text("部分来源同步失败", style = MaterialTheme.typography.labelSmall, color = NextPalette.Warning)
                    sourceErrors.forEach {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = NextPalette.InkSecondary)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SoftChip("全部", onClick = { sourceFilter = null }, selected = sourceFilter == null)
                sources.forEach { source ->
                    SoftChip(
                        sourceLabels[source] ?: source,
                        onClick = { sourceFilter = source },
                        selected = sourceFilter == source,
                    )
                }
            }
            if (notices.isEmpty()) {
                EmptyState("暂无通知。")
            } else {
                PageCard {
                    notices.forEachIndexed { index, notice ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    notice.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NextPalette.Ink,
                                    fontWeight = if (notice.read) FontWeight.Normal else FontWeight.SemiBold,
                                )
                                Text(
                                    "${sourceLabels[notice.sourceId] ?: notice.sourceId} · ${notice.publishedOn}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NextPalette.InkSecondary,
                                )
                            }
                            TextButton(onClick = {
                                CampusNoticeRepository.markRead(accountId, notice.articleId)
                                uriHandler.openUri(notice.originalUrl)
                            }) { Text("查看") }
                        }
                        if (index != notices.lastIndex) HorizontalDivider(color = NextPalette.Line)
                    }
                }
                val status = current.sourceStatuses[sourceFilter]
                if (status?.nextPageUrl != null && !status.historyExhausted) {
                    TextButton(onClick = {
                        CampusNoticeRepository.requestLoadMore(sourceFilter)
                        scope.launch { }
                    }) { Text("加载更早") }
                }
            }
        }
    }
}

@Composable
fun LostFoundPage(onBack: () -> Unit) {
    var items by remember { mutableStateOf<List<LostFoundItem>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(1) }
    var state by remember { mutableStateOf<LoadState<Unit>>(LoadState.Loading) }
    var showPublish by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var deleting by remember { mutableStateOf<LostFoundItem?>(null) }
    var filterState by remember { mutableIntStateOf(1) }
    var types by remember { mutableStateOf<List<LostFoundTypeItem>>(emptyList()) }
    var campuses by remember { mutableStateOf<List<CampusItem>>(emptyList()) }
    var typeFilter by remember { mutableStateOf<String?>(null) }
    var campusFilter by remember { mutableStateOf<String?>(null) }
    var myPostsOnly by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun load(target: Int, append: Boolean) {
        val result = runCatching {
            withContext(Dispatchers.IO) {
                AHURepository.getLostFoundList(target, 20, state = filterState).requireValue().data
            }
        }
        result.fold(
            onSuccess = {
                total = it.total
                items = if (append) items + it.list else it.list
                page = target
                state = LoadState.Ready(Unit)
            },
            onFailure = { state = it.toLoadFailure() },
        )
    }

    LaunchedEffect(reload, filterState) {
        state = LoadState.Loading
        load(1, false)
    }

    LaunchedEffect(Unit) {
        runCatching {
            withContext(Dispatchers.IO) {
                AHURepository.getAllLostFoundType().requireValue().`object` to
                    AHURepository.getAllCampus().requireValue().`object`
            }
        }.onSuccess { (typeList, campusList) ->
            types = typeList
            campuses = campusList
        }
    }

    FeatureScaffold(
        title = "失物招领",
        onBack = onBack,
        actions = {
            TextButton(onClick = { showPublish = true }) { Text("发布") }
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SoftChip("失物招领", onClick = { filterState = 1 }, selected = filterState == 1)
            SoftChip("寻物启事", onClick = { filterState = 2 }, selected = filterState == 2)
        }
        AsyncContent(state, onRetry = { reload++ }) {
            val visible = items.filter { item ->
                (typeFilter == null || item.typeid == typeFilter) &&
                    (campusFilter == null || item.campusid == campusFilter) &&
                    (!myPostsOnly || item.createuser == LegacyCampusApi.currentUserName())
            }
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SoftChip("全部校区", onClick = { campusFilter = null }, selected = campusFilter == null)
                    campuses.forEach { campus ->
                        SoftChip(campus.campusName, onClick = { campusFilter = campus.id }, selected = campusFilter == campus.id)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SoftChip("全部类型", onClick = { typeFilter = null }, selected = typeFilter == null)
                    types.forEach { type ->
                        SoftChip(type.typeName, onClick = { typeFilter = type.typeId }, selected = typeFilter == type.typeId)
                    }
                    SoftChip("我的发布", onClick = { myPostsOnly = !myPostsOnly }, selected = myPostsOnly)
                }
            }
            if (visible.isEmpty()) EmptyState("暂无信息。") else FeatureScrollColumn {
                Text("共 ${visible.size} 条", style = MaterialTheme.typography.labelSmall, color = NextPalette.InkSecondary)
                visible.forEach { item ->
                    PageCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                item.title,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleSmall,
                                color = NextPalette.Ink,
                            )
                            Text(
                                item.lostType?.typeName ?: "其他",
                                style = MaterialTheme.typography.labelSmall,
                                color = NextPalette.AccentDeep,
                            )
                        }
                        Text(
                            "${item.campusName.orEmpty()} · ${item.createtime.orEmpty()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NextPalette.InkSecondary,
                        )
                        Text(
                            "联系人 ${item.linkman.orEmpty()} · ${item.phone.orEmpty()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NextPalette.InkSecondary,
                        )
                        if (item.createuser == LegacyCampusApi.currentUserName()) {
                            TextButton(onClick = { deleting = item }) { Text("删除这条") }
                        }
                    }
                }
                if (items.size < total) {
                    TextButton(onClick = {
                        scope.launch {
                            state = LoadState.Loading
                            load(page + 1, true)
                        }
                    }) { Text("加载更多") }
                }
            }
        }
    }

    if (showPublish) {
        PublishLostFoundDialog(
            defaultTypeState = filterState.toString(),
            onDismiss = { showPublish = false },
            onPublished = {
                showPublish = false
                reload++
            },
        )
    }

    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除这条信息？") },
            text = { Text(item.title) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                AHURepository.deleteLostFound(item.id).requireValue()
                            }
                        }
                        reload++
                    }
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun PublishLostFoundDialog(
    defaultTypeState: String,
    onDismiss: () -> Unit,
    onPublished: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var linkman by remember { mutableStateOf(LegacyCampusApi.currentUserName().orEmpty()) }
    var phone by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var publishState by remember { mutableStateOf(defaultTypeState) }
    var types by remember { mutableStateOf<List<LostFoundTypeItem>>(emptyList()) }
    var campuses by remember { mutableStateOf<List<CampusItem>>(emptyList()) }
    var typeId by remember { mutableStateOf<String?>(null) }
    var campusId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching {
            withContext(Dispatchers.IO) {
                val typeResult = AHURepository.getAllLostFoundType().requireValue().`object`
                val campusResult = AHURepository.getAllCampus().requireValue().`object`
                typeResult to campusResult
            }
        }.onSuccess { (typeResult, campusResult) ->
            types = typeResult
            campuses = campusResult
            typeId = typeResult.firstOrNull()?.typeId
            campusId = campusResult.firstOrNull()?.id
        }.onFailure { error = it.message }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发布失物招领") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectField(
                        label = "类型",
                        value = types.firstOrNull { it.typeId == typeId }?.typeName ?: "选择",
                        options = types.map { it.typeId to it.typeName },
                        onSelected = { typeId = it },
                        modifier = Modifier.weight(1f),
                    )
                    SelectField(
                        label = "校区",
                        value = campuses.firstOrNull { it.id == campusId }?.campusName ?: "选择",
                        options = campuses.map { it.id to it.campusName },
                        onSelected = { campusId = it },
                        modifier = Modifier.weight(1f),
                    )
                }
                SelectField(
                    label = "类别",
                    value = if (publishState == "1") "失物招领" else "寻物启事",
                    options = listOf("1" to "失物招领", "2" to "寻物启事"),
                    onSelected = { publishState = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = linkman,
                    onValueChange = { linkman = it },
                    label = { Text("联系人") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.filter(Char::isDigit).take(11) },
                    label = { Text("手机号") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述（可选）") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = NextPalette.Danger)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !submitting &&
                    title.isNotBlank() && linkman.isNotBlank() && phone.length == 11 &&
                    typeId != null && campusId != null,
                onClick = {
                    submitting = true
                    error = null
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                AHURepository.publishLostFound(
                                    LostFoundPublishRequest(
                                        imgs = emptyList(),
                                        linkman = linkman,
                                        phone = phone,
                                        typeid = typeId!!,
                                        num1 = description.takeIf { it.isNotBlank() },
                                        campusid = campusId!!,
                                        title = title,
                                        state = publishState,
                                    ),
                                ).requireValue()
                            }
                        }.fold(
                            onSuccess = { onPublished() },
                            onFailure = { error = it.message ?: "发布失败" },
                        )
                        submitting = false
                    }
                },
            ) { Text(if (submitting) "发布中…" else "发布") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun SelectField(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            trailingIcon = {
                TextButton(onClick = { expanded = true }) { Text("选择") }
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        expanded = false
                        onSelected(id)
                    },
                )
            }
        }
    }
}

@Composable
fun WeatherPage(onBack: () -> Unit) {
    var state by remember { mutableStateOf<LoadState<com.ahu.ahutong.data.weather.WeatherResponse>>(LoadState.Loading) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) {
        state = LoadState.Loading
        state = runCatching {
            withContext(Dispatchers.IO) {
                WeatherApi.API.getWeather(city = "合肥", extended = true, indices = false, forecast = true, hourly = false)
            }
        }.fold(onSuccess = { LoadState.Ready(it) }, onFailure = { it.toLoadFailure() })
    }
    FeatureScaffold(
        title = "天气",
        onBack = onBack,
        actions = { TextButton(onClick = { reload++ }) { Text("刷新") } },
    ) {
        AsyncContent(state, onRetry = { reload++ }) {
            val weather = (state as LoadState.Ready).value
            FeatureScrollColumn {
                PageCard {
                    Text(
                        "${weather.city.orEmpty()} ${weather.district.orEmpty()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = NextPalette.InkSecondary,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            weather.temperature?.let { "${it.toInt()}°" } ?: "--",
                            style = MaterialTheme.typography.displaySmall,
                            color = NextPalette.AccentDeep,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            weather.weather.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            color = NextPalette.Ink,
                        )
                    }
                    Text(
                        "体感 ${weather.feelsLike?.let { "${it.toInt()}°" } ?: "--"} · 湿度 ${weather.humidity?.toString() ?: "--"}% · ${weather.windDirection.orEmpty()}${weather.windPower.orEmpty()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = NextPalette.InkSecondary,
                    )
                    Text(
                        "AQI ${weather.aqi?.toString() ?: "--"} ${weather.aqiCategory.orEmpty()} · ${weather.tempMin?.let { "%.0f".format(Locale.CHINA, it) } ?: "--"}~${weather.tempMax?.let { "%.0f".format(Locale.CHINA, it) } ?: "--"}°",
                        style = MaterialTheme.typography.labelSmall,
                        color = NextPalette.InkSecondary,
                    )
                    Text(
                        "更新于 ${weather.reportTime.orEmpty()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = NextPalette.InkSecondary,
                    )
                }
                weather.alerts.orEmpty().forEach { alert ->
                    PageCard {
                        Text(
                            alert.title ?: "天气预警",
                            style = MaterialTheme.typography.titleSmall,
                            color = NextPalette.Danger,
                        )
                        Text(
                            alert.text.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = NextPalette.InkSecondary,
                        )
                    }
                }
                weather.forecast.orEmpty().take(3).let { forecast ->
                    if (forecast.isNotEmpty()) {
                        PageCard {
                            PageTitle("未来预报")
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                forecast.forEach { day ->
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            day.week ?: day.date.orEmpty(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NextPalette.InkSecondary,
                                        )
                                        Text(
                                            day.weatherDay.orEmpty(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = NextPalette.Ink,
                                        )
                                        Text(
                                            "${day.tempMin?.toInt() ?: "--"}° / ${day.tempMax?.toInt() ?: "--"}°",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NextPalette.InkSecondary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CanteenPage(onBack: () -> Unit) {
    var period by remember { mutableStateOf("week") }
    var campus by remember { mutableStateOf("all") }
    val campusFilters = linkedMapOf(
        "all" to null,
        "磬苑" to "榴园,桔园,梅园,桂园,蕙园食堂,梧桐园食堂",
        "龙河" to "本部二食堂,本部三食堂,二食堂餐厅,三食堂,外招餐厅",
        "金寨路" to "江淮学院食堂",
    )
    var state by remember { mutableStateOf<LoadState<InsightsResponse>>(LoadState.Loading) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(period, campus, reload) {
        state = LoadState.Loading
        state = runCatching {
            withContext(Dispatchers.IO) {
                CanteenApi.API.insights(
                    period = period,
                    limit = 30,
                    canteens = campusFilters[campus],
                    minTxns = 20,
                )
            }
        }.fold(onSuccess = { LoadState.Ready(it) }, onFailure = { it.toLoadFailure() })
    }
    FeatureScaffold(
        title = "食堂榜单",
        onBack = onBack,
        actions = { TextButton(onClick = { reload++ }) { Text("刷新") } },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SoftChip("本周", onClick = { period = "week" }, selected = period == "week")
            SoftChip("本月", onClick = { period = "month" }, selected = period == "month")
            SoftChip("全部", onClick = { period = "all" }, selected = period == "all")
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            campusFilters.keys.forEach { key ->
                SoftChip(
                    if (key == "all") "全部" else key,
                    onClick = { campus = key },
                    selected = campus == key,
                )
            }
        }
        AsyncContent(state, onRetry = { reload++ }) {
            val data = (state as LoadState.Ready).value
            FeatureScrollColumn {
                val winner = data.superlatives?.topWindow ?: data.ranking?.firstOrNull()
                winner?.let { item ->
                    PageCard {
                        Text("本周冠军", style = MaterialTheme.typography.labelSmall, color = NextPalette.AccentDeep)
                        Text(
                            item.name ?: "未命名窗口",
                            style = MaterialTheme.typography.titleLarge,
                            color = NextPalette.Ink,
                        )
                        Text(
                            "${listOfNotNull(item.canteen, item.floor, "${item.txns} 笔").joinToString(" · ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NextPalette.InkSecondary,
                        )
                    }
                }
                PageCard {
                    Text(
                        "上榜窗口 ${data.ranking?.size ?: 0} 个 · 样本 ${data.sample?.txns ?: 0} 笔",
                        style = MaterialTheme.typography.labelSmall,
                        color = NextPalette.InkSecondary,
                    )
                    data.ranking.orEmpty().take(20).forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${item.rank}",
                                modifier = Modifier.width(28.dp),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (item.rank <= 3) NextPalette.AccentDeep else NextPalette.InkSecondary,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.name ?: "未命名窗口",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NextPalette.Ink,
                                )
                                Text(
                                    listOfNotNull(item.canteen, item.floor, item.terminal).joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NextPalette.InkSecondary,
                                )
                            }
                            Text(
                                buildString {
                                    append("${item.txns} 笔")
                                    item.avgCentsPerTxn?.let { append(" · 人均 ¥%.1f".format(Locale.CHINA, it / 100.0)) }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = NextPalette.InkSecondary,
                            )
                        }
                    }
                }
                data.notes?.takeIf { it.isNotEmpty() }?.let { notes ->
                    PageCard {
                        notes.forEach {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = NextPalette.InkSecondary)
                        }
                    }
                }
            }
        }
    }
}
