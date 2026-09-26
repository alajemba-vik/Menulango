package com.menulango.feature.capture

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.menulango.MenuSource
import com.menulango.PaywallReason
import com.menulango.Route
import com.menulango.core.design.Elevation
import com.menulango.core.design.Motion
import com.menulango.core.design.Paper
import com.menulango.core.design.PaperIcons
import com.menulango.core.design.Shapes
import com.menulango.core.design.Space
import com.menulango.core.ui.DishPlate
import com.menulango.core.ui.IconAction
import com.menulango.core.ui.PrimaryButton
import com.menulango.core.ui.SecondaryButton
import com.menulango.core.ui.StateMessage
import com.menulango.core.ui.felt
import com.menulango.core.ui.paper
import com.menulango.core.ui.tipTarget
import com.menulango.data.menu.local.CachedMenuSummary
import com.menulango.data.tips.Tip
import com.menulango.feature.menu.PageInbox
import com.menulango.platform.CameraController
import com.menulango.platform.CameraState
import com.menulango.platform.CameraViewfinder
import com.menulango.platform.PickedPhoto
import com.menulango.platform.rememberPhotoPicker
import com.menulango.resources.Res
import com.menulango.resources.action_back
import com.menulango.resources.action_choose_photo
import com.menulango.resources.app_name
import com.menulango.resources.capture_add_page_title
import com.menulango.resources.capture_camera_allow
import com.menulango.resources.capture_camera_body
import com.menulango.resources.capture_camera_title
import com.menulango.resources.capture_camera_unavailable
import com.menulango.resources.capture_failed
import com.menulango.resources.capture_gallery
import com.menulango.resources.capture_hint
import com.menulango.resources.capture_hint_next_page
import com.menulango.resources.capture_last_menu
import com.menulango.resources.capture_last_menu_description
import com.menulango.resources.capture_plus
import com.menulango.resources.capture_preparing
import com.menulango.resources.capture_quota
import com.menulango.resources.capture_quota_none
import com.menulango.resources.capture_shutter
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.PI
import kotlin.math.sin

/**
 * @param addPage set when the camera is adding pages to a menu already being read; the photos go
 *   back to that menu through the [PageInbox] and [onPagesAdded] returns there.
 */
@Composable
internal fun CaptureScreen(
    navigate: (Route) -> Unit,
    addPage: Route.AddPage? = null,
    onPagesAdded: () -> Unit = {},
    bottomInset: Dp = 0.dp,
) {
    val viewModel = koinViewModel<CaptureViewModel>()
    val inbox = koinInject<PageInbox>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val camera = remember { CameraController() }
    val scope = rememberCoroutineScope()

    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }

    // New photos start a menu, or join the one being read when adding pages.
    val onPhotos: (List<ByteArray>?) -> Unit = { pages ->
        if (addPage != null && !pages.isNullOrEmpty()) {
            inbox.deliver(addPage.sessionId, pages.take(addPage.maxPages ?: pages.size))
            onPagesAdded()
        } else {
            viewModel.onPhotosReady(pages)?.let(navigate)
        }
    }
    val picker =
        rememberPhotoPicker { picked ->
            when (picked) {
                is PickedPhoto.Chosen -> onPhotos(picked.pages)
                PickedPhoto.Cancelled -> viewModel.onPhotoCancelled()
                PickedPhoto.Unreadable -> onPhotos(null)
            }
        }
    // The menu being added to has already passed the free-scan gate.
    val gate: () -> Route? = { if (addPage != null) null else viewModel.gateForScan() }

    CaptureContent(
        state = state,
        addingPage = addPage != null,
        bottomInset = bottomInset,
        camera = camera,
        viewfinder = { CameraViewfinder(camera, Modifier.fillMaxSize()) },
        actions =
            CaptureActions(
                onShutter = {
                    val blocked = gate()
                    if (blocked != null) {
                        navigate(blocked)
                    } else {
                        viewModel.onPreparingPhoto()
                        scope.launch { onPhotos(camera.capture()?.let(::listOf)) }
                    }
                },
                onGallery = { gate()?.let(navigate) ?: picker(addPage?.maxPages ?: viewModel.galleryLimit()) },
                onLastMenu = { key -> navigate(Route.Menu(MenuSource.Saved(key))) },
                onAllowance = { navigate(Route.Paywall(PaywallReason.Upgrade)) },
                onBack = if (addPage != null) onPagesAdded else null,
            ),
    )
}

