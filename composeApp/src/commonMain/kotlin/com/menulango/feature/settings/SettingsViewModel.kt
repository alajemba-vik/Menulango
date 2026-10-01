package com.menulango.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.menulango.data.backup.BackupRestoreResult
import com.menulango.data.backup.BackupService
import com.menulango.data.billing.BillingRepository
import com.menulango.data.billing.PurchaseOutcome
import com.menulango.data.currency.COMMON_CURRENCIES
import com.menulango.data.currency.ExchangeRates
import com.menulango.data.marks.MenuMarks
import com.menulango.data.menu.MenuRepository
import com.menulango.data.preferences.AppLanguage
import com.menulango.data.preferences.Appearance
import com.menulango.data.preferences.Preferences
import com.menulango.data.preferences.StartPage
import com.menulango.di.AppConfig
import com.menulango.feature.menu.DishFilter
import com.menulango.feature.menu.toFilters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock

internal data class SettingsUiState(
    val isPlus: Boolean,
    val appearance: Appearance,
    val dietary: Set<DishFilter>,
    val avoid: Set<String> = emptySet(),
    val showFeatured: Boolean = true,
    val restoring: Boolean = false,
)

/** A one-off message for the snackbar. */
internal enum class SettingsMessage {
    Restored,
    NothingToRestore,
    RestoreFailed,
    MenusDeleted,
    BackupFailed,
    BackupRestored,
    BackupWrongPassphrase,
    BackupUnsupportedVersion,
    BackupFileNotOpened,
}

internal data class BackupShare(
    val filename: String,
    val contents: ByteArray,
)

internal class SettingsViewModel(
    private val preferences: Preferences,
    private val billing: BillingRepository,
    private val repository: MenuRepository,
    private val backup: BackupService,
    config: AppConfig,
    private val marks: MenuMarks,
    private val rates: ExchangeRates,
) : ViewModel() {
    /** Test builds only: the sample menu and a free Plus switch live in Settings. */
    val showsTestTools: Boolean = config.showsTestTools

    fun setTestPlus(on: Boolean) = billing.setDebugUnlock(on)

    /** A real subscription keeps Plus on whatever the test switch says. */
    val paidPlus: StateFlow<Boolean> = billing.hasPurchase

    private val messages = MutableSharedFlow<SettingsMessage>(extraBufferCapacity = 1)
    val message: SharedFlow<SettingsMessage> = messages.asSharedFlow()
    private val backupShares = MutableSharedFlow<BackupShare>(extraBufferCapacity = 1)
    val backupShare: SharedFlow<BackupShare> = backupShares.asSharedFlow()
    private val restoring = MutableStateFlow(false)

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

    val calmMotion: StateFlow<Boolean> = preferences.calmMotion

    fun setCalmMotion(value: Boolean) = preferences.setCalmMotion(value)

    val convertPrices: StateFlow<Boolean> = preferences.convertPrices
    val homeCurrency: StateFlow<String> = preferences.homeCurrency

    fun setConvertPrices(value: Boolean) {
        preferences.setConvertPrices(value)
        if (value) viewModelScope.launch { rates.refresh() }
    }

    fun setHomeCurrency(code: String) = preferences.setHomeCurrency(code)

    /** Every currency there is a rate for, the common ones first; just those until rates arrive. */
    fun currencyChoices(): List<String> {
        val known =
            rates.table.value
                ?.rates
                ?.keys
                .orEmpty()
        val common = COMMON_CURRENCIES.filter { known.isEmpty() || it in known }
        return common + known.sorted().filterNot { it in common }
    }

    val startPage: StateFlow<StartPage> = preferences.startPage

    val language: StateFlow<AppLanguage> = preferences.language

    fun setLanguage(value: AppLanguage) = preferences.setLanguage(value)

    fun setStartPage(value: StartPage) = preferences.setStartPage(value)

    /**
     * Whether the Plus card should write itself out this time. Decorative motion is a delight the
     * first time and a delay the tenth, so it plays at most once every few hours; in between the
     * card is simply there, already written.
     */
    val performPlusCard: Boolean =
        (Clock.System.now().toEpochMilliseconds()).let { now ->
            (now - preferences.plusCardPerformedAt >= PLUS_CARD_EVERY_MS).also {
                if (it) {
                    preferences.plusCardPerformedAt =
                        now
                }
            }
        }

    fun addAvoid(word: String) {
        val clean = word.trim().lowercase().take(MAX_WORD)
        if (clean.isNotEmpty()) preferences.setAvoid(preferences.avoid.value + clean)
    }

    fun removeAvoid(word: String) = preferences.setAvoid(preferences.avoid.value - word)

    fun restore() {
        if (restoring.value) return
        viewModelScope.launch {
            restoring.value = true
            try {
                val outcome = billing.restore()
                messages.emit(
                    when (outcome) {
                        PurchaseOutcome.Unlocked -> SettingsMessage.Restored
                        PurchaseOutcome.NothingToRestore -> SettingsMessage.NothingToRestore
                        else -> SettingsMessage.RestoreFailed
                    },
                )
            } finally {
                restoring.value = false
            }
        }
    }

    fun deleteSavedMenus() {
        viewModelScope.launch {
            repository.forgetAll()
            marks.clear()
            messages.emit(SettingsMessage.MenusDeleted)
        }
    }

    fun createBackup(passphrase: String) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.Default) { backup.export(passphrase) } }
                .onSuccess { backupShares.emit(BackupShare(BACKUP_FILENAME, it)) }
                .onFailure { messages.emit(SettingsMessage.BackupFailed) }
        }
    }

    fun restoreBackup(
        contents: ByteArray,
        passphrase: String,
    ) {
        viewModelScope.launch {
            when (withContext(Dispatchers.Default) { backup.restore(contents, passphrase) }) {
                BackupRestoreResult.Restored -> messages.emit(SettingsMessage.BackupRestored)
                BackupRestoreResult.WrongPassphraseOrDamaged -> messages.emit(SettingsMessage.BackupWrongPassphrase)
                BackupRestoreResult.UnsupportedVersion -> messages.emit(SettingsMessage.BackupUnsupportedVersion)
            }
        }
    }

    fun backupFileWasNotOpened() {
        messages.tryEmit(SettingsMessage.BackupFileNotOpened)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val MAX_WORD = 30
        const val BACKUP_FILENAME = "menulango-backup.menulango"
    }
}

private const val PLUS_CARD_EVERY_MS = 6 * 60 * 60 * 1000L
