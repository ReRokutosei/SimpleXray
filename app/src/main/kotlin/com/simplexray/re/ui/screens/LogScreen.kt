package com.simplexray.re.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplexray.re.R
import com.simplexray.re.prefs.LogLevel
import com.simplexray.re.viewmodel.LogViewModel
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun LogScreen(
    logViewModel: LogViewModel,
    listState: LazyListState,
    paddingValues: PaddingValues = PaddingValues(),
    logLevel: LogLevel = LogLevel.Auto,
    accessLog: Boolean = true,
    dnsLog: Boolean = false
) {
    val context = LocalContext.current
    val filteredEntries by logViewModel.filteredEntries.collectAsStateWithLifecycle()
    val isInitialLoad = remember { mutableStateOf(true) }
    val bottomPadding = paddingValues.calculateBottomPadding().coerceAtLeast(12.dp)

    DisposableEffect(key1 = Unit) {
        logViewModel.registerLogReceiver(context)
        logViewModel.loadLogs()
        onDispose {
            logViewModel.unregisterLogReceiver(context)
        }
    }

    LaunchedEffect(filteredEntries) {
        if (filteredEntries.isNotEmpty() && isInitialLoad.value) {
            listState.animateScrollToItem(0)
            isInitialLoad.value = false
        }
    }

    val isAllLogsDisabled = !accessLog &&
            logLevel == LogLevel.None &&
            !dnsLog

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        if (isAllLogsDisabled) {
            // Logging is disabled by the user (loglevel: none): hide every log
            // entry and show a centered card instead. File logging continues so
            // switching back to another level immediately shows history.
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.log_disabled_none),
                    modifier = Modifier.fillMaxWidth(),
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        } else if (filteredEntries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_log_entries),
                    modifier = Modifier.fillMaxWidth(),
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            SelectionContainer {
                Box(modifier = Modifier.fillMaxSize()) {
                    val adapter = rememberScrollBarAdapter(scrollState = listState)
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        contentPadding = PaddingValues(top = bottomPadding, bottom = 12.dp),
                        reverseLayout = true
                    ) {
                        items(filteredEntries) { logEntry ->
                            LogEntryItem(logEntry = logEntry)
                        }
                    }
                    VerticalScrollBar(
                        adapter = adapter,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }
        }
    }
}

@Composable
fun LogEntryItem(logEntry: String) {
    val colorOnSurface = MiuixTheme.colorScheme.onSurface
    val timestampColor = MiuixTheme.colorScheme.primary

    val annotatedString = remember(logEntry) {
        buildAnnotatedString {
            var endIndex = 0
            while (endIndex < logEntry.length) {
                val c = logEntry[endIndex]
                if (Character.isDigit(c) || c == '/' || c == ' ' || c == ':' || c == '.') {
                    endIndex++
                } else {
                    break
                }
            }
            if (endIndex > 0) {
                val potentialTimestamp = logEntry.substring(0, endIndex)
                if (potentialTimestamp.contains("/") && potentialTimestamp.contains(":")) {
                    withStyle(
                        style = SpanStyle(
                            color = timestampColor
                        )
                    ) {
                        append(logEntry.substring(0, endIndex))
                    }
                    append(logEntry.substring(endIndex))
                } else {
                    append(logEntry)
                }
            } else {
                append(logEntry)
            }
        }
    }

    Text(
        text = annotatedString,
        fontSize = 13.sp,
        fontFamily = FontFamily.Monospace,
        color = colorOnSurface,
        modifier = Modifier.padding(vertical = 3.dp)
    )
}
