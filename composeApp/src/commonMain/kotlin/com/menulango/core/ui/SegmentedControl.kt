package com.menulango.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.menulango.core.design.Elevation
import com.menulango.core.design.Paper
import com.menulango.core.design.Shapes
import com.menulango.core.design.Space

/**
 * A choice between views of the same thing, iOS's segmented control: one lens that slides to the
 * chosen option. Used where the options are ways of showing, never filters (those are chips).
 */
@Composable
internal fun SegmentedControl(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: (Int) -> Boolean = { true },
) {
    val colors = Paper.colors
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(Shapes.button)
            .background(colors.sunk)
            .padding(4.dp),
    ) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(
            targetValue = segment * selected,
            animationSpec =
                if (Paper.reduceMotion) {
                    tween(0)
                } else {
                    spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                },
            label = "segment-lens",
        )
        Box(
            Modifier
                .matchParentSize()
                .padding(end = maxWidth - segment)
                .offset(x = offset)
                .shadow(Elevation.resting, Shapes.chip)
                .clip(Shapes.chip)
                .background(colors.raised),
        )
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selected
                val usable = enabled(index)
                val tint by animateColorAsState(
                    when {
                        isSelected -> colors.ink
                        usable -> colors.inkMuted
                        else -> colors.inkFaint
                    },
                    label = "segment-text",
                )
                Text(
                    option,
                    style = Paper.type.button.copy(fontSize = Paper.type.bodySmall.fontSize),
                    color = tint,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier =
                        Modifier
                            .weight(1f)
                            .clip(Shapes.chip)
                            .selectable(
                                selected = isSelected,
                                enabled = usable,
                                role = Role.RadioButton,
                                onClick = { onSelect(index) },
                            ).padding(vertical = Space.sm),
                )
            }
        }
    }
}
