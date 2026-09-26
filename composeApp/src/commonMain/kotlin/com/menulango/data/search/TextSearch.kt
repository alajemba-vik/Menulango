package com.menulango.data.search

/**
 * Local, forgiving text search: case and common accents are ignored ("creme" finds "Crème") and
 * every word typed must appear somewhere in the searched text, in any order.
 */
internal object TextSearch {
    fun matches(
        query: String,
        fields: List<String?>,
    ): Boolean {
        val words = fold(query).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return true
        val haystack = fields.filterNotNull().joinToString(" ") { fold(it) }
        return words.all { haystack.contains(it) }
    }

    /** Lowercase, accents folded, punctuation turned into spaces. */
    fun fold(text: String): String =
        text
            .lowercase()
            .map { ACCENTS[it] ?: it }
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")

    private val ACCENTS: Map<Char, Char> =
        buildMap {
            "àáâãäåā".forEach { put(it, 'a') }
            "çćč".forEach { put(it, 'c') }
            "èéêëēė".forEach { put(it, 'e') }
            "ìíîïī".forEach { put(it, 'i') }
            "ñń".forEach { put(it, 'n') }
            "òóôõöøō".forEach { put(it, 'o') }
            "ùúûüū".forEach { put(it, 'u') }
            "ýÿ".forEach { put(it, 'y') }
            "šś".forEach { put(it, 's') }
            "žźż".forEach { put(it, 'z') }
        }
}

/** What a menu search looks at beyond the dish's name, only when the diner adds it as a tag. */
internal enum class DishSearchTag { Ingredients, Description }

internal data class DishSearch(
    /** Whether the search field is showing; it stays out of the way until the icon is tapped. */
    val open: Boolean = false,
    val query: String = "",
    val tags: Set<DishSearchTag> = emptySet(),
) {
    val isActive: Boolean get() = query.isNotBlank()
}
