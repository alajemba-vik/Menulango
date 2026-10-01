package com.menulango.feature.capture

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.menulango.core.design.Motion
import com.menulango.core.design.Paper
import com.menulango.core.design.Shapes
import com.menulango.core.design.Space
import com.menulango.core.ui.BackGesture
import com.menulango.core.ui.DishPlate
import com.menulango.core.ui.FiberText
import com.menulango.core.ui.LogoSaffron
import com.menulango.core.ui.MenuLangoMark
import com.menulango.core.ui.PrimaryButton
import com.menulango.core.ui.felt
import com.menulango.core.ui.pressable
import com.menulango.data.tips.Tips
import com.menulango.di.AppConfig
import com.menulango.resources.Res
import com.menulango.resources.app_name
import com.menulango.resources.paywall_privacy
import com.menulango.resources.welcome_body
import com.menulango.resources.welcome_camera_note
import com.menulango.resources.welcome_get_started
import com.menulango.resources.welcome_how_title
import com.menulango.resources.welcome_open_camera
import com.menulango.resources.welcome_step_choose
import com.menulango.resources.welcome_step_photo
import com.menulango.resources.welcome_step_read
import com.menulango.resources.welcome_tips_toggle
import com.menulango.resources.welcome_title
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * The first thing a new diner sees, instead of a camera and a permission prompt out of nowhere.
 * Two short pages rather than one long one: a landing page that is only the mark, the name and
 * the promise, then how it works, with the note about the camera right before it is asked for.
 * The sample menu waits on the camera screen, beside the shutter, until the diner has a menu.
 */
@Composable
internal fun Welcome(
    onOpenCamera: () -> Unit,
    bottomInset: Dp,
) {
    val colors = Paper.colors
    var howItWorks by rememberSaveable { mutableStateOf(false) }
    BackGesture(enabled = howItWorks) { howItWorks = false }
    val reduceMotion = Paper.reduceMotion
    AnimatedContent(
        targetState = howItWorks,
        transitionSpec = {
            if (reduceMotion) {
                fadeIn(tween(Motion.QUICK_MS)) togetherWith fadeOut(tween(Motion.QUICK_MS))
            } else {
                val forward = targetState
                (
                    fadeIn(tween(Motion.SHEET_MS)) +
                        slideInHorizontally(tween(Motion.SHEET_MS)) { if (forward) it / 4 else -it / 4 }
                ) togetherWith
                    (
                        fadeOut(tween(Motion.QUICK_MS)) +
                            slideOutHorizontally(tween(Motion.SHEET_MS)) { if (forward) -it / 4 else it / 4 }
                    )
            }
        },
        modifier = Modifier.fillMaxSize().felt(colors.paper),
        label = "welcome",
    ) { showingSteps ->
        // At least a screen tall, so the words sit in the middle and the buttons at the bottom,
        // and still scrollable when large text makes it taller than the phone.
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = Space.readingWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = Space.gutter)
                    .padding(bottom = bottomInset + Space.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                if (showingSteps) {
                    HowItWorks(onOpenCamera)
                } else {
                    Landing(onStart = { howItWorks = true })
                }
            }
        }
    }
}

/**
 * The mark, the name and the promise, and nothing else to read. Left alone, the page shows what
 * the app does without a word: the title passes through other languages and back, and now and
 * then one plate on the table is swapped for a dish from somewhere else, one thing at a time.
 * The first touch stops it for good, and Reduce Motion never starts it.
 */
@Composable
private fun Landing(onStart: () -> Unit) {
    val colors = Paper.colors
    val name = stringResource(Res.string.app_name)
    val title = stringResource(Res.string.welcome_title)
    val reduceMotion = Paper.reduceMotion
    var touched by remember { mutableStateOf(false) }
    var titleIndex by remember { mutableStateOf(0) }
    val swapped = remember { mutableStateListOf(*Array(TablePlates.size) { false }) }
    LaunchedEffect(touched) {
        if (touched || reduceMotion) {
            titleIndex = 0
            return@LaunchedEffect
        }
        delay(IDLE_START_MS)
        var plate = 0
        while (true) {
            titleIndex = (titleIndex + 1) % (TitleElsewhere.size + 1)
            delay(IDLE_STEP_MS)
            swapped[PlateSwapOrder[plate]] = !swapped[PlateSwapOrder[plate]]
            plate = (plate + 1) % PlateSwapOrder.size
            delay(IDLE_STEP_MS)
        }
    }
    // An empty first row so the words sit centred between the top and the button.
    Spacer(Modifier.height(Space.md))
    Column(
        Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                awaitPointerEvent(PointerEventPass.Initial)
                touched = true
            }
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PlateTable(swapped)
        Spacer(Modifier.height(Space.md))
        Text(
            name,
            style = Paper.type.headline,
            color = colors.sealInk,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.section))
        // Always read aloud in the diner's language, whatever the page is showing.
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = TITLE_ROOM)
                .clearAndSetSemantics {
                    heading()
                    contentDescription = title
                },
            contentAlignment = Alignment.Center,
        ) {
            // The words gather out of the felt when the language changes: the cloth behind the
            // page rearranging itself, the way a foreign menu turns readable.
            FiberText(
                text = if (titleIndex == 0) title else TitleElsewhere[titleIndex - 1],
                style = Paper.type.hero,
                color = colors.ink,
                fiber = colors.ink,
                loose = colors.inkFaint,
                animate = !reduceMotion,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(Space.sm))
        Text(
            stringResource(Res.string.welcome_body),
            style = Paper.type.body,
            color = colors.inkMuted,
            textAlign = TextAlign.Center,
        )
    }
    Column(Modifier.fillMaxWidth().padding(top = Space.section), horizontalAlignment = Alignment.CenterHorizontally) {
        PrimaryButton(stringResource(Res.string.welcome_get_started), onStart, Modifier.fillMaxWidth())
    }
}

