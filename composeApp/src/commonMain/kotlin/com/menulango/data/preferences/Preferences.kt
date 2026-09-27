package com.menulango.data.preferences

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/**
 * The language MenuLango speaks: its own words, and the language new menus are explained in.
 * [System] follows the phone (or the per-app language chosen in the phone's settings).
 */
@Serializable
internal enum class AppLanguage(
    val languageTag: String?,
    /** The language's name in itself, so anyone can find their own in the list. */
    val nativeName: String,
) {
    System(null, ""),
    English("en", "English"),
    French("fr", "Français"),
    Spanish("es", "Español"),
    Arabic("ar", "العربية"),
    Chinese("zh-Hans", "简体中文"),
    Japanese("ja", "日本語"),
    Korean("ko", "한국어"),
    Russian("ru", "Русский"),
    Hindi("hi", "हिन्दी"),
}

/** Which tab the app opens on. */
@Serializable
internal enum class StartPage { Menus, Camera }

/** How the app should look, whatever the phone is set to. */
internal enum class Appearance { System, Light, Dark }

/** The preferences a diner can take with them in an encrypted backup. */
@Serializable
internal data class PreferenceBackup(
    val appearance: Appearance,
    val dietary: Set<String>,
    val avoid: Set<String>,
    val showFeatured: Boolean,
    val language: AppLanguage = AppLanguage.System,
    val calmMotion: Boolean = false,
    val startPage: StartPage = StartPage.Menus,
)

/**
 * The diner's standing choices, kept between launches.
 *
 * The dietary profile is stored as filter names, not the UI's filter type, so the data layer never
 * depends on a screen; a name that no longer exists is simply ignored when read.
 */
internal class Preferences(
    private val settings: Settings,
) {
    private val appearanceState = MutableStateFlow(readAppearance())
    private val dietaryState = MutableStateFlow(readDietary())
    private val avoidState = MutableStateFlow(readList(KEY_AVOID))
    private val featuredState = MutableStateFlow(settings.getBoolean(KEY_FEATURED, true))
    private val calmMotionState = MutableStateFlow(settings.getBoolean(KEY_CALM_MOTION, false))

    /**
     * The app's own "reduce motion", for diners who want the texture still and the cards flat
     * without changing their whole phone. Adds to the system setting, never overrides it.
     */
    val calmMotion: StateFlow<Boolean> = calmMotionState.asStateFlow()

    private val languageState =
        MutableStateFlow(
            AppLanguage.entries.firstOrNull {
                it.name == settings.getStringOrNull(KEY_LANGUAGE)
            } ?: AppLanguage.System,
        )

    val language: StateFlow<AppLanguage> = languageState.asStateFlow()

    /** The language new menus are explained in: the app's chosen language, else the phone's. */
    val contentLanguageTag: String
        get() =
            language.value.languageTag ?: androidx.compose.ui.text.intl.Locale.current
                .toLanguageTag()

    fun setLanguage(value: AppLanguage) {
        settings.putString(KEY_LANGUAGE, value.name)
        languageState.value = value
    }

    private val startPageState =
        MutableStateFlow(
            StartPage.entries.firstOrNull { it.name == settings.getStringOrNull(KEY_START) } ?: StartPage.Menus,
        )

    /** Menus by default: most returns are to a menu already read. Camera-first diners can switch. */
    val startPage: StateFlow<StartPage> = startPageState.asStateFlow()

    fun setStartPage(value: StartPage) {
        settings.putString(KEY_START, value.name)
        startPageState.value = value
    }

    /** Launches on which the diner went straight from Menus to the camera: the hint to suggest a switch. */
    var cameraFirstLaunches: Int
        get() = settings.getInt(KEY_CAMERA_FIRST, 0)
        set(value) = settings.putInt(KEY_CAMERA_FIRST, value)

    /** When the Plus card last wrote itself out, so the flourish stays rare. */
    var plusCardPerformedAt: Long
        get() = settings.getLong(KEY_PLUS_INK, 0L)
        set(value) = settings.putLong(KEY_PLUS_INK, value)

    fun setCalmMotion(value: Boolean) {
        settings.putBoolean(KEY_CALM_MOTION, value)
        calmMotionState.value = value
    }

    /** Whether menus open with "Don't leave without trying". Some diners would rather just read. */
    val showFeatured: StateFlow<Boolean> = featuredState.asStateFlow()

    fun setShowFeatured(value: Boolean) {
        settings.putBoolean(KEY_FEATURED, value)
        featuredState.value = value
    }

    val appearance: StateFlow<Appearance> = appearanceState.asStateFlow()

    /** Filters applied to every menu as it opens. The diner can still turn them off on the menu. */
    val dietary: StateFlow<Set<String>> = dietaryState.asStateFlow()

    /** The diner's own words — "coriander", "mushroom" — for dishes to leave out of every menu. */
    val avoid: StateFlow<Set<String>> = avoidState.asStateFlow()

    fun setAvoid(value: Set<String>) {
        settings.putString(KEY_AVOID, value.sorted().joinToString(LIST_SEPARATOR))
        avoidState.value = value
    }

    fun setAppearance(value: Appearance) {
        settings.putString(KEY_APPEARANCE, value.name)
        appearanceState.value = value
    }

    fun setDietary(value: Set<String>) {
        settings.putString(KEY_DIETARY, value.sorted().joinToString(SEPARATOR))
        dietaryState.value = value
    }

    fun backup(): PreferenceBackup =
        PreferenceBackup(
            appearance = appearance.value,
            dietary = dietary.value,
            avoid = avoid.value,
            showFeatured = showFeatured.value,
            language = language.value,
            calmMotion = calmMotion.value,
            startPage = startPage.value,
        )

    /** Keep stricter food choices from either device; leave an explicit appearance choice intact. */
    fun merge(incoming: PreferenceBackup) {
        setDietary(dietary.value + incoming.dietary)
        setAvoid(avoid.value + incoming.avoid)
        if (appearance.value == Appearance.System) setAppearance(incoming.appearance)
        if (language.value == AppLanguage.System) setLanguage(incoming.language)
        setShowFeatured(showFeatured.value && incoming.showFeatured)
        // A comfort setting from either device holds; a start page only fills in the default.
        if (incoming.calmMotion) setCalmMotion(true)
        if (startPage.value == StartPage.Menus) setStartPage(incoming.startPage)
    }

    private fun readAppearance(): Appearance =
        Appearance.entries.firstOrNull { it.name == settings.getStringOrNull(KEY_APPEARANCE) } ?: Appearance.System

    private fun readList(key: String): Set<String> =
        settings
            .getStringOrNull(key)
            ?.split(LIST_SEPARATOR)
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty()

    private fun readDietary(): Set<String> =
        settings
            .getStringOrNull(KEY_DIETARY)
            ?.split(SEPARATOR)
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty()

    private companion object {
        const val KEY_APPEARANCE = "prefs.appearance"
        const val KEY_DIETARY = "prefs.dietary"
        const val SEPARATOR = ","
        const val KEY_AVOID = "prefs.avoid"
        const val KEY_FEATURED = "prefs.featured"
        const val KEY_CALM_MOTION = "prefs.calmMotion"
        const val KEY_PLUS_INK = "prefs.plusInkAt"
        const val KEY_START = "prefs.startPage"
        const val KEY_LANGUAGE = "prefs.language"
        const val KEY_CAMERA_FIRST = "prefs.cameraFirstLaunches"

        /** Words may contain commas ("peppers, green"), never a line break. */
        const val LIST_SEPARATOR = "\n"
    }
}
