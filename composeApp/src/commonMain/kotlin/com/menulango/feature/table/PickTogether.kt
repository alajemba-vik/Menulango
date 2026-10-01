package com.menulango.feature.table

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.menulango.core.design.Paper
import com.menulango.core.design.PaperIcons
import com.menulango.core.design.Shapes
import com.menulango.core.design.Space
import com.menulango.core.ui.MenuLangoMark
import com.menulango.core.ui.PrimaryButton
import com.menulango.core.ui.SecondaryButton
import com.menulango.core.ui.paperFieldColors
import com.menulango.core.ui.pressable
import com.menulango.data.menu.model.Dish
import com.menulango.platform.rememberNearbyAccess
import com.menulango.resources.Res
import com.menulango.resources.menu_page_dishes
import com.menulango.resources.menu_plus_badge
import com.menulango.resources.table_accept
import com.menulango.resources.table_close
import com.menulango.resources.table_decline
import com.menulango.resources.table_denied
import com.menulango.resources.table_host_body
import com.menulango.resources.table_host_title
import com.menulango.resources.table_hosting_body
import com.menulango.resources.table_hosting_title
import com.menulango.resources.table_join_body
import com.menulango.resources.table_join_title
import com.menulango.resources.table_joined_body
import com.menulango.resources.table_joined_title
import com.menulango.resources.table_joined_unmatched
import com.menulango.resources.table_joining
import com.menulango.resources.table_leave
import com.menulango.resources.table_looking_body
import com.menulango.resources.table_looking_title
import com.menulango.resources.table_lost
import com.menulango.resources.table_name_hint
import com.menulango.resources.table_name_label
import com.menulango.resources.table_needs_name
import com.menulango.resources.table_request
import com.menulango.resources.table_stop_looking
import com.menulango.resources.table_unmatched
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * "Pick together": one phone hosts the table, the others join it, and everyone's picks gather
 * on the host's phone under their own names. Each person scans the menu themselves; only picks
 * travel, phone to phone, nothing through the internet. Hosting is part of Plus; joining is free.
 */
@Composable
internal fun PickTogether(
    orderKey: String,
    dishes: () -> List<Dish>,
    isPlus: Boolean,
    onPlus: () -> Unit,
) {
    val session = koinInject<TableSession>()
    val state by session.table.collectAsState()
    AnimatedContent(
        targetState = state,
        contentKey = { it::class },
        transitionSpec = { fadeIn(tween(FADE_MS)) togetherWith fadeOut(tween(FADE_MS)) },
        label = "table",
    ) { shown ->
        when (shown) {
            TableState.Idle -> Start(session, orderKey, dishes, isPlus, onPlus)
            is TableState.Hosting -> Hosting(session, shown)
            is TableState.Looking -> Looking(session, shown)
            is TableState.Joined -> Joined(session, shown)
        }
    }
}

@Composable
private fun Start(
    session: TableSession,
    orderKey: String,
    dishes: () -> List<Dish>,
    isPlus: Boolean,
    onPlus: () -> Unit,
) {
    val colors = Paper.colors
    var name by remember { mutableStateOf(session.name) }
    var needsName by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val access = rememberNearbyAccess(onReady = { pending?.invoke() }, onDenied = { denied = true })
    val begin: (() -> Unit) -> Unit = { start ->
        if (name.isBlank()) {
            needsName = true
        } else {
            session.name = name
            needsName = false
            denied = false
            pending = start
            access()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it.take(MAX_NAME)
                needsName = false
            },
            label = { Text(stringResource(Res.string.table_name_label)) },
            placeholder = { Text(stringResource(Res.string.table_name_hint), color = colors.inkFaint) },
            singleLine = true,
            isError = needsName,
            colors = paperFieldColors(),
            shape = Shapes.button,
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (needsName) Note(stringResource(Res.string.table_needs_name), colors.alarm)
        Choice(
            title = stringResource(Res.string.table_host_title),
            body = stringResource(Res.string.table_host_body),
            locked = !isPlus,
            onClick = { if (isPlus) begin { session.host(orderKey, dishes) } else onPlus() },
        )
        Choice(
            title = stringResource(Res.string.table_join_title),
            body = stringResource(Res.string.table_join_body),
            locked = false,
            onClick = { begin { session.look(orderKey) } },
        )
        if (denied) Note(stringResource(Res.string.table_denied), colors.alarm)
    }
}

