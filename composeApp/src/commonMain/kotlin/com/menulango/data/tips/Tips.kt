package com.menulango.data.tips

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The little notes that point at what makes MenuLango different, in the order a first evening
 * meets them. Each is shown once, where it is useful, never as a carousel up front.
 */
internal enum class Tip(
    /** False for moments that are not a note: the welcome page, the swipe hint. */
    val isNote: Boolean = true,
) {
    /** Test builds only: points testers at the free Plus switch. */
    TesterSettings,
    TesterPlus,
    Scan,
    TapDish,
    AddDish,
    HelpChoose,
    Picks,

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
}

/** Which tips the diner has already read, kept between launches. Settings can bring them back. */
internal class Tips(
    private val settings: Settings,
) {
    private val seenState = MutableStateFlow(read())

    val seen: StateFlow<Set<Tip>> = seenState.asStateFlow()

    fun markSeen(tip: Tip) {
        val next = seenState.value + tip
        settings.putString(KEY, next.joinToString(SEPARATOR) { it.name })
        seenState.value = next
    }

    fun reset() {
        settings.remove(KEY)
        seenState.value = emptySet()
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
        const val SEPARATOR = ","
    }
}
