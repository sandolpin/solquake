package com.sandolpin.weatherquake.service

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.QUEUE_FLUSH
import java.util.Locale

/**
 * 設定画面の「通知時に音声で読み上げる」を実現するラッパー。
 *
 * Android標準の TextToSpeech は初期化が非同期(コールバック)なため、
 * 「初期化前に読み上げ依頼が来たら、初期化完了後にキューから読み上げる」という
 * 単純なペンディングキューを持たせている。
 * Serviceのプロセス生存中は使い回し、onDestroyでshutdownする想定(EewServiceから呼ぶ)。
 */
object TtsAnnouncer {

    private var tts: TextToSpeech? = null
    private var isReady = false
    private val pendingUtterances = mutableListOf<String>()

    fun init(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.JAPAN
                isReady = true
                pendingUtterances.forEach { speakInternal(it) }
                pendingUtterances.clear()
            }
        }
    }

    /**
     * 緊急地震速報向け読み上げ。
     * 「○○で地震、予想最大震度は〇、強い揺れに警戒:○○...」(警戒地域が無い場合は後半省略)
     */
    fun announceEew(hypocenter: String, maxIntensityLabel: String, warnAreaLabel: String?) {
        val warnPart = warnAreaLabel?.let { "、強い揺れに警戒:$it" } ?: ""
        speak("${hypocenter}で地震、予想最大震度は${maxIntensityLabel}${warnPart}")
    }

    /**
     * 地震情報向け読み上げ。
     * 「地震情報、〇時〇分ごろ、〇〇で最大震度〇の地震がありました。
     *   震源は○○、深さ〇km、マグニチュード〇、最大震度〇を○○県(都道府県)で観測しています」
     */
    fun announceQuake(
        occurredAtLabel: String,
        hypocenter: String,
        depthKm: Int?,
        magnitude: Double?,
        maxIntensityLabel: String,
        observedPrefecture: String?
    ) {
        val time = timeLabelFrom(occurredAtLabel)
        val depthPart = depthKm?.let { "深さ${it}km" } ?: "深さ不明"
        val magPart = magnitude?.let { "マグニチュード$it" } ?: "マグニチュード不明"
        val observedPart = observedPrefecture?.let { "${it}で観測しています" } ?: "観測地点は不明です"
        speak(
            "地震情報、${time}、${hypocenter}で最大震度${maxIntensityLabel}の地震がありました。" +
                    "震源は${hypocenter}、${depthPart}、${magPart}、最大震度${maxIntensityLabel}を${observedPart}"
        )
    }

    /**
     * QuakeRepositoryが整形した "8/21 19:09" 形式の occurredAtLabel から
     * 読み上げ用の「19時09分ごろ」を作る。
     * occurredAtLabelは既に "M/d HH:mm" フォーマット済みの文字列であり、
     * LocalDateTimeオブジェクトはこの時点で残っていないため、文字列を後ろから
     * 分解する形で時刻部分だけを取り出している。
     */
    private fun timeLabelFrom(occurredAtLabel: String): String {
        return try {
            val timePart = occurredAtLabel.substringAfterLast(" ") // "19:09"
            val (hour, minute) = timePart.split(":")
            "${hour.toInt()}時${minute}分ごろ"
        } catch (e: Exception) {
            "先ほど"
        }
    }

    private fun speak(text: String) {
        if (isReady) speakInternal(text) else pendingUtterances.add(text)
    }

    private fun speakInternal(text: String) {
        tts?.speak(text, QUEUE_FLUSH, null, "wq_${System.currentTimeMillis()}")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}