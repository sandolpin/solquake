package com.sandolpin.weatherquake.data

import android.content.Context
import android.util.JsonReader
import java.io.InputStreamReader

/**
 * 座標抽出・バウンディングボックス計算まで済ませた状態のFeature。
 *
 * polygonsは [lon0, lat0, lon1, lat1, ...] のように経度緯度を交互に格納したFloatArrayのリスト。
 * List<Pair<Double,Double>>ではなくFloatArrayにしているのは、全国分(数十万点規模)を
 * 保持する際のメモリ・GC負荷を大幅に削減するため。
 */
data class PreparedFeature(
    val name: String,
    val polygons: List<FloatArray>,
    val bbox: BoundingBox?
)

/**
 * assets/japan.geojson を読み込み、Chiiki名(気象庁の一次細分区域名/地方予報区名)に
 * 対応する座標データを提供するローダー。
 *
 * android.util.JsonReaderによるストリーミング解析で、必要な座標(外側リングのみ)を
 * 直接FloatArrayへ書き込むため、パース時間・メモリ使用量ともに軽量。
 * 解析結果は全国分をまとめて1回だけ計算し、以降はメモリキャッシュを再利用する。
 */
object GeoJsonLoader {

    private const val ASSET_FILE_NAME = "japan.geojson"

    @Volatile
    private var cachedPreparedFeatures: List<PreparedFeature>? = null

    /**
     * 全Featureの座標データを返す。初回のみassetsからストリーミング解析し、
     * 以後はキャッシュを返す。呼び出し側はDispatchers.IO上から呼ぶこと。
     */
    @Synchronized
    fun preparedFeatures(context: Context): List<PreparedFeature> {
        cachedPreparedFeatures?.let { return it }
        val result = parseAsset(context)
        cachedPreparedFeatures = result
        return result
    }

    /** アプリ起動時など、地図表示より前にバックグラウンド解析を済ませておくための関数 */
    fun preload(context: Context) {
        preparedFeatures(context)
    }

    private fun parseAsset(context: Context): List<PreparedFeature> {
        val features = mutableListOf<PreparedFeature>()
        context.assets.open(ASSET_FILE_NAME).use { input ->
            JsonReader(InputStreamReader(input, "UTF-8")).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    if (reader.nextName() == "features") {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            parseFeature(reader)?.let { features.add(it) }
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
        var polygons: List<FloatArray> = emptyList()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "properties" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        if (reader.nextName() == "name") {
                            name = reader.nextString()
                        } else {
                            reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
                "geometry" -> polygons = parseGeometry(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (polygons.isEmpty()) return null
        val bbox = GeoCoordinateUtil.boundingBox(polygons)
        return PreparedFeature(name, polygons, bbox)
    }

    /**
     * Polygon/MultiPolygonのcoordinatesを読み、各ポリゴンの「外側リングのみ」をFloatArrayにする。
     * 内側リング(穴)は描画対象外のため、skipValue()で読み飛ばす。
     */
    private fun parseGeometry(reader: JsonReader): List<FloatArray> {
        var geomType = ""
        val polygons = mutableListOf<FloatArray>()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "type" -> geomType = reader.nextString()
                "coordinates" -> {
                    when (geomType) {
                        "Polygon" -> {
                            reader.beginArray()
                            var isOuter = true
                            while (reader.hasNext()) {
                                if (isOuter) {
                                    polygons.add(parseRing(reader))
                                    isOuter = false
                                } else {
                                    reader.skipValue()
                                }
                            }
                            reader.endArray()
                        }
                        "MultiPolygon" -> {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                reader.beginArray()
                                var isOuter = true
                                while (reader.hasNext()) {
                                    if (isOuter) {
                                        polygons.add(parseRing(reader))
                                        isOuter = false
                                    } else {
                                        reader.skipValue()
                                    }
                                }
                                reader.endArray()
                            }
                            reader.endArray()
                        }
                        else -> reader.skipValue()
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return polygons
    }

    /** 1つのリング([[lon,lat], [lon,lat], ...])を読み、[lon0,lat0,lon1,lat1,...]のFloatArrayにする */
    private fun parseRing(reader: JsonReader): FloatArray {
        val buffer = ArrayList<Float>()
        reader.beginArray()
        while (reader.hasNext()) {
            reader.beginArray()
            val lon = reader.nextDouble()
            val lat = reader.nextDouble()
            while (reader.hasNext()) reader.skipValue()
            reader.endArray()
            buffer.add(lon.toFloat())
            buffer.add(lat.toFloat())
        }
        reader.endArray()

        val array = FloatArray(buffer.size)
        for (i in buffer.indices) array[i] = buffer[i]
        return array
    }
}