internal data class CaptureActions(
    val onShutter: () -> Unit,
    val onGallery: () -> Unit,
    val onLastMenu: (String) -> Unit,
    val onAllowance: () -> Unit,
    /** Present only when adding a page: back to the menu without one. */
    val onBack: (() -> Unit)? = null,
) {
    companion object {
        val Preview = CaptureActions({}, {}, {}, {})
    }
}

/**
 * Full-bleed camera, and almost nothing else: one hint, one honest counter, the shutter where the
 * thumb rests. No tab bar, no settings gear.
 */
@Composable
internal fun CaptureContent(
    state: CaptureUiState,
    addingPage: Boolean = false,
    bottomInset: Dp = 0.dp,
    camera: CameraController,
    viewfinder: @Composable () -> Unit,
    actions: CaptureActions,
) {
    val colors = Paper.colors
    // Adding a page to a menu already being read: no counter, no "last menu", a different hint.
    val ready = (state as? CaptureUiState.Ready)?.let { if (addingPage) it.copy(lastMenu = null) else it }
    val title = stringResource(if (addingPage) Res.string.capture_add_page_title else Res.string.app_name)
    Box(Modifier.fillMaxSize().background(colors.scrim)) {
        viewfinder()

        val blocked = camera.state == CameraState.PermissionDenied || camera.state == CameraState.Unavailable
        if (blocked) {
            // Without a camera this is a page, not a viewfinder: paper, ink, and the other way in.
            CameraProblem(
                addingPage = addingPage,
                body =
                    stringResource(
                        if (camera.state == CameraState.PermissionDenied) {
                            Res.string.capture_camera_body
                        } else {
                            Res.string.capture_camera_unavailable
                        },
                    ),
                onAllow = if (camera.state == CameraState.PermissionDenied) camera::retryAccess else null,
                state = ready,
                actions = actions,
            )
            TopChrome(title, actions, Modifier.align(Alignment.TopCenter), tint = colors.ink)
            return@Box
        }
        if (camera.state == CameraState.Ready) FrameGuide(Modifier.align(Alignment.Center))

        // A soft darkening under the chrome so white text reads over any menu.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(260.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, colors.scrim.copy(alpha = 0.72f)))),
        )

        TopChrome(title, actions, Modifier.align(Alignment.TopCenter), tint = colors.onPhoto)

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = bottomInset)
                .padding(horizontal = Space.gutter, vertical = Space.gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val hint =
                when {
                    ready?.isPreparingPhoto == true -> Res.string.capture_preparing
                    ready?.photoProblem == true -> Res.string.capture_failed
                    addingPage -> Res.string.capture_hint_next_page
                    else -> Res.string.capture_hint
                }
            Text(
                stringResource(hint),
                style = Paper.type.bodySmall,
                color = colors.onPhoto,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Space.xs))
            if (!addingPage) ready?.allowance?.let { AllowanceLine(it, actions.onAllowance, colors.onPhoto) }
            Spacer(Modifier.height(Space.md))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    ChromeButton(
                        PaperIcons.Gallery,
                        stringResource(Res.string.capture_gallery),
                        null,
                        actions.onGallery,
                    )
                }
                Box(Modifier.tipTarget(Tip.Scan)) {
                    Shutter(
                        enabled = camera.state == CameraState.Ready && ready?.isPreparingPhoto != true,
                        onClick = actions.onShutter,
                    )
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    ready?.lastMenu?.let { last -> LastMenuButton(last, actions.onLastMenu) }
                }
            }
        }
    }
}

@Composable
private fun TopChrome(
    title: String,
    actions: CaptureActions,
    modifier: Modifier = Modifier,
    tint: Color,
) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Space.gutter, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.onBack?.let { onBack ->
            IconAction(
                PaperIcons.Back,
                stringResource(Res.string.action_back),
                onBack,
                tint = Paper.colors.ink,
                background = Paper.colors.raised.copy(alpha = 0.92f),
                modifier = Modifier.padding(end = Space.sm),
            )
        }
        Text(
            title,
            style = Paper.type.dishName,
            color = tint,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
    }
}

