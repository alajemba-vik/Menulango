package com.menulango.core.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.menulango.core.design.Paper
import com.menulango.core.design.PaperIcons
import com.menulango.core.design.Shapes
import com.menulango.data.tips.Tip
import com.menulango.data.tips.Tips
import com.menulango.resources.Res
import com.menulango.resources.restore_info_body
import com.menulango.resources.restore_info_label
import com.menulango.resources.restore_info_title
import com.menulango.resources.tip_got_it
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * "Restore purchases", which Apple requires wherever subscriptions are sold, with a small info
 * mark beside it that explains what it does. Both always stay.
 */
@Composable
internal fun RestoreButton(
    text: String,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Paper.colors.ink,
) {
    val tips = koinInject<Tips>()
    var explaining by remember { mutableStateOf(false) }
    val infoLabel = stringResource(Res.string.restore_info_label)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        QuietButton(text, onRestore, color = color, singleLine = true)
        Icon(
            PaperIcons.Info,
            contentDescription = null,
            tint = Paper.colors.inkMuted,
            modifier =
                Modifier
                    // Pulled in over the button's own padding, so the mark sits beside the words
                    // (about 6 dp away) while keeping its full tap area.
                    .offset(x = -ICON_PULL)
                    .clip(Shapes.pill)
                    .pressable({ explaining = true })
                    .semantics { contentDescription = infoLabel }
                    // A 18dp mark with a 46dp tap area around it.
                    .padding(14.dp)
                    .size(18.dp),
        )
    }
    if (explaining) {
        val colors = Paper.colors
        AlertDialog(
            onDismissRequest = {
                explaining = false
                tips.markSeen(Tip.RestoreInfo)
            },
            containerColor = colors.raised,
            title = {
                Text(
                    stringResource(Res.string.restore_info_title),
                    style = Paper.type.dishName,
                    color = colors.ink,
                )
            },
            text = { Text(stringResource(Res.string.restore_info_body), style = Paper.type.body, color = colors.ink) },
            confirmButton = {
                TextButton(onClick = {
                    explaining = false
                    tips.markSeen(Tip.RestoreInfo)
                }) { Text(stringResource(Res.string.tip_got_it), color = colors.sealInk) }
            },
        )
    }
}

/** The button's end padding plus the mark's tap padding, less the 6 dp gap that should remain. */
private val ICON_PULL = 20.dp
