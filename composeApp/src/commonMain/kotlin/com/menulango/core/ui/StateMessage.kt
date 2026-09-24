package com.menulango.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.menulango.core.design.Paper
import com.menulango.core.design.Space
import com.menulango.core.result.AppError
import com.menulango.resources.Res
import com.menulango.resources.error_capture_body
import com.menulango.resources.error_capture_title
import com.menulango.resources.error_config_body
import com.menulango.resources.error_config_title
import com.menulango.resources.error_malformed_body
import com.menulango.resources.error_malformed_title
import com.menulango.resources.error_offline_body
import com.menulango.resources.error_offline_title
import com.menulango.resources.error_rate_body
import com.menulango.resources.error_rate_title
import com.menulango.resources.error_unreadable_body
import com.menulango.resources.error_unreadable_title
import com.menulango.resources.error_upstream_body
import com.menulango.resources.error_upstream_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The shape of every empty and failed state: a plain-language title, one honest sentence, and
 * the recovery actions. Never a stack trace, never an error code.
 */
@Composable
internal fun StateMessage(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = Space.gutter,
    actions: @Composable () -> Unit = {},
) {
    Column(
        modifier =
            modifier
                .widthIn(
                    max = Space.readingWidth,
                ).padding(horizontal = horizontalPadding, vertical = Space.section),
        verticalArrangement = Arrangement.spacedBy(Space.related),
    ) {
        Text(title, style = Paper.type.headline, color = Paper.colors.ink, modifier = Modifier.semantics { heading() })
        Text(body, style = Paper.type.bodySmall, color = Paper.colors.inkMuted)
        Spacer(Modifier.height(Space.md))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.related)) { actions() }
    }
}

internal data class ErrorCopy(
    val title: StringResource,
    val body: StringResource,
)

internal fun AppError.copy(): ErrorCopy =
    when (this) {
        AppError.Offline -> ErrorCopy(Res.string.error_offline_title, Res.string.error_offline_body)
        AppError.RateLimited -> ErrorCopy(Res.string.error_rate_title, Res.string.error_rate_body)
        AppError.Unreadable -> ErrorCopy(Res.string.error_unreadable_title, Res.string.error_unreadable_body)
        AppError.Upstream -> ErrorCopy(Res.string.error_upstream_title, Res.string.error_upstream_body)
        AppError.Malformed -> ErrorCopy(Res.string.error_malformed_title, Res.string.error_malformed_body)
        AppError.NotConfigured -> ErrorCopy(Res.string.error_config_title, Res.string.error_config_body)
        AppError.CaptureFailed -> ErrorCopy(Res.string.error_capture_title, Res.string.error_capture_body)
    }

@Composable
internal fun ErrorMessage(
    error: AppError,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = Space.gutter,
    actions: @Composable () -> Unit = {},
) {
    val copy = error.copy()
    StateMessage(stringResource(copy.title), stringResource(copy.body), modifier, horizontalPadding, actions)
}
