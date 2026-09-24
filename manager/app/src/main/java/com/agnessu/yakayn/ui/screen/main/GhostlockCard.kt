package com.agnessu.yakayn.ui.screen.main

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.agnessu.yakayn.R
import com.agnessu.yakayn.data.ghostlock.GhostlockRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Self-contained GhostLock entry point. Tapping it opens a menu with:
 *  - "Check online": verifies the running kernel against the online list (the
 *    device is deliberately NOT checked — only the kernel) and, on a match,
 *    runs the bundled payload straight away.
 *  - "Use custom payload.so": runs a user-picked payload.so, no kernel check.
 */
@Composable
fun GhostlockButton(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = koinInject<GhostlockRepository>()
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    val logLines = remember { mutableStateListOf<String>() }

    val pickPayloadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            showMenu = false
            showLog = true
            running = true
            logLines.clear()
            scope.launch {
                val result = repository.runCustomPayload(uri) { line -> logLines.add(line) }
                Toast.makeText(
                    context,
                    result.fold(
                        onSuccess = { R.string.ghostlock_success },
                        onFailure = { R.string.ghostlock_failed },
                    ),
                    Toast.LENGTH_LONG,
                ).show()
                running = false
            }
        }
    }

    if (showMenu) {
        GhostlockMenuDialog(
            enabled = !running,
            onCheckOnline = {
                showMenu = false
                showLog = true
                running = true
                logLines.clear()
                scope.launch {
                    val supported = repository.isKernelSupportedOnline().getOrDefault(false)
                    if (!supported) {
                        Toast.makeText(
                            context,
                            R.string.ghostlock_not_supported_online,
                            Toast.LENGTH_LONG,
                        ).show()
                        showLog = false
                    } else {
                        val result = repository.runExploit(repository.kernelRelease) { line ->
                            logLines.add(line)
                        }
                        Toast.makeText(
                            context,
                            result.fold(
                                onSuccess = { R.string.ghostlock_success },
                                onFailure = { R.string.ghostlock_failed },
                            ),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    running = false
                }
            },
            onUseCustomPayload = {
                pickPayloadLauncher.launch(arrayOf("*/*"))
            },
            onDismiss = { showMenu = false },
        )
    }

    if (showLog) {
        GhostlockLogDialog(lines = logLines, onDismiss = { showLog = false })
    }

    Button(
        onClick = { showMenu = true },
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(stringResource(R.string.home_ghostlock))
    }
}

@Composable
private fun GhostlockMenuDialog(
    enabled: Boolean,
    onCheckOnline: () -> Unit,
    onUseCustomPayload: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ghostlock_close))
            }
        },
        title = { Text(stringResource(R.string.ghostlock_log_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onCheckOnline,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(stringResource(R.string.ghostlock_check_online))
                }
                OutlinedButton(
                    onClick = onUseCustomPayload,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.ghostlock_use_custom_payload))
                }
            }
        },
    )
}

@Composable
private fun GhostlockLogDialog(
    lines: List<String>,
    onDismiss: () -> Unit,
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) scrollState.scrollTo(scrollState.maxValue)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ghostlock_close))
            }
        },
        title = { Text(stringResource(R.string.ghostlock_log_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(scrollState)
            ) {
                Text(
                    text = lines.joinToString("\n").ifBlank { "…" },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        },
    )
}
