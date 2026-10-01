package com.agnessu.yakayn.ui.screen

import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.agnessu.yakayn.data.ghostlock.AndroidGhostlockRepository
import com.agnessu.yakayn.data.ghostlock.ExploitState
import com.agnessu.yakayn.data.ghostlock.ParseResult
import com.agnessu.yakayn.data.ghostlock.ProfileSource
import com.agnessu.yakayn.data.shizuku.ShizukuExploitRunner
import com.agnessu.yakayn.data.shizuku.ShizukuStatus
import com.agnessu.yakayn.ui.component.settings.AppBackButton
import com.agnessu.yakayn.ui.navigation.LocalNavigator
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GhostlockScreen() {
    val context = LocalContext.current
    val repository = koinInject<AndroidGhostlockRepository>()
    val shizukuRunner = koinInject<ShizukuExploitRunner>()
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()

    val settings by repository.settings.collectAsState()
    val exploitState by repository.exploitState.collectAsState()
    val shizukuStatus by shizukuRunner.status.collectAsState()

    var showConsent by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    val logLines = remember { mutableStateListOf<String>() }
    var cpuDropdownExpanded by remember { mutableStateOf(false) }
    var otaUrl by remember { mutableStateOf("") }
    var bootUri by remember { mutableStateOf<Uri?>(null) }
    var xblUri by remember { mutableStateOf<Uri?>(null) }
    var uefiUri by remember { mutableStateOf<Uri?>(null) }
    var extracting by remember { mutableStateOf(false) }
    var pendingOverwrite by remember { mutableStateOf(false) }
    val extractLog = remember { mutableStateListOf<String>() }

    val kernel = remember { repository.kernel }
    val selectedProfileId by repository.selectedProfileId.collectAsState()
    val profile = remember(selectedProfileId) { repository.resolveActiveProfile() }
    val exactBuiltin = remember { repository.matchBuiltinProfileExact() }
    val userProfiles = remember(selectedProfileId) { repository.userProfiles() }
    val cpuPairs = remember { repository.availableCpuPairs() }

    val busy = exploitState is ExploitState.Running || exploitState is ExploitState.Preparing

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val imported = repository.importProfile(uri)
            if (imported != null) {
                repository.selectProfile(imported.id)
                Toast.makeText(context, R.string.ghostlock_profile_imported, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, R.string.ghostlock_import_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val extractBootPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) bootUri = uri }
    val extractXblPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) xblUri = uri }
    val extractUefiPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) uefiUri = uri }

    fun startExtract(overwrite: Boolean) {
        scope.launch {
            extracting = true
            pendingOverwrite = false
            extractLog.clear()
            val result: ParseResult? = try {
                val xblPath = xblUri?.let { repository.cacheDocument(it, "xbl_config_extract.img") }
                val uefiPath = uefiUri?.let { repository.cacheDocument(it, "uefi_extract.bin") }
                val input = when {
                    otaUrl.isNotBlank() -> otaUrl.trim()
                    bootUri != null -> repository.cacheDocument(bootUri!!, "boot_extract.img")
                    else -> null
                }
                if (input == null) {
                    extractLog.add(context.getString(R.string.ghostlock_extract_no_input))
                    null
                } else {
                    repository.parseSource(input, xblPath, uefiPath, overwrite) { line ->
                        extractLog.add(line)
                    }
                }
            } catch (e: Exception) {
                extractLog.add("error: ${e.message}")
                null
            } finally {
                extracting = false
            }

            when (result) {
                is ParseResult.Parsed -> {
                    result.documentName?.let { repository.selectProfile(it) }
                    Toast.makeText(context, R.string.ghostlock_extract_success, Toast.LENGTH_LONG).show()
                }

                is ParseResult.RequiresOverwrite -> {
                    pendingOverwrite = true
                    Toast.makeText(context, R.string.ghostlock_extract_overwrite, Toast.LENGTH_LONG).show()
                }

                is ParseResult.AlreadyPresent -> {
                    Toast.makeText(context, R.string.ghostlock_extract_already, Toast.LENGTH_SHORT).show()
                }

                is ParseResult.Failed -> {
                    Toast.makeText(context, R.string.ghostlock_extract_failed, Toast.LENGTH_LONG).show()
                }

                null -> Unit
            }
        }
    }

    fun startRun() {
        if (settings.useShizuku && shizukuStatus == ShizukuStatus.PERMISSION_REQUIRED) {
            shizukuRunner.requestPermission()
            return
        }
        showConsent = false
        showLog = true
        logLines.clear()
        scope.launch {
            val result = repository.runExploit { line -> logLines.add(line) }
            Toast.makeText(
                context,
                result.fold(
                    onSuccess = { R.string.ghostlock_success },
                    onFailure = { R.string.ghostlock_failed },
                ),
                Toast.LENGTH_LONG,
            ).show()
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

            // --- Device info ---
            GhostlockSectionTitle(stringResource(R.string.ghostlock_section_device))

            Text(
                text = stringResource(R.string.ghostlock_current_kernel, kernel.release),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "SoC: ${kernel.soc} • ${kernel.deviceName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!repository.isSupportedArch()) {
                Text(
                    text = stringResource(R.string.ghostlock_unsupported_arch),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // --- Profile ---
            GhostlockSectionTitle(stringResource(R.string.ghostlock_section_profile))

            when {
                profile != null && profile.source == ProfileSource.USER_IMPORTED && profile.isValid -> {
                    Text(
                        text = profile.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.ghostlock_profile_user_active),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                exactBuiltin != null -> {
                    Text(
                        text = stringResource(R.string.ghostlock_profile_supported),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "${stringResource(R.string.ghostlock_profile_builtin)} · ${exactBuiltin.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> {
                    Text(
                        text = stringResource(R.string.ghostlock_no_exact_profile),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.ghostlock_no_exact_profile_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (exactBuiltin == null && userProfiles.isNotEmpty()) {
                userProfiles.forEach { userProfile ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { repository.selectProfile(userProfile.id) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = userProfile.id == selectedProfileId,
                            onClick = { repository.selectProfile(userProfile.id) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = userProfile.name,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("*/*")) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.ghostlock_import_profile))
            }

            // --- Offset extraction ---
            GhostlockSectionTitle(stringResource(R.string.ghostlock_section_extract))

            OutlinedTextField(
                value = otaUrl,
                onValueChange = { otaUrl = it },
                label = { Text(stringResource(R.string.ghostlock_extract_ota_url)) },
                singleLine = true,
                enabled = !extracting,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedButton(
                onClick = { extractBootPicker.launch(arrayOf("*/*")) },
                enabled = !extracting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    bootUri?.let {
                        stringResource(R.string.ghostlock_extract_boot_picked, it.lastPathSegment ?: "")
                    } ?: stringResource(R.string.ghostlock_extract_pick_boot),
                )
            }

            OutlinedButton(
                onClick = { extractXblPicker.launch(arrayOf("*/*")) },
                enabled = !extracting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    xblUri?.let {
                        stringResource(R.string.ghostlock_extract_xbl_picked, it.lastPathSegment ?: "")
                    } ?: stringResource(R.string.ghostlock_extract_pick_xbl),
                )
            }

            OutlinedButton(
                onClick = { extractUefiPicker.launch(arrayOf("*/*")) },
                enabled = !extracting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    uefiUri?.let {
                        stringResource(R.string.ghostlock_extract_uefi_picked, it.lastPathSegment ?: "")
                    } ?: stringResource(R.string.ghostlock_extract_pick_uefi),
                )
            }

            if (pendingOverwrite) {
                Text(
                    text = stringResource(R.string.ghostlock_extract_overwrite),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { startExtract(overwrite = true) },
                        enabled = !extracting,
                    ) {
                        Text(stringResource(R.string.ghostlock_extract_overwrite_confirm))
                    }
                    TextButton(onClick = { pendingOverwrite = false }) {
                        Text(stringResource(R.string.ghostlock_extract_cancel))
                    }
                }
            }

            Button(
                onClick = { startExtract(overwrite = false) },
                enabled = !extracting && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (extracting) stringResource(R.string.ghostlock_extract_running)
                    else stringResource(R.string.ghostlock_extract_run),
                )
            }

            if (extractLog.isNotEmpty()) {
                Text(
                    text = extractLog.joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }

            // --- Execution settings ---
            GhostlockSectionTitle(stringResource(R.string.ghostlock_section_settings))

            val visibleCpuPairs = cpuPairs.take(20)
            ExposedDropdownMenuBox(
                expanded = cpuDropdownExpanded,
                onExpandedChange = { cpuDropdownExpanded = it },
            ) {
                OutlinedTextField(
                    value = settings.cpuPair.toString(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.ghostlock_cpu_pair)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cpuDropdownExpanded) },
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownMenuPopup(
                    expanded = cpuDropdownExpanded,
                    onDismissRequest = { cpuDropdownExpanded = false },
                ) {
                    DropdownMenuGroup(
                        shapes = MenuDefaults.groupShapes()
                    ) {
                        visibleCpuPairs.forEachIndexed { index, pair ->
                            DropdownMenuItem(
                                shape = MenuDefaults.itemShape(index, visibleCpuPairs.size).shape,
                                text = { Text(pair.toString()) },
                                onClick = {
                                    repository.updateSettings { copy(cpuPair = pair) }
                                    cpuDropdownExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            SettingsRow(
                label = stringResource(R.string.ghostlock_safe_mode),
                checked = settings.safeMode,
                onCheckedChange = { repository.updateSettings { copy(safeMode = it) } },
            )

            SettingsRow(
                label = stringResource(R.string.ghostlock_use_shizuku),
                checked = settings.useShizuku,
                onCheckedChange = { repository.updateSettings { copy(useShizuku = it) } },
                subtitle = when (shizukuStatus) {
                    ShizukuStatus.NOT_RUNNING -> stringResource(R.string.ghostlock_shizuku_not_running)
                    ShizukuStatus.PERMISSION_REQUIRED -> stringResource(R.string.ghostlock_shizuku_permission)
                    ShizukuStatus.READY -> stringResource(R.string.ghostlock_shizuku_ready)
                },
            )

            // --- Status ---
            val stateText = when (val state = exploitState) {
                is ExploitState.Running -> "Running: ${state.step} ${state.status}"
                is ExploitState.Preparing -> "Preparing..."
                is ExploitState.Finished -> "Finished (exit ${state.exitCode})"
                is ExploitState.Failed -> "Failed: ${state.error}"
                is ExploitState.Idle -> null
            }
            if (stateText != null) {
                Text(
                    text = stateText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(4.dp))

            // --- Start button ---
            Button(
                onClick = { showConsent = true },
                enabled = !busy && profile?.isValid == true && repository.isSupportedArch(),
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
private fun SettingsRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
