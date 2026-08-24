package com.sandolpin.weatherquake.ui.eew

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sandolpin.weatherquake.data.IntensityLevel
import com.sandolpin.weatherquake.data.eew.EewCardState
import com.sandolpin.weatherquake.data.eew.EewCodeType
import com.sandolpin.weatherquake.data.settings.IntensityColorContrast
import com.sandolpin.weatherquake.ui.components.IntensityBadge
import com.sandolpin.weatherquake.ui.theme.CardCancel
import com.sandolpin.weatherquake.ui.theme.CardForecast
import com.sandolpin.weatherquake.ui.theme.CardWarning

/**
 * 緊急地震速報1件分のカード。
 * デザイン画像に合わせ、予報=オレンジ、警報/特別警報=赤、取消=グレーのヘッダーで表現する。
 */
@Composable
fun EewCard(
    state: EewCardState,
    modifier: Modifier = Modifier,
    intensityContrast: IntensityColorContrast = IntensityColorContrast.DEFAULT,
    showMap: Boolean = true,
    isFinalOverride: Boolean? = null,
    defaultExpanded: Boolean = true
) {
    val eew = state.eew
    val codeType = state.codeType
    val headerColor = when (codeType) {
        EewCodeType.FORECAST -> CardForecast
        EewCodeType.WARNING, EewCodeType.EMERGENCY_WARNING -> CardWarning
        EewCodeType.CANCEL -> CardCancel
    }
    val isWarningTier = codeType == EewCodeType.WARNING || codeType == EewCodeType.EMERGENCY_WARNING
    val hasWarnArea = eew.isWarn && !eew.WarnArea.isNullOrEmpty()
    val hasValidEpicenter = eew.Latitude != 0.0 || eew.Longitude != 0.0
    val hasMapArea = showMap && codeType != EewCodeType.CANCEL && (!eew.WarnArea.isNullOrEmpty() || hasValidEpicenter)

    var expanded by remember { mutableStateOf(defaultExpanded) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (isWarningTier) headerColor else Color.White,
        border = if (isWarningTier) null else androidx.compose.foundation.BorderStroke(3.dp, headerColor)
    ) {
        Column(modifier = Modifier.animateContentSize()) {
            // --- ヘッダー ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerColor)
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(codeType.displayName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                val serialLabel = "第${eew.Serial}報${if (isFinalOverride ?: eew.isFinal) "（最終）" else ""}"
                Text(serialLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            if (expanded) {
                Surface(color = if (isWarningTier) Color.White else Color.White) {
                    Column(Modifier.padding(16.dp)) {
                        Row {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("予想震度", fontSize = 12.sp, color = Color(0xFF616161))
                                Spacer(Modifier.width(0.dp))
                                IntensityBadge(level = state.intensity, size = 64.dp, contrast = intensityContrast)
                            }
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text("${eew.AnnouncedTime}発表", fontSize = 12.sp, color = Color(0xFF616161))
                                Text(eew.Hypocenter, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.Black)
                                if (eew.isAssumption) {
                                    Text("PLUM法による仮定震源要素", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Black)
                                } else {
                                    Text("深さ${eew.Depth.toInt()}km / 規模 M${eew.Magunitude}", fontSize = 15.sp, color = Color.Black)
                                }
                                Text("${eew.OriginTime}発生", fontSize = 12.sp, color = Color(0xFF616161))
                            }
                        }

                        if (hasMapArea) {
                            Spacer(Modifier.height(12.dp))
                            WarnAreaMap(
                                warnAreas = eew.WarnArea.orEmpty(),
                                latitude = eew.Latitude,
                                longitude = eew.Longitude,
                                isAssumption = eew.isAssumption
                            )
                        }

                        if (hasWarnArea) {
                            Spacer(Modifier.height(12.dp))
                            Text("強い揺れが予想される地域", fontSize = 12.sp, color = Color(0xFF616161))
                            Spacer(Modifier.height(6.dp))

                            val grouped = eew.WarnArea!!
                                .groupBy { IntensityLevel.fromApiString(it.Shindo1) }
                                .toList()
                                .sortedByDescending { (level, _) -> level.ordinal }

                            grouped.forEachIndexed { index, (level, areas) ->
                                Surface(shape = RoundedCornerShape(6.dp), color = level.bgColor) {
                                    Text(
                                        "震度${level.formalLabel}",
                                        color = level.textColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    areas.joinToString("、") { it.Chiiki },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = Color.Black
                                )
                                if (index != grouped.lastIndex) Spacer(Modifier.height(10.dp))
                            }
                        }

                        if (codeType == EewCodeType.CANCEL) {
                            Spacer(Modifier.height(8.dp))
                            Text("この緊急地震速報は、取り消されました。", color = Color(0xFF616161))
                        }
                    }
                }
            }
        }
    }
}
