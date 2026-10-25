package com.ahu.ahutong.next.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.legacy.toChinese
import com.ahu.ahutong.next.ui.components.SoftSurface
import com.ahu.ahutong.next.ui.theme.NextPalette

fun <T> AhuResult<T>.requireValue(): T = when (this) {
    is AhuResult.Success -> value
    is AhuResult.Failure -> throw IllegalStateException(error.toChinese())
}

@Composable
fun PageCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    SoftSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = 7.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
fun PageTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = NextPalette.Ink,
    )
}

@Composable
fun LabelValueRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = NextPalette.Ink,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.width(96.dp),
            style = MaterialTheme.typography.labelMedium,
            color = NextPalette.InkSecondary,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
        )
    }
}

@Composable
fun HorizontalDividerLine() {
    Spacer(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    )
}

fun Double.compact(): String =
    if (this % 1.0 == 0.0) this.toInt().toString() else String.format(java.util.Locale.CHINA, "%.1f", this)