/**
 * How it works in three lines, then the camera. The note about photos and the privacy policy sit
 * right above the moment the camera is asked for, where they matter.
 */
@Composable
private fun HowItWorks(onOpenCamera: () -> Unit) {
    val colors = Paper.colors
    val privacyUrl = koinInject<AppConfig>().privacyPolicyUrl
    val uriHandler = LocalUriHandler.current
    // An empty first row so the steps sit centred between the top and the buttons.
    Spacer(Modifier.height(Space.md))
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PlateCluster()
        Text(
            stringResource(Res.string.welcome_how_title),
            style = Paper.type.headline,
            color = colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Space.section))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Step(1, Res.string.welcome_step_photo)
            Step(2, Res.string.welcome_step_read)
            Step(3, Res.string.welcome_step_choose)
        }
    }
    Column(Modifier.fillMaxWidth().padding(top = Space.section), horizontalAlignment = Alignment.CenterHorizontally) {
        TipsChoice()
        Spacer(Modifier.height(Space.md))
        PrimaryButton(stringResource(Res.string.welcome_open_camera), onOpenCamera, Modifier.fillMaxWidth())
        Spacer(Modifier.height(Space.sm))
        Text(
            stringResource(Res.string.welcome_camera_note),
            style = Paper.type.caption,
            color = colors.inkFaint,
            textAlign = TextAlign.Center,
        )
        privacyUrl?.let { url ->
            Text(
                stringResource(Res.string.paywall_privacy),
                style = Paper.type.caption,
                color = colors.inkMuted,
                textDecoration = TextDecoration.Underline,
                modifier =
                    Modifier
                        .clip(Shapes.chip)
                        .pressable({ uriHandler.openUri(url) })
                        .semantics { role = Role.Button }
                        .heightIn(min = Space.touchTarget)
                        .wrapContentHeight()
                        .padding(horizontal = Space.sm),
            )
        }
    }
}

/**
 * "Every dish, explained" as people elsewhere would read it: shown in turn while the landing page
 * sits idle. Written once here, not translated, because showing other languages is the point.
 */
private val TitleElsewhere =
    listOf(
        "Chaque plat, expliqué",
        "Cada plato, explicado",
        "どの料理も、よくわかる",
        "Ogni piatto, spiegato",
        "모든 요리를 알기 쉽게",
    )

/** The order plates are swapped in: round the table, never two neighbours in a row. */
private val PlateSwapOrder = listOf(0, 4, 1, 5, 2, 6, 3)

private val TITLE_ROOM = 96.dp
private const val IDLE_START_MS = 3_000L
private const val IDLE_STEP_MS = 4_200L

private val LANDING_MARK = 104.dp

/**
 * Dishes from around the world set round the mark, like plates on a table: the emoji plates people
 * like on menus, saying "food from anywhere" before a word is read. Each settles into place once,
 * a beat after the last, and then the table is still. Nothing bobs or floats.
 */
@Composable
private fun PlateTable(swapped: List<Boolean>) {
    val reduceMotion = Paper.reduceMotion
    BoxWithConstraints(Modifier.fillMaxWidth().height(TABLE_HEIGHT), contentAlignment = Alignment.Center) {
        // Laid out for a 320 dp table; narrower screens draw it a little smaller.
        val scale = minOf(1f, maxWidth / TABLE_WIDTH)
        TablePlates.forEachIndexed { index, plate ->
            val arrived = remember { Animatable(if (reduceMotion) 1f else 0f) }
            LaunchedEffect(Unit) {
                if (reduceMotion) return@LaunchedEffect
                delay(PLATE_FIRST_MS + index * PLATE_STAGGER_MS)
                arrived.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessLow))
            }
            Box(
                Modifier
                    .offset(x = plate.x * scale, y = plate.y * scale)
                    .graphicsLayer {
                        val p = arrived.value
                        alpha = p.coerceIn(0f, 1f)
                        scaleX = 0.82f + 0.18f * p
                        scaleY = 0.82f + 0.18f * p
                        translationY = (1f - p) * PLATE_RISE.toPx()
                        rotationZ = plate.tilt
                    }.clearAndSetSemantics { },
            ) {
                // A swap is a plate cleared and another set down: a quick shrink and fade out,
                // then the new dish settles in.
                AnimatedContent(
                    targetState = if (swapped.getOrElse(index) { false }) plate.other else plate.emoji,
                    transitionSpec = {
                        (
                            fadeIn(
                                tween(PLATE_SWAP_MS),
                            ) + scaleIn(tween(PLATE_SWAP_MS), initialScale = 0.85f)
                        ) togetherWith
                            (
                                fadeOut(
                                    tween(PLATE_SWAP_MS / 2),
                                ) + scaleOut(tween(PLATE_SWAP_MS / 2), targetScale = 0.85f)
                            )
                    },
                    label = "plate",
                ) { emoji -> DishPlate(emoji, size = plate.size * scale) }
            }
        }
        val mark = remember { Animatable(if (reduceMotion) 1f else 0f) }
        LaunchedEffect(Unit) {
            if (!reduceMotion) mark.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
        }
        MenuLangoMark(
            description = null,
            modifier =
                Modifier.size(LANDING_MARK * scale).graphicsLayer {
                    alpha = mark.value.coerceIn(0f, 1f)
                    scaleX = 0.9f + 0.1f * mark.value
                    scaleY = 0.9f + 0.1f * mark.value
                },
        )
    }
}

