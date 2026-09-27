package app.warden.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.warden.data.WardenClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * One shared, process-lifetime source of connection state.
 *
 * Each screen used to run its own 1s poll, so every tab switch re-initialised to
 * false and flashed "not running" until the next tick. This single poller keeps
 * the state across tab switches — screens just read it.
 */
object ConnState {
    var connected by mutableStateOf(false); private set
    var root by mutableStateOf(false); private set

    private var poller: Job? = null

    fun ensurePolling(scope: CoroutineScope) {
        if (poller?.isActive == true) return
        poller = scope.launch {
            while (isActive) {
                WardenClient.connect()
                connected = WardenClient.connected
                root = WardenClient.rootAvailable
                delay(1000)
            }
        }
    }
}
