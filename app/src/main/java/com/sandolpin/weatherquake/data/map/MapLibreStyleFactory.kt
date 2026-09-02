package com.sandolpin.weatherquake.map

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.toArgb
import com.sandolpin.weatherquake.data.IntensityLevel
import org.maplibre.android.maps.Style

/**
 * MapLibreの地図スタイル(震度着色ロジック込み)を組み立てるファクトリ。
 *
 * [設計変更の経緯]
 * 当初は Style.Builder().fromJson(空スタイル) の後に .withSource(...) / .withLayer(...) を
 * 段階的に呼び足す方式にしていたが、MapSnapshotter経由で使うと
 * 「java.lang.IllegalStateException: invalid native peer」が
 * MapSnapshotterのコンストラクタ内(スタイル読み込み完了と同時に同期的に発生する
 * nativeAddSourceの呼び出し)で発生することが分かった。
 * ライブ地図(MapView)側では同じ組み立て方でも問題が出なかったため、
 * MapSnapshotter特有のタイミング/ネイティブ側のライフサイクルの問題と考えられる。
 *
 * 対策として、ソース・レイヤーも含めてすべてを1つのJSON文字列として組み立て、
 * Style.Builder().fromJson(json) に一括で渡す方式に変更した。
 * スタイルJSONの一括パースは、ライブ地図で背景レイヤー表示に使っていたのと同じ経路であり、
 * 段階的なネイティブオブジェクトの追加を伴わないため安定する。
 *
 * レイヤー構成(地方予報区/市町村 共通):
 *   1. "{id}-base-fill"            : 該当geojson全域を薄いグレー/白で塗る下地レイヤー
 *   2. "{id}-base-line"            : 全域の境界線
 *   3. "{id}-colored-fill"         : 震度が判明している地域だけをフィルタし、震度色で塗る
 *   4. "{id}-colored-line"         : 上記の白い縁取り
 *   5. "{id}-selected-line-outline": 選択中(タップ等で選ばれた)地域の縁取り強調(下敷きの黒縁)
 *   6. "{id}-selected-line"        : 選択中地域の縁取り強調(黄色の線。5の上に重ねて視認性を出す)
 *
 * [選択ハイライトの更新方針]
 * 5・6のレイヤーは常にスタイルへ含めておき、初期状態では「該当なし・非表示」にしておく。
 * ユーザーが地図をタップ/リストから地域を選んだタイミングでは、スタイル全体を作り直す
 * (setStyleし直す)のではなく、Style.getLayer()で該当レイヤーだけを取得してfilter/visibilityを
 * 差し替える。スタイル全体を作り直すとシンボル(震源マーク)やカメラ位置がリセットされて
 * チラつくため、この軽量な更新経路を用意している。
 */
object MapLibreStyleFactory {

    const val REGION_SOURCE_ID = "regions"
    const val CITY_SOURCE_ID = "cities"

    const val REGION_COLORED_FILL_LAYER = "$REGION_SOURCE_ID-colored-fill"
    const val CITY_COLORED_FILL_LAYER = "$CITY_SOURCE_ID-colored-fill"
    /** 市町村タップ検出(queryRenderedFeatures)用。震度データの有無に関わらず全域に存在するレイヤー */
    const val CITY_BASE_FILL_LAYER = "$CITY_SOURCE_ID-base-fill"

    /** 選択中地域の縁取り強調レイヤー(市町村・地方予報区それぞれ)。実行時にfilter/visibilityを更新して使う。 */
    const val CITY_SELECTED_LINE_LAYER = "$CITY_SOURCE_ID-selected-line"
    const val CITY_SELECTED_OUTLINE_LAYER = "$CITY_SOURCE_ID-selected-line-outline"
    const val REGION_SELECTED_LINE_LAYER = "$REGION_SOURCE_ID-selected-line"
    const val REGION_SELECTED_OUTLINE_LAYER = "$REGION_SOURCE_ID-selected-line-outline"

