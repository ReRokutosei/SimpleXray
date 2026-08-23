package com.simplexray.re.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Generic confirmation dialog: a title, a summary and a cancel/confirm button row.
 * Collapses the repeated `OverlayDialog` + `Row` + two `TextButton` boilerplate.
 */
@Composable
fun ConfirmOverlayDialog(
    title: String,
    summary: String,
    confirmText: String,
    cancelText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayDialog(
        show = true,
        title = title,
        summary = summary,
        onDismissRequest = onDismiss,
        content = {
            Row {
                TextButton(
                    text = cancelText,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(16.dp))
                TextButton(
                    text = confirmText,
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
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
) {
    OverlayDialog(
        show = true,
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
                    TextButton(
                        text = buttonText,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }
    )
}
