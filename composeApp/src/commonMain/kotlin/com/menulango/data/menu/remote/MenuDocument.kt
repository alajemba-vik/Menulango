package com.menulango.data.menu.remote

import com.menulango.data.menu.model.Dish
import com.menulango.data.menu.model.Menu

/**
 * Writes a validated [Menu] back into the wire format for the local cache.
 *
 * The cache stores the same shape the proxy returns, so a cached menu is re-read through
 * [MenuResponseParser] like any other response. One schema, one validator.
 */
internal fun Menu.toDocument(): String {
    val dto =
        ScanResponseDto(
            menu =
                MenuMetaDto(
                    language = meta.language,
                    currency = meta.currency,
                    venueType = meta.venueType,
                    truncated = meta.truncated,
                    confidence = meta.confidence,
                ),
            dishes = dishes.map(Dish::toDto),
        )
    return MenuResponseParser.LenientJson.encodeToString(ScanResponseDto.serializer(), dto)
}

private fun Dish.toDto(): DishDto =
    DishDto(
        id = id,
        originalName = originalName,
        readableName = readableName,
        section = section,
        whatItIs = whatItIs,
        ingredients = ingredients,
        howItIsMade = howItIsMade,
        pitch = pitch,
        price = price?.let { PriceDto(amount = it.amount, currency = it.currency, asPrinted = it.asPrinted) },
        flags =
            FlagsDto(
                spicy = flags.spicy,
                raw = flags.raw,
                offal = flags.offal,
                pork = flags.pork,
                vegetarian = flags.vegetarian,
                vegan = flags.vegan,
                large = flags.large,
                shareable = flags.shareable,
                localSpecialty = flags.localSpecialty,
            ),
        allergens =
            AllergensDto(
                likelyContains = allergens.likelyContains,
                mayContain = allergens.mayContain,
                note = allergens.note,
            ),
        adventureLevel = adventureLevel,
        effortLevel = effortLevel,
        confidence = confidence,
        emoji = emoji,
    )
