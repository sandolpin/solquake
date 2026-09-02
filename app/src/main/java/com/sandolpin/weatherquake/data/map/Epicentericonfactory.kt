package com.sandolpin.weatherquake.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.ColorUtils

/**
 * 震源マーカーのアイコンをBitmapとして1回だけ生成する。
 * 以前(WarnAreaMap.kt / WarnAreaMapRenderer.kt)はCanvas上に毎フレーム直接×印を描いていたが、
 * MapLibreではSymbolLayerの「アイコン画像」としてBitmapをスタイルに登録し、
 * それをLatLng位置に配置する方式になる。点滅はComposable側でアイコンの表示/非表示を
 * 切り替えることで表現する(このBitmap自体は静的な1枚絵でよい)。
 *
 * [サイズについて]
 * 「震源の×マークが小さい」との指摘を受け、キャンバスサイズ(SIZE_PX)を96→144に拡大した。
 * 腕の長さ・線の太さはすべてSIZE_PXに対する比率で計算しているため、この定数を変えるだけで
 * 全体が比例して大きくなる(各Composable側のwithIconSize()の値と合わせて調整すること)。
 */
object EpicenterIconFactory {

    const val ICON_ID_CROSS = "epicenter-cross"
    const val ICON_ID_ASSUMPTION = "epicenter-assumption"

    private const val SIZE_PX = 144

    /** 通常の震源(×印)アイコン */
    fun crossIcon(): Bitmap = renderIcon { canvas, paint ->
        val center = SIZE_PX / 2f
        val armLength = SIZE_PX * 0.30f
        val shadowOffset = 5f
        val shadowColor = ColorUtils.setAlphaComponent(Color.BLACK, (0.4f * 255).toInt())
        val outlineColor = Color.WHITE
        val fillColor = Color.parseColor("#E53935")

        fun crossPath(cx: Float, cy: Float): Path = Path().apply {
            moveTo(cx - armLength, cy - armLength)
            lineTo(cx + armLength, cy + armLength)
            moveTo(cx + armLength, cy - armLength)
            lineTo(cx - armLength, cy + armLength)
        }

        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND

        paint.strokeWidth = SIZE_PX * 0.065f
        paint.color = shadowColor
        canvas.drawPath(crossPath(center + shadowOffset, center + shadowOffset), paint)

        paint.strokeWidth = SIZE_PX * 0.12f
        paint.color = outlineColor
        canvas.drawPath(crossPath(center, center), paint)

        paint.strokeWidth = SIZE_PX * 0.065f
        paint.color = fillColor
        canvas.drawPath(crossPath(center, center), paint)
    }

    /** PLUM法などによる仮定震源(○印)アイコン */
    fun assumptionIcon(): Bitmap = renderIcon { canvas, paint ->
        val center = SIZE_PX / 2f
        val radius = SIZE_PX * 0.24f
        val shadowOffset = 5f
        val shadowColor = ColorUtils.setAlphaComponent(Color.BLACK, (0.4f * 255).toInt())

        paint.style = Paint.Style.FILL
        paint.color = shadowColor
        canvas.drawCircle(center + shadowOffset, center + shadowOffset, radius, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = SIZE_PX * 0.085f
        paint.color = Color.WHITE
        canvas.drawCircle(center, center, radius, paint)

        paint.strokeWidth = SIZE_PX * 0.07f
        paint.color = Color.parseColor("#E53935")
        canvas.drawCircle(center, center, radius - 4f, paint)
    }

    private inline fun renderIcon(draw: (Canvas, Paint) -> Unit): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        draw(canvas, paint)
        return bitmap
    }

    /**
     * 震度で色、規模(マグニチュード)で大きさが変わる半透明の円マーカー。
     * QuakeHistoryMap(地震履歴の全画面地図)のピンに使用する。
     *
     * [サイズについて] こちらも震源×印と同様に、視認性向上のため直径の範囲を
     * 56〜200px → 76〜260pxに拡大した。
     *
     * @param magnitude 規模。null(不明)の場合は中間サイズ(M4.0相当)を採用する。
     * @param colorArgb 塗りつぶし色(通常はIntensityLevel.bgColor.toArgb())
     */
    fun magnitudeCircle(magnitude: Double?, colorArgb: Int): Bitmap {
        val mag = (magnitude ?: 4.0).coerceIn(1.0, 9.0)
        // M1〜M9を、直径76px(小)〜260px(大)にマッピングする。
        val diameterPx = (76 + (mag - 1.0) / 8.0 * 184).toInt().coerceIn(76, 260)

        val bitmap = Bitmap.createBitmap(diameterPx, diameterPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = diameterPx / 2f
        val radius = diameterPx / 2f - 5f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 半透明の塗りつぶし(震度色)
        paint.style = Paint.Style.FILL
        paint.color = ColorUtils.setAlphaComponent(colorArgb, (0.55f * 255).toInt())
        canvas.drawCircle(center, center, radius, paint)

        // 縁取り(同系色を不透明で少し太めに)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = diameterPx * 0.04f
        paint.color = ColorUtils.setAlphaComponent(colorArgb, (0.9f * 255).toInt())
        canvas.drawCircle(center, center, radius, paint)

        return bitmap
    }
}