package com.menulango.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.menulango.core.design.MenuLangoTheme
import com.menulango.core.design.Paper
import com.menulango.data.menu.model.Allergens
import com.menulango.data.menu.model.Dish
import com.menulango.data.menu.model.DishFlags
import com.menulango.data.menu.model.MenuMeta
import com.menulango.data.menu.model.Price

/** Wraps a preview in the paper theme on a page-coloured background. */
@Composable
internal fun PaperPreview(
    dark: Boolean,
    content: @Composable () -> Unit,
) {
    MenuLangoTheme(darkTheme = dark) {
        Box(Modifier.background(Paper.colors.paper)) { content() }
    }
}

/** A few real dishes, so previews show real typography rather than lorem ipsum. */
internal object PreviewDishes {
    val kokoretsi =
        Dish(
            id = "kokoretsi",
            originalName = "Κοκορέτσι",
            readableName = "Kokoretsi",
            section = "From the grill",
            whatItIs =
                "Lamb offal wrapped in intestine and grilled slowly on a spit over charcoal. " +
                    "Crisp outside, soft inside.",
            ingredients = listOf("lamb liver", "lamb heart", "lamb intestine", "lemon", "oregano"),
            howItIsMade =
                "The organ meat is cut small and left for hours in lemon and herbs. " +
                    "It is threaded onto a long spit, then the cleaned intestine is wound around it " +
                    "until it forms a thick roll. It turns over charcoal for two to three hours.",
            pitch = "The dish Greek families argue about at Easter.",
            price = Price(12.5, "EUR", "12,50€"),
            flags = DishFlags.None.copy(offal = true, large = true, shareable = true, localSpecialty = true),
            allergens = Allergens(listOf("gluten"), listOf("sulphites"), "Usually grilled beside other meats."),
            adventureLevel = 5,
            effortLevel = 5,
            confidence = 0.88,
        )

    val horiatiki =
        Dish(
            id = "horiatiki",
            originalName = "Χωριάτικη σαλάτα",
            readableName = "Greek village salad",
            section = "Salads",
            whatItIs = "Tomato, cucumber, onion, peppers and olives in olive oil, with a whole slab of feta on top.",
            ingredients = listOf("tomato", "cucumber", "red onion", "feta", "olives"),
            howItIsMade = "Nothing is cooked. The vegetables are cut into large chunks just before serving.",
            pitch = "Ripe summer tomatoes and good oil. Simple and hard to beat.",
            price = Price(8.5, "EUR", "8,50€"),
            flags = DishFlags.None.copy(vegetarian = true, shareable = true),
            allergens = Allergens(listOf("milk"), emptyList(), null),
            adventureLevel = 1,
            effortLevel = 1,
            confidence = 0.98,
        )

    val sfougato =
        horiatiki.copy(
            id = "sfougato",
            originalName = "Σφουγγάτο",
            readableName = "Sfougato",
            whatItIs = "Probably a thick baked omelette with courgette and local cheese.",
            howItIsMade = null,
            confidence = 0.45,
        )

    val all = listOf(horiatiki, kokoretsi, sfougato)
    val meta = MenuMeta(language = "el", currency = "EUR", venueType = "taverna", truncated = false, confidence = 0.93)
}
