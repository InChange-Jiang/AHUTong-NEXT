package com.ahu.ahutong.next

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ahu.ahutong.legacy.LegacyCampusApi
import com.ahu.ahutong.legacy.LegacyDataApi
import com.ahu.ahutong.next.ui.NextApp
import com.ahu.ahutong.next.ui.theme.NextTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LegacyCampusApi.init(applicationContext)
        LegacyDataApi.init(applicationContext)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            NextTheme {
                NextApp()
            }
        }
    }
}
