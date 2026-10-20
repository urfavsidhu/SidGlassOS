package com.sidglassos.features.dashboard

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sidglassos.core.storage.GlassSettings
import com.sidglassos.core.storage.SettingsRepository
import com.sidglassos.features.widgets.WidgetRefresh
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Preference transactions survive configuration changes and merge stale UI snapshots. */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)
    val settings = repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    var message by mutableStateOf<String?>(null)
        private set

    fun dismissMessage() { message = null }

    fun save(baseline: GlassSettings, edited: GlassSettings) {
        viewModelScope.launch {
            try {
                repository.applyEdits(baseline, edited)
                if (baseline.widgetOpacity != edited.widgetOpacity || baseline.widgetTint != edited.widgetTint) {
                    refreshWidgets()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message = "Could not save preferences. Please try again."
            }
        }
    }

    fun reset() {
        viewModelScope.launch {
            try {
                repository.reset()
                try { WidgetRefresh.resetShortcut(getApplication()) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { message = "Preferences reset. Open Glass widgets to refresh any installed cards." }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message = "Could not reset preferences. Please try again."
            }
        }
    }

    private fun refreshWidgets() {
        try { WidgetRefresh.refreshAll(getApplication()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { message = "Preferences saved. Installed widgets will update on their next refresh." }
    }
}
