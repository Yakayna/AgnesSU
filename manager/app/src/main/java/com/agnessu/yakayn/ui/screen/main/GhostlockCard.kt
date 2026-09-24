package com.agnessu.yakayn.ui.screen.main

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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

private enum class GhostlockPhase { Idle, Checking, Ready, Unsupported, Running }

/**
 * Self-contained GhostLock action button. Tap to check the device kernel
 * against the online kernel list; when matched it flips to "Start", which runs
 * the exploit (full offsets pulled online) while streaming its log to a dialog.
 */
@Composable
fun GhostlockButton(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = koinInject<GhostlockRepository>()
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(GhostlockPhase.Idle) }
    var showLog by remember { mutableStateOf(false) }
    val logLines = remember { mutableStateListOf<String>() }

    if (showLog) {
        GhostlockLogDialog(lines = logLines, onDismiss = { showLog = false })
    }

    Button(
        onClick = {
            when (phase) {
                GhostlockPhase.Idle -> {
                    phase = GhostlockPhase.Checking
                    scope.launch {
                        val supported = repository.isKernelSupportedOnline()
                        phase = if (supported.getOrDefault(false)) {
                            GhostlockPhase.Ready
                        } else {
                            Toast.makeText(
                                context,
                                R.string.ghostlock_not_supported_online,
                                Toast.LENGTH_LONG,
                            ).show()
                            GhostlockPhase.Unsupported
                        }
                    }
                }

                GhostlockPhase.Ready -> {
                    phase = GhostlockPhase.Running
                    showLog = true
                    logLines.clear()
                    scope.launch {
                        val result = repository.runExploit(repository.kernelRelease) { line ->
                            logLines.add(line)
                        }
                        val message = result.fold(
                            onSuccess = { R.string.ghostlock_success },
                            onFailure = { R.string.ghostlock_failed },
                        )
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        phase = GhostlockPhase.Idle
                    }
                }

                else -> Unit
            }
        },
        enabled = phase != GhostlockPhase.Checking && phase != GhostlockPhase.Running,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(
            text = stringResource(
                when (phase) {
                    GhostlockPhase.Idle -> R.string.home_ghostlock
                    GhostlockPhase.Checking -> R.string.ghostlock_checking
                    GhostlockPhase.Ready -> R.string.ghostlock_start
                    GhostlockPhase.Unsupported -> R.string.ghostlock_unsupported
                    GhostlockPhase.Running -> R.string.ghostlock_running
                }
            )
        )
    }
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
