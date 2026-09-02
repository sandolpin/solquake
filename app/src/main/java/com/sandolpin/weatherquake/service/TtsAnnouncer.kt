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
     * 緊急地震速報向けの読み上げ。
     * 予報: 「○○で地震、予想最大震度は○」
     * 警報・特別警報: 「○○で地震、予想最大震度は○、強い揺れに警戒、○○」
     */
    fun announceEew(hypocenter: String, maxIntensityLabel: String, isWarningTier: Boolean, warnAreaLabel: String?) {
        val text = buildString {
            append("${hypocenter}で地震、予想最大震度は${maxIntensityLabel}")
            if (isWarningTier && !warnAreaLabel.isNullOrBlank()) {
                append("、強い揺れに警戒、$warnAreaLabel")
            }
        }
        speak(text)
    }

    /** 地震情報向け: 「地震情報。最大震度〇〇を〇〇で観測」のような文言を組み立てて読み上げる */
    fun announceQuake(maxIntensityLabel: String, hypocenter: String, areaLabel: String?) {
        val areaPart = areaLabel?.let { "、最大震度を観測したのは${it}です" } ?: ""
        speak("地震情報です。震源は${hypocenter}、最大震度は${maxIntensityLabel}${areaPart}。")
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