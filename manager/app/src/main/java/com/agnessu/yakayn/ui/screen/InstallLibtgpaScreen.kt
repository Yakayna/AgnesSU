package com.agnessu.yakayn.ui.screen

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.FileOpen
import androidx.compose.material.icons.twotone.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.agnessu.yakayn.R
import com.agnessu.yakayn.data.shell.KsuCliRepository
import com.agnessu.yakayn.ui.component.settings.AppBackButton
import com.agnessu.yakayn.ui.navigation.LocalNavigator
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.io.File

private const val AOV_PACKAGE = "com.garena.game.kgvn"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallLibtgpaScreen() {
    val context = LocalContext.current
    val ksuCli: KsuCliRepository = koinInject()
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedName by remember { mutableStateOf<String?>(null) }
    var showReinstallDialog by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf("") }

    val patchingMsg = stringResource(R.string.libtgpa_patching)
    val reinstallingMsg = stringResource(R.string.libtgpa_reinstalling)
    val successMsg = stringResource(R.string.libtgpa_success)
    val notInstalledMsg = stringResource(R.string.libtgpa_game_not_installed)
    val libDirMissingMsg = stringResource(R.string.libtgpa_libdir_missing)
    val copyFailedMsg = stringResource(R.string.libtgpa_copy_failed)
    val failedFmt = stringResource(R.string.libtgpa_failed)

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            selectedName = queryDisplayName(context, uri) ?: uri.lastPathSegment ?: "libtgpa.so"
            log = ""
        }
    }

    fun runFlow(reinstall: Boolean) {
        val uri = selectedUri ?: return
        working = true
        log = ""
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                // 1. copy the picked file into app cache (root can read it there)
                val cacheFile = File(context.cacheDir, "libtgpa_pick.so")
                val copied = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        cacheFile.outputStream().use { output -> input.copyTo(output) }
                    } ?: throw IllegalStateException("openInputStream returned null")
                }.isSuccess
                if (!copied) return@withContext copyFailedMsg

                val shell = ksuCli.getRootShell()

                // 2. the game must be installed
                val (pathOk, pathOut) = shExec(shell, "pm path $AOV_PACKAGE")
                if (!pathOk || pathOut.none { it.startsWith("package:") }) {
                    return@withContext notInstalledMsg
                }

                // 3. optional reinstall (session install of all splits from the on-device apks)
                if (reinstall) {
                    withContext(Dispatchers.Main) { log = reinstallingMsg }
                    val apks = pathOut.filter { it.startsWith("package:") }
                        .map { it.removePrefix("package:").trim() }
                    val (createOk, createOut) = shExec(shell, "pm install-create -r -g")
                    val sessionId = Regex("\\[(\\d+)]").find(createOut.joinToString("\n"))
                        ?.groupValues?.get(1)
                    if (!createOk || sessionId == null) {
                        return@withContext failedFmt.format(createOut.joinToString("\n"))
                    }
                    apks.forEachIndexed { index, apk ->
                        shExec(shell, "pm install-write $sessionId split$index \"$apk\"")
                    }
                    val (commitOk, commitOut) = shExec(shell, "pm install-commit $sessionId")
                    if (!commitOk) {
                        shExec(shell, "pm install-abandon $sessionId")
                        return@withContext failedFmt.format(commitOut.joinToString("\n"))
                    }
                }

                withContext(Dispatchers.Main) { log = patchingMsg }

                // 4. locate the apk dir again (the path changes after a reinstall)
                val (path2Ok, path2Out) = shExec(shell, "pm path $AOV_PACKAGE")
                val base = path2Out.firstOrNull { it.startsWith("package:") }
                    ?.removePrefix("package:")?.trim()
                if (!path2Ok || base.isNullOrEmpty()) {
                    return@withContext notInstalledMsg
                }
                val apkDir = base.substringBeforeLast('/')
                val libDir = "$apkDir/lib/arm64"

                val (dirOk, _) = shExec(shell, "[ -d \"$libDir\" ]")
                if (!dirOk) return@withContext libDirMissingMsg

                val target = "$libDir/libtgpa.so"
                // preserve the original owner if the file exists, else default to system:system
                val (_, ownerOut) = shExec(shell, "stat -c '%U:%G' \"$target\" 2>/dev/null")
                val owner = ownerOut.firstOrNull { it.contains(':') }?.trim() ?: "system:system"

                val (cpOk, cpOut) = shExec(shell, "cp -f \"${cacheFile.absolutePath}\" \"$target\"")
                if (!cpOk) return@withContext failedFmt.format(cpOut.joinToString("\n"))
                shExec(shell, "chmod 755 \"$target\"")
                shExec(shell, "chown $owner \"$target\"")
                shExec(shell, "restorecon \"$target\" 2>/dev/null")

                cacheFile.delete()
                successMsg
            }
            log = result
            working = false
        }
    }

    if (showReinstallDialog) {
        AlertDialog(
            onDismissRequest = { if (!working) showReinstallDialog = false },
            title = { Text(stringResource(R.string.libtgpa_reinstall_dialog_title)) },
            text = { Text(stringResource(R.string.libtgpa_reinstall_dialog_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    showReinstallDialog = false
                    runFlow(reinstall = false)
                }) { Text(stringResource(R.string.libtgpa_reinstall_yes)) }
            },
            dismissButton = {
                Column {
                    TextButton(onClick = {
                        showReinstallDialog = false
                        runFlow(reinstall = true)
                    }) { Text(stringResource(R.string.libtgpa_reinstall_no)) }
                    TextButton(onClick = { showReinstallDialog = false }) {
                        Text(stringResource(R.string.libtgpa_cancel))
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.install_libtgpa_title)) },
                navigationIcon = {
                    val navigator = LocalNavigator.current
                    AppBackButton(onClick = { navigator.pop() })
                },
                windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.TwoTone.Star, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.libtgpa_screen_desc),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            OutlinedButton(
                onClick = { picker.launch(arrayOf("*/*")) },
                enabled = !working,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.TwoTone.FileOpen, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.libtgpa_pick_file))
            }

            Text(
                text = selectedName?.let { stringResource(R.string.libtgpa_selected_file, it) }
                    ?: stringResource(R.string.libtgpa_no_file),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = { showReinstallDialog = true },
                enabled = !working && selectedUri != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.libtgpa_start))
            }

            if (working) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (log.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = log,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Run a single command on [shell], collecting stdout+stderr, returning (success, output lines). */
private fun shExec(shell: Shell, cmd: String): Pair<Boolean, List<String>> {
    val out = ArrayList<String>()
    val result = shell.newJob().add(cmd).to(out, out).exec()
    return result.isSuccess to out
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()
}
