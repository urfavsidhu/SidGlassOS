package com.sidglassos.features.launcher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sidglassos.core.storage.GlassSettings
import com.sidglassos.core.storage.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CatalogState(val apps: List<LaunchableApp> = emptyList(), val loading: Boolean = true, val error: String? = null, val iconGeneration: Int = 0)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)
    val catalog = AppCatalog(application)
    val settings = repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GlassSettings())
    private val mutableCatalog = MutableStateFlow(CatalogState())
    val appState = mutableCatalog.asStateFlow()
    private val mutableMessages = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val messages = mutableMessages.asSharedFlow()
    private var refreshJob: Job? = null

    fun refresh(invalidateIcons: Boolean = false) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            mutableCatalog.value = mutableCatalog.value.copy(loading = true, error = null)
            try {
                if (invalidateIcons) catalog.invalidateIcons()
                val apps = catalog.query()
                mutableCatalog.value = CatalogState(apps, loading = false, iconGeneration = mutableCatalog.value.iconGeneration + 1)
                if (apps.isNotEmpty() && !repository.settings.first().launcherInitialized) {
                    val preferred = listOf(listOf("phone", "dialer"), listOf("messages", "messaging", "mms"), listOf("browser", "chrome"), listOf("camera"))
                        .mapNotNull { names -> apps.firstOrNull { app -> names.any { app.label.contains(it, true) || app.packageName.contains(it, true) } } }
                    val initialDock = (preferred + apps).distinctBy { it.id }.take(4).map { it.id }
                    repository.update { current ->
                        if (current.launcherInitialized) current else current.copy(
                            launcherInitialized = true,
                            homeOrder = apps.take(12).map { it.id },
                            dock = initialDock,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableCatalog.value = mutableCatalog.value.copy(loading = false, error = "Apps could not be loaded. Retry after checking your device settings.")
            }
        }
    }

    private fun save(action: suspend () -> Unit) {
        viewModelScope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableMessages.emit("Layout could not be saved. Please try again.") }
        }
    }

    fun addHome(id: String) = save {
        repository.update {
            if (id in it.homeOrder) it else if (it.homeOrder.size < 240) it.copy(homeOrder = it.homeOrder + id)
            else { mutableMessages.tryEmit("Home holds up to 240 apps. Remove an app first."); it }
        }
    }
    fun removeHome(id: String) = save { repository.update { it.copy(homeOrder = it.homeOrder - id) } }
    fun addDock(id: String) = save {
        repository.update {
            if (id in it.dock) it else if (it.dock.size < 4) it.copy(dock = it.dock + id)
            else { mutableMessages.tryEmit("The dock holds four apps. Remove one first."); it }
        }
    }
    fun removeDock(id: String) = save { repository.update { it.copy(dock = it.dock - id) } }
    fun moveHome(id: String, target: String) = save { repository.update { it.copy(homeOrder = LayoutOrder.move(it.homeOrder, id, target)) } }
    fun moveHomeBy(id: String, delta: Int) = save { repository.update { it.copy(homeOrder = LayoutOrder.moveBy(it.homeOrder, id, delta)) } }
    fun assignIcon(id: String, symbol: String?) = save { repository.setIconAssignment(id, symbol) }
}
