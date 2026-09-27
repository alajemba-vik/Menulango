package com.menulango.data.search

/**
 * A price inside a search, typed the way people say it: "under 15", "< 15", "10-25",
 * "chicken under 20", "moins de 12", "15以下". The price part becomes a range; whatever is left
 * is searched as text, so "pepper under 12" means both at once.
 *
 * Words are recognised in every language the app speaks; symbols (<, >, ≤, ≥, a dash between
 * two numbers) work in any language.
 */
internal data class PriceQuery(
    val text: String,
    val min: Double? = null,
    val max: Double? = null,
) {
    val hasPrice: Boolean get() = min != null || max != null

    fun allows(price: Double?): Boolean {
        if (!hasPrice) return true
        if (price == null) return false
        return (min == null || price >= min) && (max == null || price <= max)
    }

    companion object {
        private const val NUMBER = """(\d+(?:[.,]\d{1,2})?)"""

        /** Currency marks around a number are ignored: "€15", "15 €", "£15", "15元". */
        private const val MONEY = """[€$£¥₹₩₽元円원]?\s*"""
        private const val MONEY_AFTER = """\s*[€$£¥₹₩₽元円원]?"""

        private val UNDER_BEFORE =
            listOf(
                "under",
                "below",
                "less than",
                "cheaper than",
                "up to",
                "max",
                "at most",
                "moins de",
                "sous",
                "jusqu'à",
                "jusqu’à",
                "maximum",
                "menos de",
                "hasta",
                "máximo",
                "maximo",
                "por debajo de",
                "أقل من",
                "تحت",
                "حتى",
                "低于",
                "少于",
                "不超过",
                "до",
                "меньше",
                "дешевле",
                "не дороже",
            )
        private val OVER_BEFORE =
            listOf(
                "over",
                "above",
                "more than",
                "from",
                "at least",
                "plus de",
                "au-dessus de",
                "dès",
                "à partir de",
                "más de",
                "desde",
                "mas de",
                "أكثر من",
                "فوق",
                "من",
                "高于",
                "超过",
                "多于",
                "от",
                "больше",
                "дороже",
            )
        private val UNDER_AFTER = listOf("or less", "以下", "以内", "未満", "이하", "미만", "से कम", "तक")
        private val OVER_AFTER = listOf("or more", "plus", "以上", "이상", "초과", "से ज़्यादा", "से अधिक")

        fun parse(query: String): PriceQuery {
            // A range first: "10-25", "10 – 25", "10 to 25".
            Regex(
                """$MONEY$NUMBER$MONEY_AFTER\s*(?:-|–|—|to|à|a|~|〜)\s*$MONEY$NUMBER$MONEY_AFTER""",
                RegexOption.IGNORE_CASE,
            ).find(query)
                ?.let { match ->
                    val a = match.groupValues[1].toAmount()
                    val b = match.groupValues[2].toAmount()
                    if (a != null &&
                        b != null
                    ) {
                        return PriceQuery(query.removeRange(match.range).tidy(), minOf(a, b), maxOf(a, b))
                    }
                }
            symbol(query, """(?:<=|≤|<)""", under = true)?.let { return it }
            symbol(query, """(?:>=|≥|>)""", under = false)?.let { return it }
            words(query, UNDER_BEFORE, before = true, under = true)?.let { return it }
            words(query, OVER_BEFORE, before = true, under = false)?.let { return it }
            words(query, UNDER_AFTER, before = false, under = true)?.let { return it }
            words(query, OVER_AFTER, before = false, under = false)?.let { return it }
            return PriceQuery(query)
        }

        private fun symbol(
            query: String,
            mark: String,
            under: Boolean,
        ): PriceQuery? {
            val match = Regex("""$mark\s*$MONEY$NUMBER$MONEY_AFTER""").find(query) ?: return null
            val amount = match.groupValues[1].toAmount() ?: return null
            return bounded(query.removeRange(match.range), amount, under)
        }

        private fun words(
            query: String,
            phrases: List<String>,
            before: Boolean,
            under: Boolean,
        ): PriceQuery? {
            // Longest phrases first, so "less than" wins over a stray "less".
            for (phrase in phrases.sortedByDescending { it.length }) {
                val p = Regex.escape(phrase)
                val pattern =
                    if (before) {
                        """(?<![\p{L}])$p\s*$MONEY$NUMBER$MONEY_AFTER"""
                    } else {
                        """$MONEY$NUMBER$MONEY_AFTER\s*$p(?![\p{L}])"""
                    }
                val match = Regex(pattern, RegexOption.IGNORE_CASE).find(query) ?: continue
                val amount = match.groupValues[1].toAmount() ?: continue
                return bounded(query.removeRange(match.range), amount, under)
            }
            return null
        }

        private fun bounded(
            rest: String,
            amount: Double,
            under: Boolean,
        ) = if (under) PriceQuery(rest.tidy(), max = amount) else PriceQuery(rest.tidy(), min = amount)

        private fun String.toAmount(): Double? = replace(',', '.').toDoubleOrNull()

        private fun String.tidy(): String = trim().replace(Regex("""\s+"""), " ")
    }
}
