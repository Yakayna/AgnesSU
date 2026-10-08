package com.agnessu.yakayn.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agnessu.yakayn.R
import com.agnessu.yakayn.data.dirtyfrag.DirtyFragInstallPhase
import com.agnessu.yakayn.data.dirtyfrag.DirtyFragRepository
import com.agnessu.yakayn.ui.component.settings.AppBackButton
import com.agnessu.yakayn.ui.navigation.LocalNavigator
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DirtyFragScreen() {
    val repository = koinInject<DirtyFragRepository>()
    val navigator = LocalNavigator.current

    val state by repository.state.collectAsState()

    val snapshot = remember { repository.snapshot }

    var showLog by remember { mutableStateOf(false) }

    val statusText = when (state.phase) {
        DirtyFragInstallPhase.Checking -> stringResource(R.string.dirtyfrag_status_checking)
        DirtyFragInstallPhase.Ready -> stringResource(R.string.dirtyfrag_status_ready)
        DirtyFragInstallPhase.Exploiting -> stringResource(R.string.dirtyfrag_status_exploiting)
        DirtyFragInstallPhase.Installed -> stringResource(R.string.dirtyfrag_status_installed)
        DirtyFragInstallPhase.Failed -> stringResource(R.string.dirtyfrag_status_failed)
    }

    val canStart = !state.busy &&
        state.phase != DirtyFragInstallPhase.Installed &&
        snapshot.isAarch64 &&
        snapshot.isApiSupported

    if (showLog) {
        DirtyFragLogDialog(log = state.log, onDismiss = { showLog = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dirtyfrag_page_title)) },
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

            DirtyFragSectionTitle(stringResource(R.string.dirtyfrag_section_device))

            Text(
                text = stringResource(R.string.dirtyfrag_device_model, snapshot.model),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.dirtyfrag_device_kernel, snapshot.kernelVersionFull),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!snapshot.isAarch64) {
                Text(
                    text = stringResource(R.string.dirtyfrag_unsupported_arch),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (!snapshot.isApiSupported) {
                Text(
                    text = stringResource(R.string.dirtyfrag_unsupported_api),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            DirtyFragSectionTitle(stringResource(R.string.dirtyfrag_section_status))

            Text(
                text = stringResource(R.string.dirtyfrag_beta_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = {
                    showLog = true
                    repository.install()
                },
                enabled = canStart,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(stringResource(R.string.dirtyfrag_root_now))
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DirtyFragSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun DirtyFragLogDialog(log: String, onDismiss: () -> Unit) {
    val scrollState = rememberScrollState()
    LaunchedEffect(log.length) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dirtyfrag_close))
            }
        },
        title = { Text(stringResource(R.string.dirtyfrag_log_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(scrollState),
            ) {
                Text(
                    text = log.ifBlank { "…" },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        },
    )
}
