package com.menulango.feature.share

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.menulango.core.design.Paper
import com.menulango.core.design.Shapes
import com.menulango.core.design.Space
import com.menulango.core.ui.PrimaryButton
import com.menulango.core.ui.SecondaryButton
import com.menulango.core.ui.SegmentedControl
import com.menulango.core.ui.paperShimmer
import com.menulango.data.menu.model.Dish
import com.menulango.data.menu.model.MenuMeta
import com.menulango.data.preferences.Preferences
import com.menulango.data.share.MenuSharing
import com.menulango.data.share.ShareLink
import com.menulango.feature.table.PICK_TOGETHER_ENABLED
import com.menulango.feature.table.PickTogether
import com.menulango.feature.table.TableSession
import com.menulango.feature.table.TableState
import com.menulango.platform.shareText
import com.menulango.resources.Res
import com.menulango.resources.action_try_again
import com.menulango.resources.menu_plus_badge
import com.menulango.resources.share_body
import com.menulango.resources.share_copied
import com.menulango.resources.share_copy
import com.menulango.resources.share_expiry
import com.menulango.resources.share_failed
import com.menulango.resources.share_link
import com.menulango.resources.share_locked_action
import com.menulango.resources.share_locked_body
import com.menulango.resources.share_making
import com.menulango.resources.share_private
import com.menulango.resources.share_qr_description
import com.menulango.resources.share_tab_link
import com.menulango.resources.share_tab_together
import com.menulango.resources.share_title
import io.github.alexzhirkevich.qrose.options.QrBallShape
import io.github.alexzhirkevich.qrose.options.QrBrush
import io.github.alexzhirkevich.qrose.options.QrColors
import io.github.alexzhirkevich.qrose.options.QrFrameShape
import io.github.alexzhirkevich.qrose.options.QrPixelShape
import io.github.alexzhirkevich.qrose.options.QrShapes
import io.github.alexzhirkevich.qrose.options.roundCorners
import io.github.alexzhirkevich.qrose.options.solid
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * "Share with your table": the menu, explained, as a link the whole table can open, and a QR code
 * for the quickest way there, since they are sitting right here. Like AirDrop's sheet: one big
 * thing to point a camera at, then the usual share and copy for everything else.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShareMenuSheet(
    title: String,
    meta: MenuMeta,
    dishes: List<Dish>,
    orderKey: String,
    currentDishes: () -> List<Dish>,
    isPlus: Boolean,
    onPlus: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Paper.colors
    val sharing = koinInject<MenuSharing>()
    val table = koinInject<TableSession>()
    val languageTag = koinInject<Preferences>().contentLanguageTag
    // Straight to "Pick together" while a table is open, so the diner lands where they left off.
    var tab by remember {
        mutableIntStateOf(
            if (!PICK_TOGETHER_ENABLED ||
                table.table.value == TableState.Idle
            ) {
                TAB_LINK
            } else {
                TAB_TOGETHER
            },
        )
    }
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<ShareState>(ShareState.Making) }
    // A link is only made for Plus, and only once the link tab is actually shown.
    LaunchedEffect(attempt, tab == TAB_LINK && isPlus) {
        if (tab != TAB_LINK || !isPlus) return@LaunchedEffect
        state = ShareState.Making
        state = sharing.share(title, languageTag, meta, dishes)?.let(ShareState::Ready) ?: ShareState.Failed
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = Shapes.sheet,
        containerColor = colors.raised,
        scrimColor = colors.scrim.copy(alpha = 0.4f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.rule) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.gutter)
                .padding(bottom = Space.gutter)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(Res.string.share_title),
                style = Paper.type.headline,
                color = colors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            if (PICK_TOGETHER_ENABLED) {
                SegmentedControl(
                    options =
                        listOf(
                            stringResource(Res.string.share_tab_link),
                            stringResource(Res.string.share_tab_together),
                        ),
                    selected = tab,
                    onSelect = { tab = it },
                    modifier = Modifier.padding(top = Space.md, bottom = Space.md),
                )
            } else {
                Spacer(Modifier.height(Space.md))
            }
            if (tab == TAB_TOGETHER) {
                PickTogether(orderKey, currentDishes, isPlus, onPlus)
            } else {
                Text(
                    stringResource(Res.string.share_body),
                    style = Paper.type.bodySmall,
                    color = colors.inkMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = Space.section),
                )
                if (!isPlus) {
                    Locked(onPlus)
                } else {
                    AnimatedContent(
                        targetState = state,
                        transitionSpec = { fadeIn(tween(FADE_MS)) togetherWith fadeOut(tween(FADE_MS)) },
                        contentAlignment = Alignment.TopCenter,
                        label = "share",
                    ) { shown ->
                        when (shown) {
                            ShareState.Making -> Making()
                            ShareState.Failed -> Failed(onRetry = { attempt++ })
                            is ShareState.Ready -> Ready(title, shown.link)
                        }
                    }
                }
            }
        }
    }
}

