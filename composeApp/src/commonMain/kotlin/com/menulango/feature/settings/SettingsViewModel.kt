package com.menulango.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.menulango.data.billing.BillingRepository
import com.menulango.data.billing.PurchaseOutcome
import com.menulango.data.menu.MenuRepository
import com.menulango.data.preferences.Appearance
import com.menulango.data.preferences.Preferences
import com.menulango.di.AppConfig
import com.menulango.feature.menu.DishFilter
import com.menulango.feature.menu.toFilters
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal data class SettingsUiState(
    val isPlus: Boolean,
    val appearance: Appearance,
    val dietary: Set<DishFilter>,
    val avoid: Set<String> = emptySet(),
    val showFeatured: Boolean = true,
    val restoring: Boolean = false,
)

/** A one-off message for the snackbar. */
internal enum class SettingsMessage { Restored, NothingToRestore, RestoreFailed, MenusDeleted }

internal class SettingsViewModel(
    private val preferences: Preferences,
    private val billing: BillingRepository,
    private val repository: MenuRepository,
    config: AppConfig,
) : ViewModel() {
    /** Test builds only: the sample menu and a free Plus switch live in Settings. */
    val showsTestTools: Boolean = config.showsTestTools

    fun setTestPlus(on: Boolean) = billing.setDebugUnlock(on)

    private val messages = MutableSharedFlow<SettingsMessage>(extraBufferCapacity = 1)
    val message: SharedFlow<SettingsMessage> = messages.asSharedFlow()

    val uiState: StateFlow<SettingsUiState> =
        combine(
            billing.isPlus,
            preferences.appearance,
            preferences.dietary,
            preferences.avoid,
            preferences.showFeatured,
        ) { isPlus, appearance, dietary, avoid, featured ->
            SettingsUiState(isPlus, appearance, dietary.toFilters(), avoid, featured)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            SettingsUiState(billing.isPlus.value, preferences.appearance.value, preferences.dietary.value.toFilters()),
        )

    fun setAppearance(value: Appearance) = preferences.setAppearance(value)

    fun toggleDietary(filter: DishFilter) {
        val current = preferences.dietary.value
        preferences.setDietary(if (filter.name in current) current - filter.name else current + filter.name)
    }

    fun setShowFeatured(value: Boolean) = preferences.setShowFeatured(value)

    fun addAvoid(word: String) {
        val clean = word.trim().lowercase().take(MAX_WORD)
        if (clean.isNotEmpty()) preferences.setAvoid(preferences.avoid.value + clean)
    }

    fun removeAvoid(word: String) = preferences.setAvoid(preferences.avoid.value - word)

    fun restore() {
        viewModelScope.launch {
            val outcome = billing.restore()
            messages.emit(
                when (outcome) {
                    PurchaseOutcome.Unlocked -> SettingsMessage.Restored
                    PurchaseOutcome.NothingToRestore -> SettingsMessage.NothingToRestore
                    else -> SettingsMessage.RestoreFailed
                },
            )
        }
    }

    fun deleteSavedMenus() {
        viewModelScope.launch {
            repository.forgetAll()
            messages.emit(SettingsMessage.MenusDeleted)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val MAX_WORD = 30
    }
}
