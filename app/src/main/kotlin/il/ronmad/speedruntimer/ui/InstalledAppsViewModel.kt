package il.ronmad.speedruntimer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import il.ronmad.speedruntimer.MyApplication
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Gets the list of installed apps in a background thread.
 * PackageManager is slow, so this must be done off the main thread
 * for fast app startup.
 */
class InstalledAppsViewModel(application: Application) : AndroidViewModel(application) {

    private val _setupDone = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val setupDone: SharedFlow<Unit> = _setupDone.asSharedFlow()

    fun setupInstalledAppsMap() {
        viewModelScope.launch {
            getApplication<MyApplication>().setupInstalledAppsMap()
            _setupDone.emit(Unit)
        }
    }
}
