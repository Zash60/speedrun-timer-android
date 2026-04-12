package il.ronmad.speedruntimer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.ronmad.speedruntimer.web.Src
import il.ronmad.speedruntimer.web.SrcLeaderboard
import il.ronmad.speedruntimer.web.Success
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class GameInfoViewModel : ViewModel() {

    private val _leaderboards = MutableStateFlow<List<SrcLeaderboard>?>(null)
    val leaderboards: StateFlow<List<SrcLeaderboard>?> = _leaderboards.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    fun refreshInfo(gameName: String) {
        viewModelScope.launch {
            try {
                _isRefreshing.value = true
                val result = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    Src().fetchLeaderboardsForGame(gameName)
                }
                when (result) {
                    is Success -> {
                        _leaderboards.value = result.value
                        if (result.value.isEmpty()) {
                            emitToast("No info available for this game")
                        }
                    }
                    else -> emitToast("No info available for this game")
                }
            } catch (_: IOException) {
                delay(1000)
                emitToast("Connection error")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private suspend fun emitToast(message: String) {
        _toastMessage.emit(message)
    }
}
