package com.menulango.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.menulango.core.design.Elevation
import com.menulango.core.design.FoodGroup
import com.menulango.core.design.Paper
import com.menulango.data.menu.model.Dish
import com.menulango.data.menu.model.emoji
import kotlin.random.Random

/**
 * A matte stoneware dish with the dish's emoji on it — the app's stand-in for food photography.
 * Grey clay rather than white china, and a soft square rather than a circle, like the hand-thrown
 * dishes modern kitchens plate on: a slightly darker well, a pressed inner lip and iron speckles.
 * Decorative: screen readers skip it, the dish name says it all.
 */
@Composable
internal fun DishPlate(
    emoji: String,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
) {
    val colors = Paper.colors
    val fontSize = with(LocalDensity.current) { (size * 0.5f).toSp() }
    val clay = if (colors.isDark) STONE_DARK else STONE_LIGHT
    val speck = if (colors.isDark) Color.White else Color(0xFF6E5D4E)
    val seed = emoji.hashCode()
    val shape = RoundedCornerShape(percent = DISH_CORNER_PERCENT)
    Box(
        modifier
            .size(size)
            .shadow(Elevation.resting, shape, clip = false)
            .clip(shape)
            .drawWithCache {
                val w = this.size.width
                val lip = w * LIP
                val corner = CornerRadius(w * DISH_CORNER_PERCENT / 100f * 0.8f)
                val random = Random(seed)
                val specks =
                    List(SPECKS) {
                        Triple(
                            Offset(random.nextFloat() * w, random.nextFloat() * w),
                            (0.35f + random.nextFloat() * 0.55f) * density,
                            0.14f + random.nextFloat() * 0.24f,
                        )
                    }
                onDrawBehind {
                    drawRect(clay)
                    // The well sits a shade deeper than the lip.
                    drawRoundRect(
                        Color.Black.copy(alpha = if (colors.isDark) 0.16f else 0.05f),
                        topLeft = Offset(lip, lip),
                        size = Size(w - lip * 2, w - lip * 2),
                        cornerRadius = corner,
                    )
                    drawRoundRect(
                        Color.Black.copy(alpha = if (colors.isDark) 0.28f else 0.09f),
                        topLeft = Offset(lip, lip),
                        size = Size(w - lip * 2, w - lip * 2),
                        cornerRadius = corner,
                        style = Stroke(width = 1f * density),
                    )
                    specks.forEach { (at, r, alpha) -> drawCircle(speck.copy(alpha = alpha), r, at) }
                }
            }.clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = fontSize)
    }
}

/** Grey clay: warm enough to sit on cream felt and every food colour, never white. */
private val STONE_LIGHT = Color(0xFFDCD6CE)
private val STONE_DARK = Color(0xFF3A3431)
private const val DISH_CORNER_PERCENT = 32
private const val LIP = 0.12f
private const val SPECKS = 26

@Composable
internal fun DishPlate(
    dish: Dish,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
) = DishPlate(dish.emoji(), modifier, size)

/** The kind of food a dish is, read from its emoji, so the colour always agrees with the plate. */
internal fun Dish.foodGroup(): FoodGroup = foodGroupOf(emoji())

internal fun foodGroupOf(emoji: String): FoodGroup =
    FOOD_GROUPS.entries.firstOrNull { (_, emojis) -> emoji.replace(VARIATION_SELECTOR, "") in emojis }?.key
        ?: FoodGroup.Hearth

private const val VARIATION_SELECTOR = "\uFE0F"

private val FOOD_GROUPS: Map<FoodGroup, Set<String>> =
    mapOf(
        FoodGroup.Sea to setOf("🐙", "🦑", "🍤", "🦀", "🦞", "🦪", "🐟", "🐠", "🐡", "🍣", "🍥", "🦐"),
        FoodGroup.Garden to
            setOf("🥗", "🥦", "🍆", "🍅", "🍄", "🫒", "🥣", "🥑", "🥕", "🌽", "🥒", "🫑", "🥬", "🌶", "🧆", "🫛"),
        FoodGroup.Grill to setOf("🍖", "🥩", "🍗", "🥓", "🍔", "🌮", "🥙", "🌭", "🍢", "🌯"),
        FoodGroup.Sweet to
            setOf(
                "🍰",
                "🥧",
                "🍨",
                "🍫",
                "🍓",
                "🥞",
                "🍩",
                "🍪",
                "🧁",
                "🍮",
                "🍦",
                "🍯",
                "🍑",
                "🍒",
                "🍎",
                "🍉",
                "🍋",
                "🍧",
                "🎂",
            ),
        FoodGroup.Drink to
            setOf("☕", "🍵", "🍷", "🍺", "🍸", "🧃", "🥤", "🍹", "🥂", "🍶", "🧉", "🥛", "🍾", "🍻", "🧋"),
    )
