package com.menulango.feature.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menulango.core.design.FoodGroup
import com.menulango.core.design.Paper
import com.menulango.core.design.Space
import com.menulango.core.ui.PrimaryButton
import com.menulango.core.ui.QuietButton
import com.menulango.core.ui.felt
import com.menulango.resources.Res
import com.menulango.resources.welcome_body
import com.menulango.resources.welcome_camera_note
import com.menulango.resources.welcome_open_camera
import com.menulango.resources.welcome_sample
import com.menulango.resources.welcome_step_choose
import com.menulango.resources.welcome_step_photo
import com.menulango.resources.welcome_step_read
import com.menulango.resources.welcome_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The first thing a new diner sees, instead of a camera and a permission prompt out of nowhere:
 * what MenuLango does, shown by the reading-menu illustration, in three steps, and why the camera
 * is about to be asked for. One tap opens the camera; the other shows a real example first.
 */
@Composable
internal fun Welcome(
    onOpenCamera: () -> Unit,
    onSample: () -> Unit,
    bottomInset: Dp,
) {
    val colors = Paper.colors
    Box(Modifier.fillMaxSize().felt(colors.paper), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = Space.readingWidth)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = Space.gutter)
                .padding(bottom = bottomInset + Space.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PlateCluster()
            Text(
                stringResource(Res.string.welcome_title),
                style = Paper.type.hero,
                color = colors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(Space.sm))
            Text(
                stringResource(Res.string.welcome_body),
                style = Paper.type.body,
                color = colors.inkMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Space.section))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Step("📸", FoodGroup.Sea, Res.string.welcome_step_photo)
                Step("🍽️", FoodGroup.Grill, Res.string.welcome_step_read)
                Step("🧭", FoodGroup.Garden, Res.string.welcome_step_choose)
            }
            Spacer(Modifier.height(Space.section))
            PrimaryButton(stringResource(Res.string.welcome_open_camera), onOpenCamera, Modifier.fillMaxWidth())
            Spacer(Modifier.height(Space.xs))
            QuietButton(stringResource(Res.string.welcome_sample), onSample, color = colors.sealInk, singleLine = true)
            Spacer(Modifier.height(Space.sm))
            Text(
                stringResource(Res.string.welcome_camera_note),
                style = Paper.type.caption,
                color = colors.inkFaint,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Step(
    emoji: String,
    tint: FoodGroup,
    text: StringResource,
) {
    val colors = Paper.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).felt(colors.food(tint)),
            contentAlignment = Alignment.Center,
        ) {
            Text(emoji, fontSize = 22.sp, modifier = Modifier.clearAndSetSemantics { })
        }
        Spacer(Modifier.size(Space.md))
        Text(stringResource(text), style = Paper.type.body, color = colors.ink)
    }
}