/** "2 of 3 free scans left this month" — visible before the shutter, so the wall is never a surprise. */
@Composable
private fun AllowanceLine(
    allowance: Allowance,
    onClick: () -> Unit,
    tint: Color,
) {
    val text =
        when (allowance) {
            Allowance.Plus -> {
                stringResource(Res.string.capture_plus)
            }

            is Allowance.Free -> {
                if (allowance.quota.isExhausted) {
                    stringResource(Res.string.capture_quota_none)
                } else {
                    stringResource(Res.string.capture_quota, allowance.quota.remaining, allowance.quota.allowance)
                }
            }
        }
    Text(
        text = text,
        style = Paper.type.caption,
        color = if (allowance is Allowance.Plus) tint else tint.copy(alpha = 0.75f),
        modifier =
            Modifier
                .clickable(enabled = allowance is Allowance.Free, role = Role.Button, onClick = onClick)
                .padding(horizontal = Space.related, vertical = Space.xs),
    )
}

/** A white ring around a coral button: the brand colour where the thumb lands. It presses in, never bounces. */
@Composable
private fun Shutter(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = Paper.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.92f else 1f,
        tween(Motion.QUICK_MS, easing = Motion.standard),
        label = "shutter",
    )
    val description = stringResource(Res.string.capture_shutter)
    Box(
        Modifier
            .size(76.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.45f
            }.border(3.dp, colors.onPhoto, CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(60.dp).background(colors.seal, CircleShape))
    }
}

@Composable
private fun ChromeButton(
    icon: ImageVector,
    description: String,
    caption: String?,
    onClick: () -> Unit,
) {
    val colors = Paper.colors
    Column(
        Modifier
            .clip(Shapes.tile)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(Space.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(52.dp).background(colors.scrim.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.onPhoto)
        }
        if (caption != null) {
            Text(caption, style = Paper.type.label, color = colors.onPhoto, modifier = Modifier.padding(top = Space.xs))
        }
    }
}

@Composable
private fun LastMenuButton(
    last: CachedMenuSummary,
    onOpen: (String) -> Unit,
) {
    ChromeButton(
        icon = PaperIcons.Menu,
        description = stringResource(Res.string.capture_last_menu_description, last.dishCount),
        caption = stringResource(Res.string.capture_last_menu).uppercase(),
        onClick = { onOpen(last.cacheKey) },
    )
}

/** Four printer's corner marks that breathe gently, suggesting where the menu should sit. */
@Composable
private fun FrameGuide(modifier: Modifier = Modifier) {
    val colors = Paper.colors
    val alpha =
        if (Paper.reduceMotion) {
            0.55f
        } else {
            val breathing by rememberInfiniteTransition(label = "frame").animateFloat(
                initialValue = 0.3f,
                targetValue = 0.75f,
                animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "frame-alpha",
            )
            breathing
        }
    Canvas(
        modifier
            .padding(horizontal = Space.xl)
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .graphicsLayer { this.alpha = alpha },
    ) {
        val arm = size.minDimension * 0.12f
        val stroke = 1.5.dp.toPx()
        val corners =
            listOf(
                Offset(0f, 0f) to Offset(1f, 1f),
                Offset(size.width, 0f) to Offset(-1f, 1f),
                Offset(0f, size.height) to Offset(1f, -1f),
                Offset(size.width, size.height) to Offset(-1f, -1f),
            )
        corners.forEach { (corner, direction) ->
            drawLine(colors.onPhoto, corner, corner + Offset(arm * direction.x, 0f), stroke, StrokeCap.Round)
            drawLine(colors.onPhoto, corner, corner + Offset(0f, arm * direction.y), stroke, StrokeCap.Round)
        }
    }
}

