package com.sandolpin.weatherquake.service

import com.sandolpin.weatherquake.data.eew.EewCardState
import com.sandolpin.weatherquake.data.eew.JmaEew
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Service(WebSocket受信側)とUI(Compose画面)の間でデータを橋渡しするRepository。
 */
object EewRepository {

    private val _cards = MutableStateFlow<List<EewCardState>>(emptyList())
    val cards: StateFlow<List<EewCardState>> = _cards

    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Connecting)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus

    private val _lastUpdated = MutableStateFlow(System.currentTimeMillis())
    val lastUpdated: StateFlow<Long> = _lastUpdated

    /** EventIDごとに、これまで受信した全ての第N報を記録しておく履歴 */
    private val _history = MutableStateFlow<Map<String, List<JmaEew>>>(emptyMap())
    val history: StateFlow<Map<String, List<JmaEew>>> = _history

    private fun recordHistory(eew: JmaEew) {
        _history.update { current ->
            val existingList = current[eew.EventID] ?: emptyList()
            if (existingList.any { it.Serial == eew.Serial }) {
                return@update current
            }
            val updatedList = (existingList + eew).sortedByDescending { it.Serial }
            current + (eew.EventID to updatedList)
        }
    }

    /** カードの表示期限(5分)。この時間が経過したカードは自動で一覧から消える */
    private const val CARD_LIFETIME_MS = 5 * 60 * 1000L

    private val dismissedKeys: MutableSet<String> =
        java.util.Collections.synchronizedSet(mutableSetOf())

    private fun keyOf(eventId: String, serial: Int) = "${eventId}_$serial"

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        repositoryScope.launch {
            while (true) {
                delay(30_000)
                purgeExpiredCards()
            }
        }
    }

    private fun purgeExpiredCards() {
        val now = System.currentTimeMillis()
        val expired = _cards.value.filter { now - it.receivedAt > CARD_LIFETIME_MS }
        expired.forEach { dismissedKeys.add(keyOf(it.eew.EventID, it.eew.Serial)) }
        _cards.update { current ->
            current.filterNot { now - it.receivedAt > CARD_LIFETIME_MS }
        }
    }

    fun removeCard(eventId: String, serial: Int) {
        dismissedKeys.add(keyOf(eventId, serial))
        _cards.update { current ->
            current.filterNot { it.eew.EventID == eventId && it.eew.Serial == serial }
        }
    }

    fun markFetched() {
        _lastUpdated.value = System.currentTimeMillis()
    }

    /**
     * @return 実際にカードが新規追加/更新された場合はtrue。通知要否の判定に使う。
     */
    fun onEewReceived(eew: JmaEew): Boolean {
        recordHistory(eew)

        if (keyOf(eew.EventID, eew.Serial) in dismissedKeys) {
            return false
        }

        val current = _cards.value
        val existingIndex = current.indexOfFirst { it.eew.EventID == eew.EventID }

        if (existingIndex >= 0) {
            val existing = current[existingIndex]
            if (eew.Serial <= existing.eew.Serial) {
                return false
            }
            _cards.value = current.toMutableList().also { it[existingIndex] = EewCardState(eew) }
        } else {
            _cards.value = listOf(EewCardState(eew)) + current
        }

        return true
    }

    fun updateConnectionStatus(status: ConnectionStatus) {
        _connectionStatus.value = status
        _lastUpdated.value = System.currentTimeMillis()
    }

    fun injectTestCard(eew: JmaEew) {
        onEewReceived(eew)
    }

    fun clearAll() {
        _cards.value = emptyList()
    }
}
