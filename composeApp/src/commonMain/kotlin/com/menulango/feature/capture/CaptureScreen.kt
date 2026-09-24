package com.menulango.feature.capture

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.menulango.MenuSource
import com.menulango.PaywallReason
import com.menulango.Route
import com.menulango.core.design.Motion
import com.menulango.core.design.Paper
import com.menulango.core.design.PaperIcons
import com.menulango.core.design.Shapes
import com.menulango.core.design.Space
import com.menulango.core.ui.PrimaryButton
import com.menulango.core.ui.SecondaryButton
import com.menulango.core.ui.StateMessage
import com.menulango.data.menu.local.CachedMenuSummary
import com.menulango.platform.CameraController
import com.menulango.platform.CameraState
import com.menulango.platform.CameraViewfinder
import com.menulango.platform.PickedPhoto
import com.menulango.platform.rememberPhotoPicker
import com.menulango.resources.Res
import com.menulango.resources.action_choose_photo
import com.menulango.resources.app_name
import com.menulango.resources.capture_camera_allow
import com.menulango.resources.capture_camera_body
import com.menulango.resources.capture_camera_title
import com.menulango.resources.capture_camera_unavailable
import com.menulango.resources.capture_debug_plus_off
import com.menulango.resources.capture_debug_plus_on
import com.menulango.resources.capture_failed
import com.menulango.resources.capture_gallery
import com.menulango.resources.capture_hint
import com.menulango.resources.capture_last_menu
import com.menulango.resources.capture_last_menu_description
import com.menulango.resources.capture_plus
import com.menulango.resources.capture_preparing
import com.menulango.resources.capture_quota
import com.menulango.resources.capture_quota_none
import com.menulango.resources.capture_sample
import com.menulango.resources.capture_shutter
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun CaptureScreen(navigate: (Route) -> Unit) {
    val viewModel = koinViewModel<CaptureViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val camera = remember { CameraController() }
    val scope = rememberCoroutineScope()

    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }

    val picker =
        rememberPhotoPicker { picked ->
            when (picked) {
                is PickedPhoto.Chosen -> viewModel.onPhotoReady(picked.jpeg)?.let(navigate)
                PickedPhoto.Cancelled -> viewModel.onPhotoCancelled()
                PickedPhoto.Unreadable -> viewModel.onPhotoReady(null)
            }
        }

    CaptureContent(
        state = state,
        camera = camera,
        viewfinder = { CameraViewfinder(camera, Modifier.fillMaxSize()) },
        actions =
            CaptureActions(
                onShutter = {
                    val gate = viewModel.gateForScan()
                    if (gate != null) {
                        navigate(gate)
                    } else {
                        viewModel.onPreparingPhoto()
                        scope.launch { viewModel.onPhotoReady(camera.capture())?.let(navigate) }
                    }
                },
                onGallery = { viewModel.gateForScan()?.let(navigate) ?: picker() },
                onLastMenu = { key -> navigate(Route.Menu(MenuSource.Saved(key))) },
                onAllowance = { navigate(Route.Paywall(PaywallReason.Upgrade)) },
                onSample = { navigate(Route.Menu(MenuSource.Sample)) },
                onToggleDebugPlus = viewModel::toggleDebugPlus,
            ),
    )
}

internal data class CaptureActions(
    val onShutter: () -> Unit,
    val onGallery: () -> Unit,
    val onLastMenu: (String) -> Unit,
    val onAllowance: () -> Unit,
    val onSample: () -> Unit,
    val onToggleDebugPlus: () -> Unit,
) {
    companion object {
        val Preview = CaptureActions({}, {}, {}, {}, {}, {})
    }
}

/**
 * Full-bleed camera, and almost nothing else: one hint, one honest counter, the shutter where the
 * thumb rests. No tab bar, no settings gear.
 */
