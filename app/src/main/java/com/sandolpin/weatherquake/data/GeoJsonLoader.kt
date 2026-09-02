package com.sandolpin.weatherquake.data

import android.content.Context
import android.util.JsonReader
import java.io.InputStreamReader

/**
 * [MapLibre移行後の役割変更]
 * 以前はここで全ポリゴン座標(FloatArray)を保持し、Compose/Canvasで自力描画していたが、
 * 実際の地図描画(塗りつぶし・縁取り)はMapLibreのGeoJsonSourceがネイティブ側で
 * 直接assets配下のgeojsonファイルを読み込んで行うようになったため不要になった。
 *
 * このクラスは「カメラをどの範囲に合わせるか(バウンディングボックス)」を計算する目的にのみ
 * 縮小した。ポリゴン座標そのものは保持しない(featureごとに一時的にリングを読み、
 * bboxを計算したらすぐ捨てる)ため、以前よりメモリ使用量が大幅に少ない。
 *
 * 複数のgeojsonファイル(地方予報区用のjapan.geojson、市町村用のjapan_city.geojson)を
 * 扱えるよう、assetファイル名ごとにキャッシュを持つ設計にしている。
 */
data class PreparedFeature(
    val name: String,
    val bbox: BoundingBox?
)

class GeoJsonLoader private constructor(private val assetFileName: String) {

    @Volatile
    private var cached: List<PreparedFeature>? = null

    /** 全Featureの名前+bboxを返す。初回のみassetsからストリーミング解析し、以後はキャッシュを返す。 */
    @Synchronized
    fun preparedFeatures(context: Context): List<PreparedFeature> {
        cached?.let { return it }
        val result = parseAsset(context)
        cached = result
        return result
    }

    /** アプリ起動時など、地図表示より前にバックグラウンド解析を済ませておくための関数 */
    fun preload(context: Context) {
        preparedFeatures(context)
    }

    /** 指定した名前一覧に該当するFeatureのbboxをまとめて包含するBoundingBoxを返す */
    fun boundingBoxFor(context: Context, names: Collection<String>): BoundingBox? {
        if (names.isEmpty()) return null
        val nameSet = names.toSet()
        val boxes = preparedFeatures(context).filter { it.name in nameSet }.mapNotNull { it.bbox }
        if (boxes.isEmpty()) return null
        var minLon = Double.MAX_VALUE
        var maxLon = -Double.MAX_VALUE
        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        boxes.forEach { box ->
            if (box.minLon < minLon) minLon = box.minLon
            if (box.maxLon > maxLon) maxLon = box.maxLon
            if (box.minLat < minLat) minLat = box.minLat
            if (box.maxLat > maxLat) maxLat = box.maxLat
        }
        return BoundingBox(minLon, maxLon, minLat, maxLat)
    }

