package com.agnessu.yakayn.ui.screen

import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agnessu.yakayn.R
import com.agnessu.yakayn.data.ghostlock.GhostlockRepository
import com.agnessu.yakayn.data.ghostlock.IqooVivoPayload
import com.agnessu.yakayn.data.ghostlock.IqooVivoPayloads
import com.agnessu.yakayn.data.shizuku.ShellTransport
import com.agnessu.yakayn.ui.component.settings.AppBackButton
import com.agnessu.yakayn.ui.navigation.LocalNavigator
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** What the "Start" button is queued to run once the user agrees. */
private enum class PendingMode { None, Online, CustomPayload, IqooVivo }

/**
 * Dedicated GhostLock page. Home taps into this instead of a bare dialog so
 * future payload families can be added alongside the two sections here:
 *  - the GhostLock kernel exploit (online check + custom payload.so), and
 *  - the [Beta] Iqoo/Vivo preload payloads, auto-matched by device codename.
 *
 * Picking any action reveals a shared "Start" button; pressing it shows the
 * warning dialog, and only after the user agrees does the payload run, its
 * output streamed into a log dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GhostlockScreen() {
    val context = LocalContext.current
    val repository = koinInject<GhostlockRepository>()
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf(false) }
    var pendingMode by remember { mutableStateOf(PendingMode.None) }
    var customUri by remember { mutableStateOf<Uri?>(null) }
    var selectedIqoo by remember { mutableStateOf<IqooVivoPayload?>(null) }
    var showConsent by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    val logLines = remember { mutableStateListOf<String>() }

    // Device codename comes from ro.product.device (Build.DEVICE); a few Vivo
    // builds expose it under a sibling property instead, so match any of them.
    val candidateCodenames = remember {
        listOf(Build.DEVICE, Build.PRODUCT, Build.BOARD, Build.HARDWARE)
            .map { it?.trim().orEmpty() }
            .filter { it.isNotEmpty() }
            .distinct()
    }
    val autoMatches = remember(candidateCodenames) {
        candidateCodenames.flatMap { IqooVivoPayloads.matching(it) }.distinct()
    }

    val pickPayloadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            customUri = uri
            selectedIqoo = null
            pendingMode = PendingMode.CustomPayload
        }
    }

    fun startRun() {
        val mode = pendingMode
        val iqoo = selectedIqoo

        // The Iqoo/Vivo path stages files and fires the exploit from the shell
        // domain, so it needs Shizuku up and authorized before anything runs.
        if (mode == PendingMode.IqooVivo) {
            if (!ShellTransport.alive) {
                Toast.makeText(
                    context,
                    R.string.ghostlock_shizuku_not_running,
                    Toast.LENGTH_LONG,
                ).show()
                return
            }
            if (!ShellTransport.permissionGranted()) {
                ShellTransport.requestPermission { startRun() }
                return
            }
        }

        showConsent = false
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

                PendingMode.IqooVivo ->
                    iqoo?.let { repository.runIqooVivoPayload(it) { line -> logLines.add(line) } }
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
            selectedIqoo = null
        }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ghostlock_page_title)) },
                navigationIcon = {
                    AppBackButton(onClick = { navigator.pop() })
                },
                windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
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

            Text(
                text = stringResource(R.string.ghostlock_current_kernel, repository.kernelRelease),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            GhostlockSectionTitle(stringResource(R.string.ghostlock_section_kernel))

            Button(
                onClick = {
                    busy = true
                    scope.launch {
                        val supported = repository.isKernelSupportedOnline().getOrDefault(false)
                        if (supported) {
                            pendingMode = PendingMode.Online
                            selectedIqoo = null
                            customUri = null
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
                onClick = { pickPayloadLauncher.launch(arrayOf("*/*")) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.ghostlock_use_custom_payload))
            }

            GhostlockSectionTitle(stringResource(R.string.ghostlock_section_iqoo_vivo))

            if (Build.DEVICE.isNullOrBlank().not()) {
                Text(
                    text = stringResource(
                        R.string.ghostlock_detected_device,
                        Build.DEVICE,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IqooVivoPayloads.all.forEach { payload ->
                IqooVivoEntryRow(
                    payload = payload,
                    selected = selectedIqoo == payload,
                    autoMatched = autoMatches.contains(payload),
                    onClick = {
                        selectedIqoo = payload
                        customUri = null
                        pendingMode = PendingMode.IqooVivo
                    },
                )
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = { showConsent = true },
                enabled = !busy && pendingMode != PendingMode.None,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(stringResource(R.string.ghostlock_start))
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GhostlockSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun IqooVivoEntryRow(
    payload: IqooVivoPayload,
    selected: Boolean,
    autoMatched: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = payload.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
            if (autoMatched) {
                Text(
                    text = stringResource(R.string.ghostlock_iqoo_vivo_auto),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
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
