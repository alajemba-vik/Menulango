package com.menulango.data.menu.remote

import kotlinx.serialization.Serializable

/**
 * The wire shape of the proxy response, exactly as the model produces it.
 *
 * Every field is nullable with a default because this is untrusted input: the DTO's only job is
 * to parse without throwing, so that [MenuResponseParser] can decide what to keep. Nothing here
 * may reach the UI directly.
 */
@Serializable
internal data class ScanResponseDto(
    val menu: MenuMetaDto? = null,
    val dishes: List<DishDto>? = null,
    val error: String? = null,
)

@Serializable
internal data class MenuMetaDto(
    val language: String? = null,
    val languageTag: String? = null,
    val currency: String? = null,
    val venueType: String? = null,
    /** Only when printed on the menu; the model is told never to guess one. */
    val restaurantName: String? = null,
    val truncated: Boolean? = null,
    val confidence: Double? = null,
)

@Serializable
internal data class DishDto(
    val id: String? = null,
    val originalName: String? = null,
    val readableName: String? = null,
    val section: String? = null,
    val whatItIs: String? = null,
    val ingredients: List<String>? = null,
    val howItIsMade: String? = null,
    val pitch: String? = null,
    val price: PriceDto? = null,
    val flags: FlagsDto? = null,
    val allergens: AllergensDto? = null,
    val adventureLevel: Int? = null,
    val effortLevel: Int? = null,
    val confidence: Double? = null,
    val emoji: String? = null,
    val nutrition: NutritionDto? = null,
    val wikiTitle: String? = null,
)

@Serializable
internal data class NutritionDto(
    val kcal: Int? = null,
    val proteinG: Int? = null,
    val carbsG: Int? = null,
    val fatG: Int? = null,
)

@Serializable
internal data class PriceDto(
    val amount: Double? = null,
    val currency: String? = null,
    val asPrinted: String? = null,
)

@Serializable
internal data class FlagsDto(
    val spicy: Int? = null,
    val raw: Boolean? = null,
    val offal: Boolean? = null,
    val pork: Boolean? = null,
    val vegetarian: Boolean? = null,
    val vegan: Boolean? = null,
    val large: Boolean? = null,
    val shareable: Boolean? = null,
    val localSpecialty: Boolean? = null,
    val alcohol: Boolean? = null,
    val shellfish: Boolean? = null,
    val meatWithDairy: Boolean? = null,
)

@Serializable
internal data class AllergensDto(
    val likelyContains: List<String>? = null,
    val mayContain: List<String>? = null,
    val note: String? = null,
)

/** The error codes the proxy may return instead of a menu. See `proxy/README.md`. */
internal object ProxyErrorCode {
    const val RATE_LIMITED: String = "RATE_LIMITED"
    const val UNREADABLE: String = "UNREADABLE"
    const val UPSTREAM: String = "UPSTREAM"
}
