package com.sandolpin.weatherquake.data.quake

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder

/**
 * P2P地震情報API(地震情報/code=551)を定期取得し、UIに公開するRepository。
 * EewRepositoryと同じくobject(シングルトン)で実装し、Service/UIどちらからも参照できるようにする。
 *
 * このAPIは「地震が発生した後の観測情報」であり、緊急地震速報(EewRepository)ほど
 * 即時性がシビアではないため、専用のWebSocket接続は持たず、
 * EewServiceの常駐ループに相乗りしてポーリングする設計にしている
 * (常駐Foreground Serviceを2つ持つより省リソース)。
 */
object QuakeRepository {

    private val client = OkHttpClient()
    private val gson = Gson()

    // "2024/01/01 00:00:00.000" 形式(ミリ秒あり/なし両対応)
    private val inputTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("yyyy/MM/dd HH:mm:ss")
        .optionalStart()
        .appendPattern(".SSS")
        .optionalEnd()
        .toFormatter()
    private val displayTimeFormatter = DateTimeFormatter.ofPattern("M/d HH:mm")

    private const val HISTORY_URL = "https://api.p2pquake.net/v2/history?codes=551&limit=30"

    private val _quakes = MutableStateFlow<List<QuakeCardState>>(emptyList())
    val quakes: StateFlow<List<QuakeCardState>> = _quakes

    private val seenIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    @Volatile
    private var hasPolledOnce = false

    /** EewServiceのポーリングループから定期的に呼ばれる。新着があればtrueを返す(通知判定用)。 */
    suspend fun pollOnce(): List<QuakeCardState> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(HISTORY_URL).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val type = object : TypeToken<List<P2pQuakeItem>>() {}.type
                val items: List<P2pQuakeItem> = gson.fromJson(body, type)

                val mapped = items.mapNotNull { toCardState(it) }
                _quakes.value = mapped.sortedByDescending { it.occurredAtLabel }

                if (!hasPolledOnce) {
                    // アプリ起動直後の初回取得は、既に発生済みの過去の地震情報(最大30件)を
                    // 「新着」として扱わない。既知のIDとして記録するだけにして、
                    // 起動のたびに古い地震の通知が大量発火するのを防ぐ。
                    mapped.forEach { seenIds.add(it.id) }
                    hasPolledOnce = true
                    return@withContext emptyList()
                }

                val newlyAdded = mapped.filter { seenIds.add(it.id) }
                newlyAdded
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun toCardState(item: P2pQuakeItem): QuakeCardState? {
        val eq = item.earthquake ?: return null
        val hypo = eq.hypocenter
        val occurredAt = runCatching {
            LocalDateTime.parse(eq.time, inputTimeFormatter).format(displayTimeFormatter)
        }.getOrDefault(eq.time)

        return QuakeCardState(
            id = item.id,
            hypocenterName = hypo?.name ?: "不明",
            depthKm = hypo?.depth?.takeIf { it >= 0 },
            magnitude = hypo?.magnitude?.takeIf { it >= 0 },
            occurredAtLabel = occurredAt,
            latitude = hypo?.latitude ?: 0.0,
            longitude = hypo?.longitude ?: 0.0,
            maxScale = eq.maxScale,
            points = item.points.orEmpty()
        )
    }

    /** 設定画面の「テスト表示」用にダミーの地震情報を挿入する */
    fun injectTestQuake(sample: QuakeCardState) {
        _quakes.update { listOf(sample) + it }
    }
}