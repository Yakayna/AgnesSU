package com.agnessu.yakayn.ui.screen

import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Block
import androidx.compose.material.icons.twotone.ContentCopy
import androidx.compose.material.icons.twotone.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agnessu.yakayn.R
import com.agnessu.yakayn.data.dirtyfrag.DirtyFragInstallPhase
import com.agnessu.yakayn.data.dirtyfrag.DirtyFragRepository
import com.agnessu.yakayn.ui.component.settings.AppBackButton
import com.agnessu.yakayn.ui.component.settings.SegmentedColumn
import com.agnessu.yakayn.ui.component.settings.SettingsSwitchWidget
import com.agnessu.yakayn.ui.navigation.LocalNavigator
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DirtyFragScreen() {
    val repository = koinInject<DirtyFragRepository>()
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val state by repository.state.collectAsState()
    val snapshot = remember { repository.snapshot }

    // late-load restarts the app; re-check whenever this page is shown again.
    LaunchedEffect(Unit) { repository.refresh() }

    val statusText = if (state.rebootRequired) {
        stringResource(R.string.dirtyfrag_status_reboot_required)
    } else {
        when (state.phase) {
            DirtyFragInstallPhase.Checking -> stringResource(R.string.dirtyfrag_status_checking)
            DirtyFragInstallPhase.Ready -> stringResource(R.string.dirtyfrag_status_ready)
            DirtyFragInstallPhase.Exploiting -> stringResource(R.string.dirtyfrag_status_exploiting)
            DirtyFragInstallPhase.Verifying -> stringResource(R.string.dirtyfrag_status_verifying)
            DirtyFragInstallPhase.Installed -> stringResource(R.string.dirtyfrag_status_installed)
            DirtyFragInstallPhase.Failed -> stringResource(R.string.dirtyfrag_status_failed)
        }
    }

    // Installed is only set once KernelSU actually answers; a leftover /dev/df
    // from an earlier run this boot means the exploit cannot run again until the
    // device is rebooted, so disable the button and explain why instead of
    // letting every tap fail.
    val canStart = !state.busy &&
        !state.rebootRequired &&
        state.phase != DirtyFragInstallPhase.Installed &&
        snapshot.isAarch64 &&
        snapshot.isApiSupported

    val statusContainer = when {
        state.phase == DirtyFragInstallPhase.Installed -> MaterialTheme.colorScheme.primaryContainer
        state.rebootRequired || state.phase == DirtyFragInstallPhase.Failed ->
            MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
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

            Surface(
                color = statusContainer,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.dirtyfrag_manager_summary, context.packageName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.failureReason != null && !state.busy) {
                        Text(
                            text = state.failureReason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (state.rebootRequired) {
                        Text(
                            text = stringResource(R.string.dirtyfrag_reboot_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.dirtyfrag_beta_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SegmentedColumn(title = stringResource(R.string.dirtyfrag_section_options)) {
                item {
                    SettingsSwitchWidget(
                        icon = Icons.TwoTone.RestartAlt,
                        title = stringResource(R.string.dirtyfrag_option_soft_reboot),
                        description = stringResource(R.string.dirtyfrag_option_soft_reboot_desc),
                        enabled = !state.busy,
                        checked = state.softReboot,
                        onCheckedChange = repository::setSoftReboot,
                    )
                }
                item {
                    SettingsSwitchWidget(
                        icon = Icons.TwoTone.Block,
                        title = stringResource(R.string.dirtyfrag_option_disable_modules),
                        description = stringResource(R.string.dirtyfrag_option_disable_modules_desc),
                        enabled = !state.busy,
                        checked = state.disableModules,
                        onCheckedChange = repository::setDisableModules,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = { repository.install() },
                enabled = canStart,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(stringResource(R.string.dirtyfrag_root_now))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.dirtyfrag_section_log),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (state.log.isNotBlank()) {
                    TextButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(state.log))
                            Toast.makeText(
                                context,
                                context.getString(R.string.dirtyfrag_log_copied),
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.ContentCopy,
                            contentDescription = null,
                        )
                        Text(
                            text = stringResource(R.string.dirtyfrag_copy_log),
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }

            val logScroll = rememberScrollState()
            LaunchedEffect(state.log.length) {
                logScroll.animateScrollTo(logScroll.maxValue)
            }
            if (state.log.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .verticalScroll(logScroll)
                            .padding(12.dp),
                    ) {
                        Text(
                            text = state.log,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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
