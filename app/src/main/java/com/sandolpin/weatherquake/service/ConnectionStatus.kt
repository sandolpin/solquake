package com.sandolpin.weatherquake.service

/** WebSocket接続状態 */
sealed class ConnectionStatus {
    object Connecting : ConnectionStatus()
    object Connected : ConnectionStatus()
    data class Disconnected(val statusLabel: String, val message: String) : ConnectionStatus()
}
