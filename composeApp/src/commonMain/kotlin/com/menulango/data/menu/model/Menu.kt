package com.menulango.data.menu.model

/**
 * A menu the app has read and validated.
 *
 * Exists so that every layer above the repository works with facts it can trust: by the time a
 * [Menu] is constructed, every dish in it has passed the boundary contract. This file deliberately
 * imports nothing — not Compose, not Ktor, not SQLDelight — so the domain can never leak a
 * framework concern upwards.
 */
public data class Menu(
    val meta: MenuMeta,
    val dishes: List<Dish>,
)

/**
 * What the model could tell about the menu as a whole.
 *
 * Kept separate from [Menu] because it arrives first in the stream, before any dish, and the
 * context strip ("23 dishes · Greek · avg €12") can render from it on its own.
 */
public data class MenuMeta(
    val language: String?,
    val currency: String?,
    val venueType: String?,
    val truncated: Boolean,
    val confidence: Double,
) {
    public companion object {
        /** Used while the stream has not yet produced the menu block. */
        public val Unknown: MenuMeta =
            MenuMeta(language = null, currency = null, venueType = null, truncated = false, confidence = 0.0)
    }
}

/**
 * One dish, explained.
 *
 * [whatItIs] and [howItIsMade] are nullable on purpose: when the model does not recognise a
 * regional dish it is told to say nothing rather than invent a preparation. A missing explanation
 * is recoverable at the table ("ask the waiter"); a confident wrong one is not.
 */
public data class Dish(
    val id: String,
    val originalName: String,
    val readableName: String,
    val section: String?,
    val whatItIs: String?,
    val ingredients: List<String>,
    val howItIsMade: String?,
    val pitch: String?,
    val price: Price?,
    val flags: DishFlags,
    val allergens: Allergens,
    val adventureLevel: Int,
    val effortLevel: Int,
    val confidence: Double,
) {
    /** Below this, the explanation is shown as a best guess rather than a statement. */
    public val isBestGuess: Boolean get() = confidence < CONFIDENT_THRESHOLD

    public companion object {
        public const val CONFIDENT_THRESHOLD: Double = 0.6
        public val LEVEL_RANGE: IntRange = 1..5
        public val SPICE_RANGE: IntRange = 0..3
    }
}

/** A price as the restaurant printed it, plus the number we parsed out of it. */
public data class Price(
    val amount: Double,
    val currency: String?,
    val asPrinted: String,
)

/**
 * The facts a nervous diner scans for first.
 *
 * Booleans rather than free text so the choosing modes can rank dishes locally, with no second
 * model call.
 */
public data class DishFlags(
    val spicy: Int,
    val raw: Boolean,
    val offal: Boolean,
    val pork: Boolean,
    val vegetarian: Boolean,
    val vegan: Boolean,
    val large: Boolean,
    val shareable: Boolean,
    val localSpecialty: Boolean,
) {
    public companion object {
        public val None: DishFlags =
            DishFlags(
                spicy = 0,
                raw = false,
                offal = false,
                pork = false,
                vegetarian = false,
                vegan = false,
                large = false,
                shareable = false,
                localSpecialty = false,
            )
    }
}

/**
 * What a dish is likely to contain — never what it is free from.
 *
 * The app never claims a dish is safe. It can only report likelihoods, and always sends the
 * diner back to the restaurant.
 */
public data class Allergens(
    val likelyContains: List<String>,
    val mayContain: List<String>,
    val note: String?,
) {
    public val isEmpty: Boolean get() = likelyContains.isEmpty() && mayContain.isEmpty() && note == null

    public companion object {
        public val None: Allergens = Allergens(emptyList(), emptyList(), null)
    }
}