@Composable
private fun Choice(
    title: String,
    body: String,
    locked: Boolean,
    onClick: () -> Unit,
) {
    val colors = Paper.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Shapes.card)
            .background(colors.sunk)
            .pressable(onClick)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .padding(Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.related),
            ) {
                Text(title, style = Paper.type.title, color = colors.ink)
                if (locked) {
                    Text(
                        stringResource(Res.string.menu_plus_badge),
                        style = Paper.type.chip.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.onSeal,
                        modifier =
                            Modifier
                                .background(
                                    colors.seal,
                                    Shapes.chip,
                                ).padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Text(body, style = Paper.type.bodySmall, color = colors.inkMuted)
        }
        Spacer(Modifier.width(Space.sm))
        Icon(
            PaperIcons.ChevronRight,
            contentDescription = null,
            tint = colors.inkFaint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun Hosting(
    session: TableSession,
    state: TableState.Hosting,
) {
    val colors = Paper.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.md)) {
        Beacon()
        Heading(stringResource(Res.string.table_hosting_title))
        Text(
            stringResource(Res.string.table_hosting_body, session.name),
            style = Paper.type.bodySmall,
            color = colors.inkMuted,
            textAlign = TextAlign.Center,
        )
        state.requests.forEach { request ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(Shapes.card)
                    .background(colors.sealWash)
                    .padding(horizontal = Space.md, vertical = Space.sm)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(Res.string.table_request, request.name),
                    style = Paper.type.body,
                    color = colors.ink,
                    modifier = Modifier.weight(1f),
                )
                SmallAction(
                    stringResource(Res.string.table_decline),
                    filled = false,
                ) { session.answer(request, accept = false) }
                Spacer(Modifier.width(Space.xs))
                SmallAction(
                    stringResource(Res.string.table_accept),
                    filled = true,
                ) { session.answer(request, accept = true) }
            }
        }
        state.guests.forEach { guest ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(Shapes.card)
                    .background(colors.sunk)
                    .padding(Space.md),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(10.dp).background(colors.seal, CircleShape),
                    )
                    Spacer(Modifier.width(Space.sm))
                    Text(guest.name, style = Paper.type.title, color = colors.ink, modifier = Modifier.weight(1f))
                    Text(
                        pluralStringResource(Res.plurals.menu_page_dishes, guest.dishes, guest.dishes),
                        style = Paper.type.caption,
                        color = colors.inkMuted,
                    )
                }
                if (guest.unmatched.isNotEmpty()) {
                    Text(
                        stringResource(Res.string.table_unmatched, guest.unmatched.joinToString(", ")),
                        style = Paper.type.caption,
                        color = colors.inkMuted,
                        modifier = Modifier.padding(top = Space.xs),
                    )
                }
            }
        }
        SecondaryButton(stringResource(Res.string.table_close), session::stop, Modifier.fillMaxWidth())
    }
}

@Composable
private fun Looking(
    session: TableSession,
    state: TableState.Looking,
) {
    val colors = Paper.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.md)) {
        Beacon()
        Heading(stringResource(Res.string.table_looking_title))
        Text(
            stringResource(if (state.lost) Res.string.table_lost else Res.string.table_looking_body),
            style = Paper.type.bodySmall,
            color = colors.inkMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        state.tables.forEach { table ->
            val joining = state.joining == table.peer
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Space.touchTarget)
                    .clip(Shapes.card)
                    .background(colors.sunk)
                    .pressable({ if (state.joining == null) session.join(table) })
                    .semantics(mergeDescendants = true) { role = Role.Button }
                    .padding(Space.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(table.name, style = Paper.type.title, color = colors.ink, modifier = Modifier.weight(1f))
                if (joining) {
                    Text(stringResource(Res.string.table_joining), style = Paper.type.caption, color = colors.inkMuted)
                } else {
                    Icon(
                        PaperIcons.ChevronRight,
                        contentDescription = null,
                        tint = colors.inkFaint,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        SecondaryButton(stringResource(Res.string.table_stop_looking), session::stop, Modifier.fillMaxWidth())
    }
}

@Composable
private fun Joined(
    session: TableSession,
    state: TableState.Joined,
) {
    val colors = Paper.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.md)) {
        Box(Modifier.size(56.dp).background(colors.sealWash, CircleShape), contentAlignment = Alignment.Center) {
            Icon(PaperIcons.Check, contentDescription = null, tint = colors.sealInk, modifier = Modifier.size(28.dp))
        }
        Heading(stringResource(Res.string.table_joined_title, state.hostName))
        Text(
            stringResource(Res.string.table_joined_body),
            style = Paper.type.bodySmall,
            color = colors.inkMuted,
            textAlign = TextAlign.Center,
        )
        if (state.unmatched.isNotEmpty()) {
            Note(stringResource(Res.string.table_joined_unmatched, state.unmatched.joinToString(", ")), colors.inkMuted)
        }
        SecondaryButton(stringResource(Res.string.table_leave), session::stop, Modifier.fillMaxWidth())
    }
}

/**
 * The table, open and waiting: the mark with soft rings widening around it, like AirDrop looking
 * for someone. Waiting deserves a sign of life; under reduce motion the rings are still.
 */
@Composable
private fun Beacon() {
    val colors = Paper.colors
    val reduceMotion = Paper.reduceMotion
    val wave =
        if (reduceMotion) {
            null
        } else {
            rememberInfiniteTransition(label = "beacon").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(BEACON_MS, easing = LinearEasing), RepeatMode.Restart),
                label = "beacon-wave",
            )
        }
    val ring = colors.seal
    Box(Modifier.size(BEACON), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(BEACON)) {
            val t = wave?.value ?: 0.5f
            repeat(RINGS) { i ->
                val p = (t + i / RINGS.toFloat()) % 1f
                drawCircle(
                    ring.copy(alpha = (1f - p) * RING_ALPHA),
                    radius = size.minDimension / 2f * (MARK_FRACTION + (1f - MARK_FRACTION) * p),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }
        MenuLangoMark(description = null, modifier = Modifier.size(BEACON * MARK_FRACTION))
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = Paper.type.headline,
        color = Paper.colors.ink,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun Note(
    text: String,
    color: androidx.compose.ui.graphics.Color,
) {
    Text(
        text,
        style = Paper.type.caption,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun SmallAction(
    text: String,
    filled: Boolean,
    onClick: () -> Unit,
) {
    val colors = Paper.colors
    Text(
        text,
        style = Paper.type.button,
        color = if (filled) colors.onSeal else colors.ink,
        modifier =
            Modifier
                .heightIn(min = Space.touchTarget)
                .clip(Shapes.pill)
                .background(if (filled) colors.seal else colors.raised)
                .pressable(onClick)
                .semantics { role = Role.Button }
                .padding(horizontal = Space.md, vertical = Space.sm),
    )
}

private const val MAX_NAME = 24
private const val FADE_MS = 220
private const val BEACON_MS = 2_400
private const val RINGS = 3
private const val RING_ALPHA = 0.35f
private const val MARK_FRACTION = 0.36f
private val BEACON = 132.dp
