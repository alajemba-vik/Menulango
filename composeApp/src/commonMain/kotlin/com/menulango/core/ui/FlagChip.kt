package com.menulango.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.menulango.core.design.Paper
import com.menulango.core.design.Shapes
import com.menulango.core.design.Space
import com.menulango.data.menu.model.Dish
import com.menulango.resources.Res
import com.menulango.resources.flag_best_guess
import com.menulango.resources.flag_large
import com.menulango.resources.flag_local
import com.menulango.resources.flag_offal
import com.menulango.resources.flag_pork
import com.menulango.resources.flag_raw
import com.menulango.resources.flag_shareable
import com.menulango.resources.flag_spicy
import com.menulango.resources.flag_vegan
import com.menulango.resources.flag_vegetarian
import com.menulango.resources.flag_very_spicy
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** How a chip is inked. Local specialities get the warm ember; everything else stays neutral. */
internal enum class ChipTone { Neutral, Ember }

internal data class DishChip(
    val text: StringResource,
    val tone: ChipTone,
)

/**
 * The facts a nervous diner looks for first, most decisive first, capped at [limit].
 *
 * Text chips, never emoji: emoji would date the app and break the paper feeling instantly.
 */
internal fun Dish.chips(limit: Int = 3): List<DishChip> =
    buildList {
        if (isBestGuess) add(DishChip(Res.string.flag_best_guess, ChipTone.Neutral))
        if (flags.offal) add(DishChip(Res.string.flag_offal, ChipTone.Neutral))
        if (flags.raw) add(DishChip(Res.string.flag_raw, ChipTone.Neutral))
        when {
            flags.spicy >= 3 -> add(DishChip(Res.string.flag_very_spicy, ChipTone.Neutral))
            flags.spicy >= 2 -> add(DishChip(Res.string.flag_spicy, ChipTone.Neutral))
        }
        if (flags.localSpecialty) add(DishChip(Res.string.flag_local, ChipTone.Ember))
        if (flags.pork) add(DishChip(Res.string.flag_pork, ChipTone.Neutral))
        when {
            flags.vegan -> add(DishChip(Res.string.flag_vegan, ChipTone.Neutral))
            flags.vegetarian -> add(DishChip(Res.string.flag_vegetarian, ChipTone.Neutral))
        }
        if (flags.large) add(DishChip(Res.string.flag_large, ChipTone.Neutral))
        if (flags.shareable) add(DishChip(Res.string.flag_shareable, ChipTone.Neutral))
    }.take(limit)

@Composable
internal fun FlagChip(
    chip: DishChip,
    modifier: Modifier = Modifier,
) {
    val colors = Paper.colors
    val (ink, wash) =
        when (chip.tone) {
            ChipTone.Neutral -> colors.inkMuted to colors.sunk
            ChipTone.Ember -> colors.ember to colors.emberWash
        }
    Text(
        text = stringResource(chip.text).uppercase(),
        style = Paper.type.label,
        color = ink,
        modifier =
            modifier
                .background(wash, Shapes.chip)
                .border(Space.hairline, if (chip.tone == ChipTone.Neutral) colors.rule else wash, Shapes.chip)
                .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

@Composable
internal fun FlagChips(
    chips: List<DishChip>,
    modifier: Modifier = Modifier,
) {
    if (chips.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        chips.forEach { FlagChip(it) }
    }
}