    private fun parseAsset(context: Context): List<PreparedFeature> {
        val features = mutableListOf<PreparedFeature>()
        context.assets.open(assetFileName).use { input ->
            JsonReader(InputStreamReader(input, "UTF-8")).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    if (reader.nextName() == "features") {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            if (reader.peek() == android.util.JsonToken.NULL) {
                                reader.nextNull()
                            } else {
                                parseFeature(reader)?.let { features.add(it) }
                            }
                        }
                        reader.endArray()
                    } else {
                        reader.skipValue()
                    }
                }
                reader.endObject()
            }
        }
        return features
    }

    private fun parseFeature(reader: JsonReader): PreparedFeature? {
        var name = ""
        var bbox: BoundingBox? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "properties" -> {
                    if (reader.peek() == android.util.JsonToken.NULL) {
                        reader.nextNull()
                    } else {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            if (reader.nextName() == "name") {
                                name = if (reader.peek() == android.util.JsonToken.NULL) {
                                    reader.nextNull()
                                    ""
                                } else {
                                    reader.nextString()
                                }
                            } else {
                                reader.skipValue()
                            }
                        }
                        reader.endObject()
                    }
                }
                "geometry" -> bbox = parseGeometryBbox(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (name.isBlank() || bbox == null) return null
        return PreparedFeature(name, bbox)
    }

    /**
     * Polygon/MultiPolygonのcoordinatesを読みながら、min/maxを都度更新するだけでbboxを求める。
     * 座標配列そのものは保持しない(1点読んだら破棄する)ため、内側リング(穴)を含めて
     * 全点を読んでも問題ない(描画に使わないので外側/内側を区別する必要も無い)。
     *
     * [null許容] GeoJSONは仕様上 "geometry": null や "coordinates": null を許容している
     * (形状未確定のFeature等)。市町村レベルのデータはこのようなケースを含みやすいため、
     * beginObject()/beginArray()を呼ぶ前に必ずJsonToken.NULLかどうかを確認している。
     */
    private fun parseGeometryBbox(reader: JsonReader): BoundingBox? {
        if (reader.peek() == android.util.JsonToken.NULL) {
            reader.nextNull()
            return null
        }

        var minLon = Double.MAX_VALUE
        var maxLon = -Double.MAX_VALUE
        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var found = false

        fun visitPoint(lon: Double, lat: Double) {
            if (lon < minLon) minLon = lon
            if (lon > maxLon) maxLon = lon
            if (lat < minLat) minLat = lat
            if (lat > maxLat) maxLat = lat
            found = true
        }

        fun readPositionArray() {
            // [lon, lat] または [lon, lat, alt]
            reader.beginArray()
            val lon = if (reader.peek() == android.util.JsonToken.NULL) { reader.nextNull(); 0.0 } else reader.nextDouble()
            val lat = if (reader.peek() == android.util.JsonToken.NULL) { reader.nextNull(); 0.0 } else reader.nextDouble()
            while (reader.hasNext()) reader.skipValue()
            reader.endArray()
            visitPoint(lon, lat)
        }

        fun readRing() {
            reader.beginArray()
            while (reader.hasNext()) readPositionArray()
            reader.endArray()
        }

        fun readPolygon() {
            reader.beginArray()
            while (reader.hasNext()) readRing()
            reader.endArray()
        }

        reader.beginObject()
        var geomType = ""
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "type" -> geomType = if (reader.peek() == android.util.JsonToken.NULL) {
                    reader.nextNull()
                    ""
                } else {
                    reader.nextString()
                }
                "coordinates" -> {
                    if (reader.peek() == android.util.JsonToken.NULL) {
                        reader.nextNull()
                    } else {
                        when (geomType) {
                            "Polygon" -> readPolygon()
                            "MultiPolygon" -> {
                                reader.beginArray()
                                while (reader.hasNext()) readPolygon()
                                reader.endArray()
                            }
                            else -> reader.skipValue()
                        }
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return if (found) BoundingBox(minLon, maxLon, minLat, maxLat) else null
    }

    companion object {
        private const val REGION_ASSET = "japan.geojson"
        private const val CITY_ASSET = "japan_city.geojson"

        private val instances = java.util.concurrent.ConcurrentHashMap<String, GeoJsonLoader>()

        private fun instanceFor(assetFileName: String): GeoJsonLoader =
            instances.getOrPut(assetFileName) { GeoJsonLoader(assetFileName) }

        /** 地方予報区・都道府県レベル(緊急地震速報のWarnAreaで使用) */
        val region: GeoJsonLoader get() = instanceFor(REGION_ASSET)

        /** 市町村レベル(地震情報の観測点を市町村単位で塗りたい場合に使用) */
        val city: GeoJsonLoader get() = instanceFor(CITY_ASSET)

        /** アプリ起動時に両方のgeojsonを事前解析しておく */
        fun preloadAll(context: Context) {
            region.preload(context)
            // japan_city.geojsonが未配置の場合はここで例外が出るが、
            // 呼び出し側(WeatherQuakeApp)でtry-catchしているため致命的にはならない。
            city.preload(context)
        }
    }
}