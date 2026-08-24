package com.sandolpin.weatherquake.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class AppTab(val route: String, val label: String, val icon: ImageVector) {
    WEATHER("weather", "天気", Icons.Filled.Cloud),
    QUAKE("quake", "地震", Icons.Filled.MonitorHeart),
    SETTINGS("settings", "設定", Icons.Filled.Settings)
}

@Composable
fun BottomNavBar(selectedTab: AppTab, onTabSelected: (AppTab) -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Row(modifier = Modifier.padding(6.dp)) {
            AppTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else androidx.compose.ui.graphics.Color.Transparent,
                    modifier = Modifier
                        .clickable { onTabSelected(tab) }
                        .padding(2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            tab.icon,
                            contentDescription = tab.label,
                            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (selected) {
                            Text(tab.label, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