    /**
     * @param regionIntensityByName 地方予報区名(WarnArea.Chiiki等) -> 震度。空なら地方予報区レイヤーは全域グレー/白のみ。
     * @param cityIntensityByName 市町村名 -> 震度。nullならcityソース自体をJSONに含めない
     *   (japan_city.geojson未配置の場合にソース読み込みエラーを起こさないため)。
     */
    fun build(
        context: Context,
        regionIntensityByName: Map<String, IntensityLevel>,
        cityIntensityByName: Map<String, IntensityLevel>? = null
    ): Style.Builder {
        val json = buildStyleJson(context, regionIntensityByName, cityIntensityByName)
        return Style.Builder().fromJson(json)
    }

    private fun buildStyleJson(
        context: Context,
        regionIntensityByName: Map<String, IntensityLevel>,
        cityIntensityByName: Map<String, IntensityLevel>?
    ): String {
        val isDark = isSystemDarkMode(context)
        val backgroundColor = if (isDark) "#2C2C2C" else "#F0F0F0"
        val baseFillColor = if (isDark) "#3A3A3A" else "#FFFFFF"
        // 境界線は「ズームアウトすると塗り色と見分けがつかなくなる」問題への対応として、
        // 以前より濃い色にしている(グレー→ほぼ黒に近い濃いグレー)。
        // 境界線は前回「濃く・太く」しすぎて塗りつぶし色が見えづらいとの指摘があったため、
        // 薄いグレー・細めに戻した(以前より少しだけ視認性を上げる程度に留める)。
        val baseLineColor = if (isDark) "#9A9A9A" else "#B0B0B0"

        val sources = StringBuilder()
        val layers = StringBuilder()

        sources.append(
            """"$REGION_SOURCE_ID":{"type":"geojson","data":"asset://japan.geojson"}"""
        )
        layers.append(regionLayerGroupJson(baseFillColor, baseLineColor, regionIntensityByName))

        if (cityIntensityByName != null) {
            sources.append(",")
            sources.append(
                """"$CITY_SOURCE_ID":{"type":"geojson","data":"asset://japan_city.geojson"}"""
            )
            layers.append(",")
            layers.append(cityLayerGroupJson(baseFillColor, baseLineColor, cityIntensityByName))
        }

        return """
            {
              "version": 8,
              "name": "warn-area",
              "sources": { $sources },
              "layers": [
                {"id":"bg","type":"background","paint":{"background-color":"${jsonStr(backgroundColor)}"}},
                $layers
              ]
            }
        """.trimIndent()
    }

    private fun regionLayerGroupJson(
        baseFillColor: String,
        baseLineColor: String,
        intensityByName: Map<String, IntensityLevel>
    ): String = layerGroupJson(REGION_SOURCE_ID, baseFillColor, baseLineColor, intensityByName, visible = true)

    private fun cityLayerGroupJson(
        baseFillColor: String,
        baseLineColor: String,
        intensityByName: Map<String, IntensityLevel>
    ): String {
        val visible = intensityByName.isNotEmpty()
        return layerGroupJson(CITY_SOURCE_ID, baseFillColor, baseLineColor, intensityByName, visible)
    }

    /**
     * 縮尺(zoom)に応じて線の太さを変える式。
     * ズームアウトしている(zoomの値が小さい)ときほど太く、ズームインするほど細くする。
     * これにより、日本全体を表示するような小縮尺でも境界線が塗りつぶし色に埋もれて
     * 見えなくならないようにしている。
     *
     * ["interpolate", ["linear"], ["zoom"], zoom1, width1, zoom2, width2, ...]
     * baseWidthは基準の太さ(ズーム8前後を想定)で、そこからの倍率で最小・最大を決める。
     */
    private fun zoomAdaptiveLineWidth(baseWidth: Float): String {
        val wide = baseWidth * 2.2f   // zoom=3(日本全体)相当
        val normal = baseWidth        // zoom=8相当
        val narrow = baseWidth * 0.6f // zoom=14(かなり拡大)相当
        return """["interpolate",["linear"],["zoom"],3,$wide,8,$normal,14,$narrow]"""
    }