private sealed interface ShareState {
    data object Making : ShareState

    data object Failed : ShareState

    data class Ready(
        val link: ShareLink,
    ) : ShareState
}

/** For free diners: what the link is, and the way to it. */
@Composable
private fun Locked(onPlus: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.md)) {
        Box(
            Modifier.size(QR_CARD).clip(Shapes.card).background(Paper.colors.sunk),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(Res.string.menu_plus_badge),
                style = Paper.type.button,
                color = Paper.colors.onSeal,
                modifier =
                    Modifier
                        .background(
                            Paper.colors.seal,
                            Shapes.chip,
                        ).padding(horizontal = Space.md, vertical = Space.xs),
            )
        }
        Text(
            stringResource(Res.string.share_locked_body),
            style = Paper.type.bodySmall,
            color = Paper.colors.inkMuted,
            textAlign = TextAlign.Center,
        )
        PrimaryButton(stringResource(Res.string.share_locked_action), onPlus, Modifier.fillMaxWidth())
    }
}

@Composable
private fun Making() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(QR_CARD).clip(Shapes.card).paperShimmer())
        Text(
            stringResource(Res.string.share_making),
            style = Paper.type.caption,
            color = Paper.colors.inkMuted,
            modifier = Modifier.padding(top = Space.md).semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun Failed(onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.md)) {
        Text(
            stringResource(Res.string.share_failed),
            style = Paper.type.body,
            color = Paper.colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        PrimaryButton(stringResource(Res.string.action_try_again), onRetry, Modifier.fillMaxWidth())
    }
}

@Composable
private fun Ready(
    title: String,
    link: ShareLink,
) {
    val colors = Paper.colors

    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_MS)
            copied = false
        }
    }
    // Always dark on white, whatever the theme: that is what every camera reads fastest.
    val qr =
        rememberQrCodePainter(
            data = link.url,
            shapes =
                QrShapes(
                    darkPixel = QrPixelShape.roundCorners(),
                    ball = QrBallShape.roundCorners(BALL_ROUNDING),
                    frame = QrFrameShape.roundCorners(FRAME_ROUNDING),
                ),
            colors = QrColors(dark = QrBrush.solid(QR_INK)),
        )
    val qrDescription = stringResource(Res.string.share_qr_description)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(QR_CARD)
                .clip(Shapes.card)
                .background(Color.White)
                .padding(Space.md)
                .semantics { contentDescription = qrDescription },
        ) {
            Image(qr, contentDescription = null, modifier = Modifier.fillMaxWidth())
        }
        Text(
            link.url.removePrefix("https://"),
            style = Paper.type.caption,
            color = colors.inkMuted,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
            modifier = Modifier.padding(top = Space.sm),
        )
        Column(
            Modifier.fillMaxWidth().padding(top = Space.section),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            PrimaryButton(
                stringResource(Res.string.share_link),
                { shareText("$title\n${link.url}") },
                Modifier.fillMaxWidth(),
            )
            SecondaryButton(
                stringResource(if (copied) Res.string.share_copied else Res.string.share_copy),
                {
                    clipboard.setText(AnnotatedString(link.url))
                    copied = true
                },
                Modifier.fillMaxWidth(),
            )
        }
        Text(
            stringResource(Res.string.share_expiry),
            style = Paper.type.caption,
            color = colors.inkMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Space.md),
        )
        Text(
            stringResource(Res.string.share_private),
            style = Paper.type.caption,
            color = colors.inkFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Space.xs),
        )
    }
}

private val QR_CARD = 240.dp
private const val TAB_LINK = 0
private const val TAB_TOGETHER = 1
private val QR_INK = Color(0xFF1E1624)
private const val BALL_ROUNDING = 0.3f
private const val FRAME_ROUNDING = 0.3f
private const val FADE_MS = 220
private const val COPIED_MS = 1_800L
