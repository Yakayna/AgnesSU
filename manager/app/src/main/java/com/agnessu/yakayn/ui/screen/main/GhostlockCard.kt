package com.agnessu.yakayn.ui.screen.main

import android.net.Uri
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

/** What the "Start" button is queued to run once the user agrees. */
private enum class PendingMode { None, Online, CustomPayload }

/**
 * Self-contained GhostLock entry point. Tapping it opens a menu with:
 *  - "Check online": verifies the running kernel against the online list (the
 *    device is deliberately NOT checked — only the kernel) and, on a match,
 *    reveals a "Start" button.
 *  - "Use custom payload.so": runs a user-picked payload.so, no kernel check.
 *
 * Pressing "Start" shows a warning; only after the user agrees does the payload
 * actually execute, streaming its log to a dialog.
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
    var showConsent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var pendingMode by remember { mutableStateOf(PendingMode.None) }
    var customUri by remember { mutableStateOf<Uri?>(null) }
    val logLines = remember { mutableStateListOf<String>() }

    val pickPayloadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            customUri = uri
            pendingMode = PendingMode.CustomPayload
        }
    }

    fun startRun() {
        val mode = pendingMode
        showConsent = false
        showMenu = false
        showLog = true
        busy = true
        logLines.clear()
        scope.launch {
            val result = when (mode) {
                PendingMode.Online ->
                    repository.runExploit(repository.kernelRelease) { line -> logLines.add(line) }

                PendingMode.CustomPayload ->
                    customUri?.let { repository.runCustomPayload(it) { line -> logLines.add(line) } }
                        ?: Result.failure(IllegalStateException("no payload selected"))

                PendingMode.None ->
                    Result.failure(IllegalStateException("no action queued"))
            }
            Toast.makeText(
                context,
                result.fold(
                    onSuccess = { R.string.ghostlock_success },
                    onFailure = { R.string.ghostlock_failed },
                ),
                Toast.LENGTH_LONG,
            ).show()
            busy = false
            pendingMode = PendingMode.None
            customUri = null
        }
    }

    if (showMenu) {
        GhostlockMenuDialog(
            pendingMode = pendingMode,
            busy = busy,
            onCheckOnline = {
                busy = true
                scope.launch {
                    val supported = repository.isKernelSupportedOnline().getOrDefault(false)
                    if (supported) {
                        pendingMode = PendingMode.Online
                    } else {
                        Toast.makeText(
                            context,
                            R.string.ghostlock_not_supported_online,
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    busy = false
                }
            },
            onUseCustomPayload = {
                pickPayloadLauncher.launch(arrayOf("*/*"))
            },
            onStart = { showConsent = true },
            onDismiss = {
                showMenu = false
                pendingMode = PendingMode.None
                customUri = null
            },
        )
    }

    if (showConsent) {
        GhostlockConsentDialog(
            onAgree = { startRun() },
            onDisagree = { showConsent = false },
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
    pendingMode: PendingMode,
    busy: Boolean,
    onCheckOnline: () -> Unit,
    onUseCustomPayload: () -> Unit,
    onStart: () -> Unit,
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
                if (pendingMode == PendingMode.None) {
                    Button(
                        onClick = onCheckOnline,
                        enabled = !busy,
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
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.ghostlock_use_custom_payload))
                    }
                } else {
                    Button(
                        onClick = onStart,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(stringResource(R.string.ghostlock_start))
                    }
                }
            }
        },
    )
}

@Composable
private fun GhostlockConsentDialog(
    onAgree: () -> Unit,
    onDisagree: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDisagree,
        title = { Text(stringResource(R.string.ghostlock_warning_title)) },
        text = { Text(stringResource(R.string.ghostlock_warning)) },
        confirmButton = {
            TextButton(onClick = onAgree) {
                Text(stringResource(R.string.ghostlock_agree))
            }
        },
        dismissButton = {
            TextButton(onClick = onDisagree) {
                Text(stringResource(R.string.ghostlock_disagree))
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