private class TablePlate(
    val emoji: String,
    /** The dish it is swapped for while the page sits idle. */
    val other: String,
    val x: Dp,
    val y: Dp,
    val size: Dp,
    val tilt: Float,
)

/** A composed arrangement, not random: bigger plates at the corners, smaller ones between. */
private val TablePlates =
    listOf(
        TablePlate("🍜", "🥙", (-112).dp, (-72).dp, 58.dp, -8f),
        TablePlate("🥟", "🧆", 0.dp, (-110).dp, 46.dp, 4f),
        TablePlate("🌮", "🍛", 112.dp, (-66).dp, 58.dp, 6f),
        TablePlate("🍝", "🥗", (-130).dp, 44.dp, 50.dp, 5f),
        TablePlate("🍣", "🍲", 128.dp, 46.dp, 52.dp, -6f),
        TablePlate("🥘", "🫕", (-60).dp, 104.dp, 46.dp, -4f),
        TablePlate("🥐", "🍱", 62.dp, 102.dp, 48.dp, 7f),
    )

private val TABLE_WIDTH = 320.dp
private val TABLE_HEIGHT = 280.dp
private val PLATE_RISE = 14.dp
private const val PLATE_FIRST_MS = 180L
private const val PLATE_STAGGER_MS = 80L
private const val PLATE_SWAP_MS = 380

/**
 * One step, numbered by hand: the digit in the same handwriting as the Plus card, with a pen
 * stroke underneath that draws itself in, one step after another. Hand-made, like the dishes'
 * plates, rather than stock icons.
 */
@Composable
private fun Step(
    number: Int,
    text: StringResource,
) {
    val colors = Paper.colors
    val reduceMotion = Paper.reduceMotion
    val stroke = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reduceMotion) return@LaunchedEffect
        delay(UNDERLINE_START_MS + (number - 1) * UNDERLINE_STAGGER_MS)
        stroke.animateTo(1f, tween(UNDERLINE_DRAW_MS, easing = Motion.standard))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(STEP_NUMBER_WIDTH)
                .drawBehind {
                    // A slightly lifting pen line under the digit, drawn left to right.
                    val y = size.height * 0.86f
                    val start = size.width * 0.12f
                    val end = size.width * 0.88f
                    val line =
                        Path().apply {
                            moveTo(start, y + 2.dp.toPx())
                            quadraticTo(size.width / 2f, y - 3.dp.toPx(), end, y - 1.dp.toPx())
                        }
                    val measure = PathMeasure().apply { setPath(line, false) }
                    val drawn = Path()
                    measure.getSegment(0f, measure.length * stroke.value, drawn, true)
                    drawPath(drawn, LogoSaffron, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number.toString(),
                style = Paper.type.hand,
                color = colors.sealInk,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
        Spacer(Modifier.size(Space.md))
        Text(stringResource(text), style = Paper.type.body, color = colors.ink)
    }
}

/**
 * Whether the app should point things out along the way. Asked here, once, and on by default as
 * Apple's and Google's own apps are; a note can also stop them, and Settings brings them back.
 */
@Composable
private fun TipsChoice() {
    val tips = koinInject<Tips>()
    val on by tips.enabled.collectAsState()
    val colors = Paper.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Shapes.card)
            .toggleable(value = on, role = Role.Switch, onValueChange = tips::setEnabled)
            .padding(vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.welcome_tips_toggle),
            style = Paper.type.body,
            color = colors.ink,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = on,
            onCheckedChange = null,
            colors =
                SwitchDefaults.colors(
                    checkedTrackColor = colors.seal,
                    checkedThumbColor = colors.onSeal,
                    uncheckedTrackColor = colors.sunk,
                    uncheckedThumbColor = colors.inkFaint,
                    uncheckedBorderColor = colors.outline,
                ),
        )
    }
}

private val STEP_NUMBER_WIDTH = 44.dp
private const val UNDERLINE_START_MS = 350L
private const val UNDERLINE_STAGGER_MS = 260L
private const val UNDERLINE_DRAW_MS = 420
