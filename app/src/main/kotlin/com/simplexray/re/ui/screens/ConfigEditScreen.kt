package com.simplexray.re.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplexray.re.R
import com.simplexray.re.viewmodel.ConfigEditUiEvent
import com.simplexray.re.viewmodel.ConfigEditViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Redo
import top.yukonga.miuix.kmp.icon.extended.Undo
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.scripta.editor.CodeEditor
import top.yukonga.scripta.editor.EditorColors
import top.yukonga.scripta.editor.EditorLanguage
import top.yukonga.scripta.editor.highlight.SyntaxColors
import top.yukonga.scripta.editor.rememberSaveableCodeEditorController

@Composable
fun ConfigEditScreen(
    onBackClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: ConfigEditViewModel
) {
    ConfigEditPane(
        onBackClick = onBackClick,
        snackbarHostState = snackbarHostState,
        viewModel = viewModel,
        showNavigationIcon = true
    )
}

@Composable
fun ConfigEditPane(
    onBackClick: () -> Unit = {},
    snackbarHostState: SnackbarHostState,
    viewModel: ConfigEditViewModel,
    showNavigationIcon: Boolean = true,
    onToggleExpand: (() -> Unit)? = null,
    isExpanded: Boolean = false
) {
    val filename by viewModel.filename.collectAsStateWithLifecycle()
    val fileExtension by viewModel.fileExtension.collectAsStateWithLifecycle()
    val documentSnapshot by viewModel.documentSnapshot.collectAsStateWithLifecycle()
    val filenameErrorMessage by viewModel.filenameErrorMessage.collectAsStateWithLifecycle()
    val hasFilenameChanged by viewModel.hasFilenameChanged.collectAsStateWithLifecycle()

    val controller = rememberSaveableCodeEditorController()
    val focusManager = LocalFocusManager.current

    var syncedGeneration by rememberSaveable {
        mutableIntStateOf(0)
    }

    LaunchedEffect(documentSnapshot.generation) {
        if (documentSnapshot.generation > 0 && documentSnapshot.generation != syncedGeneration) {
            controller.setDocument(documentSnapshot.content)
            syncedGeneration = documentSnapshot.generation
        }
    }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is ConfigEditUiEvent.NavigateBack -> {
                    onBackClick()
                }

                is ConfigEditUiEvent.MarkSaved -> {
                    controller.markSaved(event.documentVersion)
                }

                is ConfigEditUiEvent.ShowSnackbar -> {
                    launch {
                        snackbarHostState.showSnackbar(event.message)
                    }
                }
            }
        }
    }

    val editorLanguage = remember(fileExtension) {
        when (fileExtension.lowercase()) {
            "yaml", "yml" -> EditorLanguage.Yaml
            else -> EditorLanguage.JavaScript
        }
    }
    val editorColors = rememberMiuixEditorColors()
    val hasUnsavedChanges = hasFilenameChanged || controller.isModified

    val onSaveClick = {
        val version = controller.documentVersion
        val rawContent = controller.getText(controller.lineEnding)
        viewModel.saveConfigFile(rawContent, version)
        focusManager.clearFocus()
    }

    val onSearchToggle = {
        if (controller.isFindVisible) {
            controller.closeFind()
        } else {
            controller.openReplace()
        }
    }

    val editorContent: @Composable (PaddingValues) -> Unit = { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                TextField(
                    value = filename,
                    onValueChange = { v ->
                        viewModel.onFilenameChange(v)
                    },
                    label = stringResource(id = R.string.filename),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )

                if (filenameErrorMessage != null) {
                    Text(
                        text = filenameErrorMessage!!,
                        color = MiuixTheme.colorScheme.error,
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }

            CodeEditor(
                controller = controller,
                language = editorLanguage,
                colors = editorColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }

    if (showNavigationIcon) {
        Scaffold(
            topBar = {
                SmallTopAppBar(
                    title = "",
                    navigationIcon = {
                        IconButton(onClick = {
                            if (controller.isFindVisible) {
                                controller.closeFind()
                            } else {
                                onBackClick()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { controller.undo() },
                            enabled = controller.canUndo
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Undo,
                                contentDescription = stringResource(R.string.undo)
                            )
                        }
                        IconButton(
                            onClick = { controller.redo() },
                            enabled = controller.canRedo
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Redo,
                                contentDescription = stringResource(R.string.redo)
                            )
                        }
                        IconButton(onClick = onSearchToggle) {
                            Icon(
                                painter = painterResource(id = R.drawable.search),
                                contentDescription = stringResource(R.string.search)
                            )
                        }
                        IconButton(
                            onClick = onSaveClick,
                            enabled = hasUnsavedChanges
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.save),
                                contentDescription = stringResource(id = R.string.save)
                            )
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(state = snackbarHostState) },
            contentWindowInsets = WindowInsets(0)
        ) { paddingValues ->
            editorContent(paddingValues)
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onToggleExpand != null) {
                        IconButton(onClick = onToggleExpand) {
                            if (isExpanded) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.back)
                                )
                            } else {
                                Icon(
                                    painter = painterResource(id = R.drawable.code),
                                    contentDescription = "Expand"
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = { controller.undo() },
                        enabled = controller.canUndo
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Undo,
                            contentDescription = stringResource(R.string.undo)
                        )
                    }
                    IconButton(
                        onClick = { controller.redo() },
                        enabled = controller.canRedo
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Redo,
                            contentDescription = stringResource(R.string.redo)
                        )
                    }
                    IconButton(onClick = onSearchToggle) {
                        Icon(
                            painter = painterResource(id = R.drawable.search),
                            contentDescription = stringResource(R.string.search)
                        )
                    }
                    IconButton(
                        onClick = onSaveClick,
                        enabled = hasUnsavedChanges
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.save),
                            contentDescription = stringResource(id = R.string.save)
                        )
                    }
                }

                editorContent(PaddingValues(0.dp))
            }

            SnackbarHost(
                state = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            )
        }
    }
}

@Composable
private fun rememberMiuixEditorColors(): EditorColors {
    val scheme = MiuixTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    return remember(
        isDark,
        scheme.background,
        scheme.onBackground,
        scheme.surface,
        scheme.surfaceContainer,
        scheme.surfaceContainerHighest,
        scheme.onSurface,
        scheme.onSurfaceSecondary,
        scheme.onSurfaceContainerVariant,
        scheme.primary
    ) {
        val base = if (isDark) EditorColors.Default else EditorColors.Light
        base.copy(
            background = scheme.background,
            foreground = scheme.onSurface,
            gutterBackground = scheme.surface,
            gutterForeground = scheme.onSurfaceContainerVariant,
            cursor = scheme.primary,
            selection = scheme.primary.copy(alpha = 0.30f),
            handle = scheme.primary,
            symbolBarBackground = scheme.surfaceContainerHighest,
            symbolBarForeground = scheme.onSurface,
            symbolBarPressed = scheme.onSurface.copy(alpha = if (isDark) 0.20f else 0.08f),
            currentLine = scheme.surfaceContainer.copy(alpha = if (isDark) 0.65f else 0.85f),
            syntax = if (isDark) SyntaxColors.Dark else SyntaxColors.Light
        )
    }
}
