package com.menulango.data.menu.remote

import com.menulango.core.result.AppError
import com.menulango.core.result.AppResult
import com.menulango.data.menu.model.Allergens
import com.menulango.data.menu.model.Dish
import com.menulango.data.menu.model.DishFlags
import com.menulango.data.menu.model.Menu
import com.menulango.data.menu.model.MenuMeta
import com.menulango.data.menu.model.Price
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Thrown in debug builds when the model breaks the response contract.
 *
 * Exists so a bad prompt change is impossible to miss during development. Release builds never
 * see it: they drop the offending dish and carry on.
 */
internal class MenuContractViolation(
    reason: String,
) : IllegalStateException("Model response broke the contract: $reason")

/**
 * The boundary between untrusted model output and the domain.
 *
 * Exists because the model is an unreliable narrator: it can omit fields, invent levels outside
 * the scale, or return a negative price. Every dish is validated here, individually, so one bad
 * dish costs one row rather than the whole menu. Nothing unvalidated gets past this class.
 *
 * @param failLoudly true in debug builds: any violation throws [MenuContractViolation].
 */
internal class MenuResponseParser(
    private val failLoudly: Boolean,
    private val json: Json = LenientJson,
) {
    /** Parses one dish object as it arrives from the stream. Returns null if it was dropped. */
    fun parseDish(dishJson: String): Dish? =
        guard("dish") {
            val dto = json.decodeFromString(DishDto.serializer(), dishJson)
            dto.toDomain()
        }

    /** Parses the `menu` block. A broken block degrades to [MenuMeta.Unknown] rather than failing. */
    fun parseMeta(metaJson: String): MenuMeta =
        guard("menu") {
            json.decodeFromString(MenuMetaDto.serializer(), metaJson).toDomain()
        } ?: MenuMeta.Unknown

    /**
     * Parses a complete response document.
     *
     * Dishes are decoded one by one from the raw JSON tree so that a type error in one dish
     * cannot take the rest down with it.
     */
    fun parseDocument(document: String): AppResult<Menu> {
        val root =
            try {
                json.parseToJsonElement(document) as? JsonObject
            } catch (e: SerializationException) {
                if (failLoudly) throw MenuContractViolation("not JSON: ${e.message}")
                null
            } ?: return AppResult.Err(AppError.Malformed)

        root["error"]?.let { return AppResult.Err(proxyError(it)) }

        val meta = root["menu"]?.let { parseMeta(it.toString()) } ?: MenuMeta.Unknown
        val rawDishes = (root["dishes"] as? JsonArray).orEmpty()
        val assembler = MenuAssembler()
        rawDishes.forEach { element -> parseDish(element.toString())?.let(assembler::add) }

        if (rawDishes.isNotEmpty() && assembler.dishes.isEmpty()) return AppResult.Err(AppError.Malformed)
        return AppResult.Ok(Menu(meta, assembler.dishes))
    }

    /**
     * Reads `{"error": "..."}` from a failed response. Never throws: an HTML error page from a
     * load balancer is a network problem, not the model breaking its contract.
     */
    fun parseProxyError(body: String): AppError? =
        try {
            (json.parseToJsonElement(body) as? JsonObject)?.get("error")?.let(::proxyError)
        } catch (e: SerializationException) {
            null
        }

    private fun proxyError(element: JsonElement): AppError =
        when ((element as? JsonPrimitive)?.contentOrNull) {
            ProxyErrorCode.RATE_LIMITED -> AppError.RateLimited
            ProxyErrorCode.UNREADABLE -> AppError.Unreadable
            else -> AppError.Upstream
        }

    private inline fun <T> guard(
        what: String,
        block: () -> T,
    ): T? =
        try {
            block()
        } catch (e: SerializationException) {
            if (failLoudly) throw MenuContractViolation("$what is not decodable: ${e.message}")
            null
        } catch (e: IllegalArgumentException) {
            if (failLoudly) throw MenuContractViolation("$what failed validation: ${e.message}")
            null
        }

    internal companion object {
        val LenientJson: Json =
            Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
                explicitNulls = false
            }
    }
}

