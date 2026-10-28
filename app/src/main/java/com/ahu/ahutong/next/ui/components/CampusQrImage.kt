package com.ahu.ahutong.next.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.next.ui.theme.NextPalette
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/** 付款码二维码；内容只在内存里生成，不落盘。 */
@Composable
fun CampusQrImage(
    payload: String,
    modifier: Modifier = Modifier,
    size: Dp = 260.dp,
) {
    val bitmap = remember(payload) {
        runCatching {
            val pixels = 720
            val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, pixels, pixels)
            Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888).apply {
                for (x in 0 until pixels) {
                    for (y in 0 until pixels) {
                        setPixel(
                            x,
                            y,
                            if (matrix.get(x, y)) android.graphics.Color.BLACK
                            else android.graphics.Color.WHITE,
                        )
                    }
                }
            }
        }.getOrNull()
    }
    if (bitmap == null) {
        Text("二维码生成失败", color = NextPalette.Danger)
    } else {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "校园卡付款码",
            modifier = modifier.size(size),
        )
    }
}