    /** 下地(全域)+震度着色(フィルタ済み)+選択ハイライト の6レイヤー分のJSON片を組み立てる */
    private fun layerGroupJson(
        sourceId: String,
        baseFillColor: String,
        baseLineColor: String,
        intensityByName: Map<String, IntensityLevel>,
        visible: Boolean
    ): String {
        val visibility = if (visible) "visible" else "none"
        val namesJsonArray = intensityByName.keys.joinToString(",") { "\"${jsonStr(it)}\"" }
        val matchExpr = buildMatchExpressionJson(intensityByName)
        val baseLineWidth = zoomAdaptiveLineWidth(0.6f)
        val coloredLineWidth = zoomAdaptiveLineWidth(1.2f)
        // 選択強調用のレイヤーは、初期状態では対象なし(空文字とのフィルタ一致=常に非該当)+非表示にしておき、
        // 実際にユーザーが選択したタイミングで呼び出し側がsetFilter/setPropertiesで更新する。
        val selectedOutlineWidth = zoomAdaptiveLineWidth(3.6f)
        val selectedLineWidth = zoomAdaptiveLineWidth(2.0f)

        return """
            {"id":"$sourceId-base-fill","type":"fill","source":"$sourceId",
             "layout":{"visibility":"$visibility"},
             "paint":{"fill-color":"${jsonStr(baseFillColor)}","fill-opacity":1.0}},
            {"id":"$sourceId-base-line","type":"line","source":"$sourceId",
             "layout":{"visibility":"$visibility"},
             "paint":{"line-color":"${jsonStr(baseLineColor)}","line-width":$baseLineWidth}},
            {"id":"$sourceId-colored-fill","type":"fill","source":"$sourceId",
             "layout":{"visibility":"$visibility"},
             "filter":["in",["get","name"],["literal",[$namesJsonArray]]],
             "paint":{"fill-color":$matchExpr,"fill-opacity":0.85}},
            {"id":"$sourceId-colored-line","type":"line","source":"$sourceId",
             "layout":{"visibility":"$visibility"},
             "filter":["in",["get","name"],["literal",[$namesJsonArray]]],
             "paint":{"line-color":"#FFFFFF","line-width":$coloredLineWidth}},
            {"id":"$sourceId-selected-line-outline","type":"line","source":"$sourceId",
             "layout":{"visibility":"none"},
             "filter":["==",["get","name"],""],
             "paint":{"line-color":"#000000","line-width":$selectedOutlineWidth,"line-opacity":0.9}},
            {"id":"$sourceId-selected-line","type":"line","source":"$sourceId",
             "layout":{"visibility":"none"},
             "filter":["==",["get","name"],""],
             "paint":{"line-color":"#FFEE58","line-width":$selectedLineWidth}}
        """.trimIndent()
    }

    /** ["match", ["get","name"], "地域A","#色1", "地域B","#色2", ..., "#デフォルト色] というJSON配列を文字列で組み立てる */
    private fun buildMatchExpressionJson(intensityByName: Map<String, IntensityLevel>): String {
        if (intensityByName.isEmpty()) {
            return "\"${colorHex(IntensityLevel.UNKNOWN)}\""
        }
        val stopsJson = intensityByName.entries.joinToString(",") { (name, level) ->
            "\"${jsonStr(name)}\",\"${colorHex(level)}\""
        }
        return """["match",["get","name"],$stopsJson,"${colorHex(IntensityLevel.UNKNOWN)}"]"""
    }

    private fun colorHex(level: IntensityLevel): String {
        val argb = level.bgColor.toArgb()
        return String.format("#%06X", argb and 0x00FFFFFF)
    }

    /** JSON文字列リテラル用の最低限のエスケープ(ダブルクォート・バックスラッシュ・制御文字) */
    private fun jsonStr(raw: String): String {
        val sb = StringBuilder()
        raw.forEach { c ->
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun isSystemDarkMode(context: Context): Boolean {
        val flags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return flags == Configuration.UI_MODE_NIGHT_YES
    }
}