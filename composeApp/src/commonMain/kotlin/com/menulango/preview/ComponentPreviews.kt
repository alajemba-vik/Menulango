package com.menulango.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.menulango.core.design.PaperIcons
import com.menulango.core.design.Space
import com.menulango.core.result.AppError
import com.menulango.core.ui.DishRowPlaceholder
import com.menulango.core.ui.ErrorMessage
import com.menulango.core.ui.FlagChips
import com.menulango.core.ui.Hairline
import com.menulango.core.ui.PrimaryButton
import com.menulango.core.ui.QuietButton
import com.menulango.core.ui.SecondaryButton
import com.menulango.core.ui.SectionLabel
import com.menulango.core.ui.chips
import com.menulango.feature.dish.AllergenPanel

@Composable
private fun Components() {
    Column(Modifier.padding(Space.gutter), verticalArrangement = Arrangement.spacedBy(Space.md)) {
        SectionLabel("What it is")
        FlagChips(PreviewDishes.kokoretsi.chips(limit = 6))
        PrimaryButton("Start my Trip Pass", {})
        SecondaryButton("Choose a photo", {}, icon = PaperIcons.Gallery)
        QuietButton("Restore purchases", {})
        Hairline()
        DishRowPlaceholder()
        AllergenPanel(PreviewDishes.kokoretsi.allergens)
    }
}

@Preview
@Composable
private fun ComponentsLight() = PaperPreview(dark = false) { Components() }

@Preview
@Composable
private fun ComponentsDark() = PaperPreview(dark = true) { Components() }

@Preview
@Composable
private fun ErrorStateLight() =
    PaperPreview(dark = false) {
        ErrorMessage(AppError.Offline) { PrimaryButton("Try again", {}) }
    }

@Preview
@Composable
private fun ErrorStateDark() =
    PaperPreview(dark = true) {
        ErrorMessage(AppError.Offline) { PrimaryButton("Try again", {}) }
    }
