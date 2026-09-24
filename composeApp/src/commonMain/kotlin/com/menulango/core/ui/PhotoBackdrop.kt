package com.menulango.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.menulango.core.design.Motion
import com.menulango.core.design.Paper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * The photographed menu, kept behind the list as context — dimmed and softened so it never
 * competes with the text. It is background, not interface: nothing is ever floated over it.
 *
 * @param focused true while a dish is open: the photo recedes to 30%.
 */
@Composable
internal fun PhotoBackdrop(
    photo: ByteArray?,
    focused: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = Paper.colors
    val image by produceState<ImageBitmap?>(null, photo) {
        value = photo?.let { bytes -> withContext(Dispatchers.Default) { runCatchingDecode(bytes) } }
    }
    val alpha by animateFloatAsState(
        targetValue = if (focused) FOCUSED_ALPHA else RESTING_ALPHA,
        animationSpec = tween(Motion.SHEET_MS, easing = Motion.standard),
        label = "photo-dim",
    )
    Box(modifier.fillMaxSize().background(if (photo == null) colors.sunk else colors.scrim)) {
        image?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(6.dp).graphicsLayer { this.alpha = alpha },
            )
        }
    }
}

private const val RESTING_ALPHA = 0.62f
private const val FOCUSED_ALPHA = 0.3f

/** A photo that fails to decode is shown as plain paper rather than crashing the menu. */
private fun runCatchingDecode(bytes: ByteArray): ImageBitmap? =
    try {
        bytes.decodeToImageBitmap()
    } catch (e: IllegalArgumentException) {
        println("MenuLango photo backdrop not decodable: ${e.message}")
        null
    }