@Composable
private fun CameraProblem(
    addingPage: Boolean,
    body: String,
    onAllow: (() -> Unit)?,
    state: CaptureUiState.Ready?,
    actions: CaptureActions,
) {
    val colors = Paper.colors
    Column(
        Modifier.fillMaxSize().felt(colors.paper),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PlateCluster()
        StateMessage(title = stringResource(Res.string.capture_camera_title), body = body) {
            if (onAllow != null) {
                PrimaryButton(stringResource(Res.string.capture_camera_allow), onAllow, Modifier.fillMaxWidth())
                SecondaryButton(
                    stringResource(Res.string.action_choose_photo),
                    actions.onGallery,
                    Modifier.fillMaxWidth(),
                    icon = PaperIcons.Gallery,
                )
            } else {
                // With no camera to allow, the photo library is the way in.
                PrimaryButton(
                    stringResource(Res.string.action_choose_photo),
                    actions.onGallery,
                    Modifier.fillMaxWidth(),
                )
            }
            state?.lastMenu?.let { last ->
                SecondaryButton(
                    stringResource(Res.string.capture_last_menu_description, last.dishCount),
                    { actions.onLastMenu(last.cacheKey) },
                    Modifier.fillMaxWidth(),
                    icon = PaperIcons.Menu,
                )
            }
            if (!addingPage) state?.allowance?.let { AllowanceLine(it, actions.onAllowance, colors.inkMuted) }
        }
    }
}

/**
 * What MenuLango does, shown rather than told: a menu pinned to the felt, printed in a language
 * you can't read, and a coral line reading down it. Each line it passes gains a small note in your
 * own words. Motion that explains, never motion for its own sake; still under reduce-motion.
 */
@Composable
private fun PlateCluster() {
    val colors = Paper.colors
    val reduceMotion = Paper.reduceMotion
    val progress =
        if (reduceMotion) {
            null
        } else {
            rememberInfiniteTransition(label = "reading").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(READ_CYCLE_MS, easing = LinearEasing)),
                label = "reading-line",
            )
        }
    // Soft linen rather than bright white, and print in a warm pencil-brown rather than grey:
    // an impression of a menu, not a diagram of one.
    val sheet = lerp(colors.raised, colors.paper, 0.6f)
    val print = colors.inkMuted.copy(alpha = 0.3f)
    val heading = colors.inkMuted.copy(alpha = 0.42f)
    val note = colors.seal.copy(alpha = 0.45f)
    Box(Modifier.padding(top = Space.xl, bottom = Space.md).size(220.dp, 260.dp), contentAlignment = Alignment.Center) {
        // A second sheet behind, as menus have pages.
        Box(
            Modifier
                .size(170.dp, 220.dp)
                .graphicsLayer { rotationZ = -7f }
                .shadow(Elevation.resting, Shapes.tile)
                .paper(sheet, Shapes.tile),
        )
        Canvas(
            Modifier
                .size(170.dp, 220.dp)
                .graphicsLayer { rotationZ = 4f }
                .shadow(Elevation.raised, Shapes.tile)
                .paper(sheet, Shapes.tile),
        ) {
            val pad = 18.dp.toPx()
            val line = 7.dp.toPx()
            val gap = 22.dp.toPx()
            // All the way down and a pause at the bottom, then round again.
            val reach = (progress?.value ?: 1f).let { (it * 1.25f).coerceAtMost(1f) }
            val scanY = pad + (size.height - pad * 2) * reach
            // A heading in the restaurant's print, then lines of dishes with prices.
            drawRoundRect(heading, Offset(pad, pad), Size(size.width * 0.45f, line * 1.4f), CornerRadius(line))
            var y = pad + gap * 1.6f
            var row = 0
            while (y < size.height - pad) {
                val width = (size.width - pad * 2) * (if (row % 3 == 1) 0.55f else 0.7f)
                drawRoundRect(print, Offset(pad, y), Size(width, line), CornerRadius(line / 2))
                drawRoundRect(
                    print,
                    Offset(size.width - pad - line * 3, y),
                    Size(line * 3, line),
                    CornerRadius(line / 2),
                )
                // Read lines gain a note in the diner's language beneath them.
                if (y < scanY) {
                    drawRoundRect(
                        note,
                        Offset(pad, y + line * 1.5f),
                        Size(width * 0.7f, line * 0.6f),
                        CornerRadius(line / 2),
                    )
                }
                y += gap
                row++
            }
            if (progress != null && reach < 1f) {
                drawLine(
                    colors.seal,
                    Offset(pad / 2, scanY),
                    Offset(size.width - pad / 2, scanY),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, colors.seal.copy(alpha = 0.08f)),
                        startY =
                            scanY - 28.dp.toPx(),
                        endY = scanY,
                    ),
                    topLeft = Offset(0f, scanY - 28.dp.toPx()),
                    size = Size(size.width, 28.dp.toPx()),
                )
            }
        }
    }
}

private const val READ_CYCLE_MS = 5200
