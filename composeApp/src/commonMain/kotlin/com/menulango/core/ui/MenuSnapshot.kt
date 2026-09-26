package com.menulango.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.menulango.core.design.Elevation
import com.menulango.core.design.Paper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * The diner's own photo of the menu, pinned to the felt header like a snapshot on a board —
 * slightly tilted, with a white border. A menu of several pages shows a second snapshot behind
 * and the page count.
 *
 * Replaces laying text over the photo: menus are dense print, and no overlay makes a title read
 * well on top of one.
 */
@Composable
internal fun MenuSnapshot(
    photo: ByteArray,
    pages: Int,
    description: String,
    modifier: Modifier = Modifier,
) {
    val colors = Paper.colors
    val image by produceState<ImageBitmap?>(null, photo) {
        value = withContext(Dispatchers.Default) { decodeOrNull(photo) }
    }
    val frame = RoundedCornerShape(10.dp)
    Box(modifier.size(SNAPSHOT_WIDTH + 12.dp, SNAPSHOT_HEIGHT + 12.dp).semantics { contentDescription = description }) {
        if (pages > 1) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(SNAPSHOT_WIDTH, SNAPSHOT_HEIGHT)
                    .graphicsLayer { rotationZ = -7f }
                    .shadow(Elevation.resting, frame)
                    .background(colors.raised, frame),
            )
        }
        Box(
            Modifier
                .align(Alignment.Center)
                .size(SNAPSHOT_WIDTH, SNAPSHOT_HEIGHT)
                .graphicsLayer { rotationZ = 5f }
                .shadow(Elevation.raised, frame)
                .background(colors.raised, frame)
                .padding(4.dp),
        ) {
            image?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(7.dp)),
                )
            }
        }
        if (pages > 1) {
            Text(
                pages.toString(),
                style = Paper.type.chip.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                color = colors.paper,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                        .background(colors.ink, CircleShape)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

private val SNAPSHOT_WIDTH = 72.dp
private val SNAPSHOT_HEIGHT = 92.dp

/** A photo that fails to decode leaves an empty white frame rather than crashing the menu. */
private fun decodeOrNull(bytes: ByteArray): ImageBitmap? =
    try {
        bytes.decodeToImageBitmap()
    } catch (e: IllegalArgumentException) {
        println("MenuLango menu snapshot not decodable: ${e.message}")
        null
    }
