package com.sandolpin.weatherquake.service

import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * WebSocket/HTTP周りの例外・ステータスコードを、日本語の分かりやすい説明に変換する。
 * 戻り値は Pair(ラベル, 説明文)。
 */
object ErrorMessageMapper {

    fun fromHttpCode(code: Int): Pair<String, String> {
        val label = "エラーステータス $code"
        val message = when (code) {
            400 -> "サーバーからの応答がありません"
            401, 403 -> "アクセスが拒否されました"
            404 -> "接続先が見つかりません"
            408 -> "サーバーへの接続がタイムアウトしました"
            429 -> "リクエストが多すぎます。しばらく待ってから再試行してください"
            in 500..599 -> "サーバー側でエラーが発生しています"
            else -> "不明なエラーが発生しました"
        }
        return label to message
    }

    fun fromThrowable(t: Throwable): Pair<String, String> {
        return when (t) {
            is UnknownHostException -> "接続エラー" to "インターネットに接続されていません"
            is SocketTimeoutException -> "タイムアウト" to "サーバーへの接続がタイムアウトしました"
            else -> "不明なエラー" to (t.message ?: "不明なエラーが発生しました")
        }
    }
}