/**
 * Collects validated dishes in order, enforcing the rules that span more than one dish:
 * ids are unique, and a menu never exceeds [MAX_DISHES].
 */
internal class MenuAssembler {
    private val collected = mutableListOf<Dish>()
    private val seenIds = mutableSetOf<String>()

    val dishes: List<Dish> get() = collected.toList()

    /** Returns false once the cap is reached, so a runaway stream can be abandoned early. */
    fun add(dish: Dish): Boolean {
        if (collected.size >= MAX_DISHES) return false
        var id = dish.id
        var suffix = 2
        while (!seenIds.add(id)) id = "${dish.id}-${suffix++}"
        collected += if (id == dish.id) dish else dish.copy(id = id)
        return true
    }

    companion object {
        const val MAX_DISHES: Int = 60
    }
}

// Validation: each require() failure becomes one dropped dish (or a loud failure in debug).

private fun DishDto.toDomain(): Dish {
    val flags = requireNotNull(flags) { "flags missing" }
    val allergens = requireNotNull(allergens) { "allergens missing" }
    val confidence = requireNotNull(confidence) { "confidence missing" }
    require(confidence in 0.0..1.0) { "confidence $confidence outside 0..1" }
    val adventure = requireNotNull(adventureLevel) { "adventureLevel missing" }
    val effort = requireNotNull(effortLevel) { "effortLevel missing" }
    require(adventure in Dish.LEVEL_RANGE) { "adventureLevel $adventure outside ${Dish.LEVEL_RANGE}" }
    require(effort in Dish.LEVEL_RANGE) { "effortLevel $effort outside ${Dish.LEVEL_RANGE}" }

    return Dish(
        id = id.required("id"),
        originalName = originalName.required("originalName"),
        readableName = readableName.required("readableName"),
        section = section.cleaned(),
        whatItIs = whatItIs.cleaned(),
        ingredients = ingredients.cleanedList(),
        howItIsMade = howItIsMade.cleaned(),
        pitch = pitch.cleaned(),
        price = price?.toDomain(),
        flags = flags.toDomain(),
        allergens = allergens.toDomain(),
        adventureLevel = adventure,
        effortLevel = effort,
        confidence = confidence,
    )
}

private fun PriceDto.toDomain(): Price? {
    // A price the model could not read is simply absent; a negative one is a broken contract.
    val parsedAmount = amount ?: return null
    require(parsedAmount >= 0.0 && parsedAmount.isFinite()) { "price $parsedAmount is negative" }
    return Price(
        amount = parsedAmount,
        currency = currency.cleaned(),
        asPrinted = asPrinted.cleaned() ?: parsedAmount.toString(),
    )
}

private fun FlagsDto.toDomain(): DishFlags {
    val spice = spicy ?: 0
    require(spice in Dish.SPICE_RANGE) { "spicy $spice outside ${Dish.SPICE_RANGE}" }
    return DishFlags(
        spicy = spice,
        raw = raw == true,
        offal = offal == true,
        pork = pork == true,
        vegetarian = vegetarian == true,
        vegan = vegan == true,
        large = large == true,
        shareable = shareable == true,
        localSpecialty = localSpecialty == true,
    )
}

private fun AllergensDto.toDomain(): Allergens =
    Allergens(
        likelyContains = likelyContains.cleanedList(),
        mayContain = mayContain.cleanedList(),
        note = note.cleaned(),
    )

private fun MenuMetaDto.toDomain(): MenuMeta {
    val score = confidence ?: 0.0
    require(score in 0.0..1.0) { "menu confidence $score outside 0..1" }
    return MenuMeta(
        language = language.cleaned(),
        currency = currency.cleaned(),
        venueType = venueType.cleaned(),
        truncated = truncated == true,
        confidence = score,
    )
}

private fun String?.required(field: String): String = requireNotNull(cleaned()) { "$field missing or blank" }

private fun String?.cleaned(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun List<String>?.cleanedList(): List<String> = orEmpty().mapNotNull { it.cleaned() }
