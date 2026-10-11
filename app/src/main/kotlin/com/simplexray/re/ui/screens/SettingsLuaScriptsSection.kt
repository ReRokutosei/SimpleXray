package com.simplexray.re.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplexray.re.R
import com.simplexray.re.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference

@Composable
internal fun SettingsLuaScriptsSection(
    mainViewModel: MainViewModel,
    onDeleteLuaScript: (String) -> Unit,
) {
    val context = LocalContext.current
    var replacingLuaFileName by remember { mutableStateOf<String?>(null) }

    val luaPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            mainViewModel.importLuaFile(uri)
        }
        replacingLuaFileName = null
    }

    SmallTitle(text = stringResource(R.string.lua_scripts_category_title))
    Card(modifier = Modifier.fillMaxWidth()) {
        LuaScriptFilesList(
            mainViewModel = mainViewModel,
            onReplace = { fileName ->
                replacingLuaFileName = fileName
                luaPickerLauncher.launch(arrayOf("*/*"))
            },
            onDelete = onDeleteLuaScript
        )

        key("import-lua-from-file") {
            ArrowPreference(
                title = "+ " + stringResource(R.string.import_from_file) + " (.lua)",
                onClick = {
                    replacingLuaFileName = null
                    luaPickerLauncher.launch(arrayOf("*/*"))
                }
            )
        }
    }
}

@Composable
private fun LuaScriptFilesList(
    mainViewModel: MainViewModel,
    onReplace: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val context = LocalContext.current
    val luaScriptVersion by mainViewModel.luaScriptVersion.collectAsStateWithLifecycle()
    var luaFiles by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(luaScriptVersion) {
        val names = withContext(Dispatchers.IO) {
            val result = mutableListOf<String>()
            context.filesDir.listFiles { file ->
                file.isFile && file.name.lowercase().endsWith(".lua")
            }?.forEach { result.add(it.name) }
            result.sorted()
        }
        luaFiles = names
    }

    luaFiles.forEach { fileName ->
        key(fileName) {
            BasicComponent(
                title = fileName,
                summary = mainViewModel.getLuaFileSummary(fileName),
                endActions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onReplace(fileName) }) {
                            Icon(
                                painter = painterResource(id = R.drawable.place_item),
                                contentDescription = stringResource(R.string.import_file)
                            )
                        }
                        IconButton(onClick = { onDelete(fileName) }) {
                            Icon(
                                painter = painterResource(id = R.drawable.delete),
                                contentDescription = stringResource(R.string.delete_config)
                            )
                        }
                    }
                }
            )
        }
    }
}
