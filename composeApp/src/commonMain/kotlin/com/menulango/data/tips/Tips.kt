package com.menulango.data.tips

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The little notes that point at what makes MenuLango different, in the order a first evening
 * meets them. Each is shown once, where it is useful, never as a carousel up front.
 *
 * Only for what the screen can't say for itself: a hidden gesture, a purpose that isn't obvious,
 * a habit worth suggesting. A labelled button, a "+" or a pencil gets no note.
 */
internal enum class Tip(
    /** False for moments that are not a note: the welcome page, the swipe hint. */
    val isNote: Boolean = true,
) {
    /** Test builds only: points testers at the free Plus switch. */
    TesterSettings,
    TesterPlus,
    Picks,

    /** Shown only after scrolling back and forth over a long menu without picking: swipe to hide. */
    HideDish,

    /** Suggested only to diners who keep going straight to the camera: points at Settings... */
    StartOnCamera,

    /** ...and then at the "Open the app on" control itself. */
    StartOnCameraHere,

    /** Not a note: the first saved menu slides aside once to show it can be swiped away. */
    SwipeToDelete(isNote = false),

    /** Not a note: the page that introduces the app before the camera is ever opened. */
    Welcome(isNote = false),

    /** Not a note: the one-time message after the first pick, saying notes exist. */
    NoteHint(isNote = false),

    /** Not a note: the "add another page" card at the end of a menu, until pages are first added. */
    AddPageCard(isNote = false),

    /** Not a note: the card inside the picks sheet the first time a guest joins the table. */
    GuestAdded(isNote = false),

    /** Not a note: the info mark beside Restore purchases, until its explanation has been read. */
    RestoreInfo(isNote = false),
    ;

    /** Shown only in test builds, never to real diners. */
    val isTesterOnly: Boolean get() = this == TesterSettings || this == TesterPlus
}

/** Which tips the diner has already read, kept between launches. Settings can bring them back. */
internal class Tips(
    private val settings: Settings,
) {
    private val seenState = MutableStateFlow(read())
    private val enabledState = MutableStateFlow(settings.getBoolean(KEY_ENABLED, true))

    val seen: StateFlow<Set<Tip>> = seenState.asStateFlow()

    /** Whether notes appear at all. The diner chooses on the welcome page, on a note, or in Settings. */
    val enabled: StateFlow<Boolean> = enabledState.asStateFlow()

    fun setEnabled(on: Boolean) {
        settings.putBoolean(KEY_ENABLED, on)
        enabledState.value = on
    }

    fun markSeen(tip: Tip) {
        val next = seenState.value + tip
        settings.putString(KEY, next.joinToString(SEPARATOR) { it.name })
        seenState.value = next
    }

    /** Tips by name, so a backup survives tips being added or retired. */
    fun backup(): Set<String> = seenState.value.map { it.name }.toSet()

    /** Restoring onto a new phone shouldn't re-teach what the diner already knows. */
    fun merge(names: Set<String>) {
        Tip.entries.filter { it.name in names }.forEach(::markSeen)
    }

    /**
     * Whether any note is still to come. Once all have been read the Tips switch shows off, so
     * turning it on plainly means "show them again". Tester notes count only in test builds.
     */
    fun notesLeft(
        seen: Set<Tip>,
        testBuild: Boolean,
    ): Boolean = Tip.entries.any { it.isNote && it !in seen && (testBuild || !it.isTesterOnly) }

    /** Every note again, from the start: what "Tips" switched back on in Settings does. */
    fun reset() {
        settings.remove(KEY)
        seenState.value = emptySet()
        setEnabled(true)
    }

    private fun read(): Set<Tip> {
        val names =
            settings
                .getStringOrNull(KEY)
                ?.split(SEPARATOR)
                .orEmpty()
                .toSet()
        return Tip.entries.filter { it.name in names }.toSet()
    }

    private companion object {
        const val KEY = "tips.seen"
        const val KEY_ENABLED = "tips.enabled"
        const val SEPARATOR = ","
    }
}
