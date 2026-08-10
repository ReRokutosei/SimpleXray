package com.simplexray.re.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplexray.re.R
import com.simplexray.re.ui.util.bracketMatcherTransformation
import com.simplexray.re.viewmodel.ConfigEditUiEvent
import com.simplexray.re.viewmodel.ConfigEditViewModel
import kotlinx.coroutines.flow.collectLatest
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    showNavigationIcon: Boolean = true
) {
    val filename by viewModel.filename.collectAsStateWithLifecycle()
    val configTextFieldValue by viewModel.configTextFieldValue.collectAsStateWithLifecycle()
    val filenameErrorMessage by viewModel.filenameErrorMessage.collectAsStateWithLifecycle()
    val hasConfigChanged by viewModel.hasConfigChanged.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()
    val scrollBehavior = MiuixScrollBehavior()
    val isKeyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val focusManager = LocalFocusManager.current
    val shareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {}

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collectLatest { event ->
            when (event) {
                is ConfigEditUiEvent.NavigateBack -> {
                    onBackClick()
                }

                is ConfigEditUiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }

                is ConfigEditUiEvent.ShareContent -> {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, event.content)
                    }
                    shareLauncher.launch(Intent.createChooser(shareIntent, null))
                }
            }
        }
    }

    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var matchIndices by remember { mutableStateOf(listOf<Int>()) }
    var currentMatchIndex by remember { mutableIntStateOf(0) }

    val updateMatches = { query: String, text: String ->
        if (query.isBlank()) {
            matchIndices = emptyList()
            currentMatchIndex = 0
        } else {
            val list = mutableListOf<Int>()
            var idx = text.indexOf(query, ignoreCase = true)
            while (idx >= 0) {
                list.add(idx)
                idx = text.indexOf(query, idx + 1, ignoreCase = true)
            }
            matchIndices = list
            if (currentMatchIndex >= list.size) {
                currentMatchIndex = (list.size - 1).coerceAtLeast(0)
            }
        }
    }

    val jumpToMatch = { index: Int ->
        if (matchIndices.isNotEmpty() && index in matchIndices.indices) {
            val start = matchIndices[index]
            val end = start + searchQuery.length
            viewModel.onConfigContentChange(
                configTextFieldValue.copy(selection = TextRange(start, end))
            )
        }
    }

    val menuEntry = remember {
        DropdownEntry(
            items = listOf(
                DropdownItem(
                    text = "分享配置文件",
                    onClick = { viewModel.shareConfigFile() }
                )
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = if (isSearching) "" else (filename.ifEmpty { stringResource(id = R.string.config) }),
                navigationIcon = {
                    if (showNavigationIcon) {
                        IconButton(onClick = {
                            if (isSearching) {
                                isSearching = false
                                searchQuery = ""
                                matchIndices = emptyList()
                            } else {
                                onBackClick()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    } else if (isSearching) {
                        IconButton(onClick = {
                            isSearching = false
                            searchQuery = ""
                            matchIndices = emptyList()
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.close_search)
                            )
                        }
                    }
                },
                actions = {
                    if (isSearching) {
                        InputField(
                            query = searchQuery,
                            onQueryChange = { q ->
                                searchQuery = q
                                updateMatches(q, configTextFieldValue.text)
                                if (matchIndices.isNotEmpty()) {
                                    jumpToMatch(0)
                                }
                            },
                            onSearch = {},
                            expanded = isSearching,
                            onExpandedChange = { isSearching = it },
                            label = stringResource(R.string.search),
                            modifier = Modifier.fillMaxWidth(0.5f)
                        )
                        if (matchIndices.isNotEmpty()) {
                            Text(
                                text = "${currentMatchIndex + 1}/${matchIndices.size}",
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            IconButton(onClick = {
                                if (matchIndices.isNotEmpty()) {
                                    currentMatchIndex = if (currentMatchIndex > 0) currentMatchIndex - 1 else matchIndices.size - 1
                                    jumpToMatch(currentMatchIndex)
                                }
                            }) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Prev")
                            }
                            IconButton(onClick = {
                                if (matchIndices.isNotEmpty()) {
                                    currentMatchIndex = (currentMatchIndex + 1) % matchIndices.size
                                    jumpToMatch(currentMatchIndex)
                                }
                            }) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next")
                            }
                        }
                    } else {
                        IconButton(onClick = { isSearching = true }) {
                            Icon(
                                painter = painterResource(id = R.drawable.search),
                                contentDescription = stringResource(R.string.search)
                            )
                        }
                        IconButton(onClick = {
                            viewModel.saveConfigFile()
                            focusManager.clearFocus()
                        }, enabled = hasConfigChanged) {
                            Icon(
                                painter = painterResource(id = R.drawable.save),
                                contentDescription = stringResource(id = R.string.save)
                            )
                        }
                        OverlayIconDropdownMenu(
                            entry = menuEntry,
                            collapseOnSelection = true
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.more)
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        contentWindowInsets = WindowInsets(0)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(paddingValues)
                .padding(horizontal = 12.dp)
                .verticalScroll(scrollState)
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

            TextField(
                value = configTextFieldValue,
                onValueChange = { newTextFieldValue ->
                    val newText = newTextFieldValue.text
                    val oldText = configTextFieldValue.text
                    val cursorPosition = newTextFieldValue.selection.start

                    if (newText.length == oldText.length + 1 &&
                        cursorPosition > 0 &&
                        newText[cursorPosition - 1] == '\n'
                    ) {
                        val pair = viewModel.handleAutoIndent(newText, cursorPosition - 1)
                        viewModel.onConfigContentChange(
                            TextFieldValue(
                                text = pair.first,
                                selection = TextRange(pair.second)
                            )
                        )
                    } else {
                        viewModel.onConfigContentChange(newTextFieldValue.copy(text = newText))
                    }
                },
                visualTransformation = bracketMatcherTransformation(configTextFieldValue),
                label = stringResource(R.string.content),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (isKeyboardOpen) 0.dp else 16.dp),
                textStyle = MiuixTheme.textStyles.main.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Text
                )
            )
        }
    }
}