@Composable
internal fun CaptureContent(
    state: CaptureUiState,
    camera: CameraController,
    viewfinder: @Composable () -> Unit,
    actions: CaptureActions,
) {
    val colors = Paper.colors
    val ready = state as? CaptureUiState.Ready
    Box(Modifier.fillMaxSize().background(colors.scrim)) {
        viewfinder()

        val blocked = camera.state == CameraState.PermissionDenied || camera.state == CameraState.Unavailable
        if (blocked) {
            // Without a camera this is a page, not a viewfinder: paper, ink, and the other way in.
            CameraProblem(
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
            TopChrome(ready?.debug, actions, Modifier.align(Alignment.TopCenter), tint = colors.ink)
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

        TopChrome(ready?.debug, actions, Modifier.align(Alignment.TopCenter), tint = colors.onPhoto)

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Space.gutter, vertical = Space.gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val hint =
                when {
                    ready?.isPreparingPhoto == true -> Res.string.capture_preparing
                    ready?.photoProblem == true -> Res.string.capture_failed
                    else -> Res.string.capture_hint
                }
            Text(
                stringResource(hint),
                style = Paper.type.bodySmall,
                color = colors.onPhoto,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Space.xs))
            ready?.allowance?.let { AllowanceLine(it, actions.onAllowance, colors.onPhoto) }
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
                Shutter(
                    enabled = camera.state == CameraState.Ready && ready?.isPreparingPhoto != true,
                    onClick = actions.onShutter,
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    ready?.lastMenu?.let { last -> LastMenuButton(last, actions.onLastMenu) }
                }
            }
        }
    }
}

@Composable
private fun TopChrome(
    debug: DebugTools?,
    actions: CaptureActions,
    modifier: Modifier = Modifier,
    tint: Color,
) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Space.gutter, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.app_name),
            style = Paper.type.dishName,
            color = tint,
            modifier = Modifier.weight(1f),
        )
        if (debug != null) {
            DebugChip(stringResource(Res.string.capture_sample), actions.onSample, tint)
            Spacer(Modifier.width(Space.related))
            DebugChip(
                stringResource(
                    if (debug.plusUnlocked) Res.string.capture_debug_plus_on else Res.string.capture_debug_plus_off,
                ),
                actions.onToggleDebugPlus,
                tint,
            )
        }
    }
}

@Composable
private fun DebugChip(
    text: String,
    onClick: () -> Unit,
    tint: Color,
) {
    Text(
        text.uppercase(),
        style = Paper.type.label,
        color = tint,
        modifier =
            Modifier
                .border(Space.hairline, tint.copy(alpha = 0.5f), Shapes.chip)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = Space.related, vertical = 6.dp),
    )
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

/** The one round thing in the app. It presses in, never bounces. */
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
        Box(Modifier.size(60.dp).background(colors.onPhoto, CircleShape))
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
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(Space.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(Space.touchTarget).background(colors.scrim.copy(alpha = 0.45f), Shapes.card),
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
    body: String,
    onAllow: (() -> Unit)?,
    state: CaptureUiState.Ready?,
    actions: CaptureActions,
) {
    val colors = Paper.colors
    Box(Modifier.fillMaxSize().background(colors.paper), contentAlignment = Alignment.Center) {
        StateMessage(title = stringResource(Res.string.capture_camera_title), body = body) {
            if (onAllow != null) {
                PrimaryButton(stringResource(Res.string.capture_camera_allow), onAllow, Modifier.fillMaxWidth())
            }
            SecondaryButton(
                stringResource(Res.string.action_choose_photo),
                actions.onGallery,
                Modifier.fillMaxWidth(),
                icon = PaperIcons.Gallery,
            )
            state?.lastMenu?.let { last ->
                SecondaryButton(
                    stringResource(Res.string.capture_last_menu_description, last.dishCount),
                    { actions.onLastMenu(last.cacheKey) },
                    Modifier.fillMaxWidth(),
                    icon = PaperIcons.Menu,
                )
            }
            state?.allowance?.let { AllowanceLine(it, actions.onAllowance, colors.inkMuted) }
        }
    }
}
