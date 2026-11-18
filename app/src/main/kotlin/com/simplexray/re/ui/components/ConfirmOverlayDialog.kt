package com.simplexray.re.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.simplexray.re.ui.scaffold.LocalLayerBackdrop
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Generic confirmation dialog: a title, a summary and a cancel/confirm button row.
 * Collapses the repeated `GlassDialog` + `Row` + two `GlassDialogButton` boilerplate.
 */
@Composable
fun ConfirmOverlayDialog(
    title: String,
    summary: String,
    confirmText: String,
    cancelText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    backdrop: Backdrop? = LocalLayerBackdrop.current,
) {
    GlassDialog(
        show = true,
        backdrop = backdrop,
        title = title,
        summary = summary,
        onDismissRequest = onDismiss,
        content = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassDialogButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = cancelText, style = MiuixTheme.textStyles.button)
                }
                GlassDialogButton(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    role = GlassDialogButtonRole.Primary,
                ) {
                    Text(text = confirmText, style = MiuixTheme.textStyles.button)
                }
            }
        }
    )
}

/**
 * Generic information dialog with a single "OK" button for help/explanation messages.
 */
@Composable
fun InfoOverlayDialog(
    title: String,
    summary: String,
    buttonText: String = "OK",
    onDismiss: () -> Unit,
    backdrop: Backdrop? = LocalLayerBackdrop.current,
) {
    GlassDialog(
        show = true,
        backdrop = backdrop,
        title = title,
        onDismissRequest = onDismiss,
        content = {
            Column {
                if (summary.isNotEmpty()) {
                    Text(
                        text = summary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        textAlign = TextAlign.Start
                    )
                }
                Row {
                    GlassDialogButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        role = GlassDialogButtonRole.Primary,
                    ) {
                        Text(text = buttonText, style = MiuixTheme.textStyles.button)
                    }
                }
            }
        }
    )
}
