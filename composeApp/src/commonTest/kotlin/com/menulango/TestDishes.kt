package com.menulango

import com.menulango.data.menu.model.Allergens
import com.menulango.data.menu.model.Dish
import com.menulango.data.menu.model.DishFlags

/** Builds a valid dish with sensible defaults so each test states only what it cares about. */
internal fun testDish(
    id: String,
    adventure: Int = 3,
    effort: Int = 3,
    confidence: Double = 0.9,
    pitch: String? = "Worth it tonight.",
    flags: DishFlags = DishFlags.None,
): Dish =
    Dish(
        id = id,
        originalName = id.uppercase(),
        readableName = id,
        section = null,
        whatItIs = "A dish.",
        ingredients = emptyList(),
        howItIsMade = "Cooked.",
        pitch = pitch,
        price = null,
        flags = flags,
        allergens = Allergens.None,
        adventureLevel = adventure,
        effortLevel = effort,
        confidence = confidence,
    )

/** One well-formed dish object as the model returns it. */
internal fun dishJson(
    id: String,
    confidence: String = "0.9",
    amount: String = "12.5",
    adventure: String = "3",
    extra: String = "",
): String =
    """
    {
      "id": "$id",
      "originalName": "Κοκορέτσι {$id}",
      "readableName": "Dish $id",
      "section": "From the grill",
      "whatItIs": "Lamb offal grilled on a spit. It says \"crisp\" outside.",
      "ingredients": ["lamb", " ", "lemon"],
      "howItIsMade": "Turned over charcoal for three hours.",
      "pitch": "The dish families argue about.",
      "price": { "amount": $amount, "currency": "EUR", "asPrinted": "12,50€" },
      "flags": { "spicy": 0, "raw": false, "offal": true, "pork": false, "vegetarian": false,
                 "vegan": false, "large": true, "shareable": true, "localSpecialty": true },
      "allergens": { "likelyContains": ["gluten"], "mayContain": [], "note": "Grilled beside other meats." },
      "adventureLevel": $adventure,
      "effortLevel": 5,
      "confidence": $confidence
      $extra
    }
    """.trimIndent()

internal fun menuDocument(vararg dishes: String): String =
    """
    {
      "menu": { "language": "el", "currency": "EUR", "venueType": "taverna", "truncated": false, "confidence": 0.92 },
      "dishes": [${dishes.joinToString(",")}]
    }
    """.trimIndent()
