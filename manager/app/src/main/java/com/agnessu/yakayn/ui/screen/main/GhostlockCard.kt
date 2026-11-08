package com.agnessu.yakayn.ui.screen.main

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.agnessu.yakayn.R
import com.agnessu.yakayn.ui.component.DangerConsentDialog
import com.agnessu.yakayn.ui.component.rememberCustomDialog
import com.agnessu.yakayn.ui.navigation.LocalNavigator
import com.agnessu.yakayn.ui.navigation.Route

/**
 * Home-screen entry point into the dedicated GhostLock page. The page itself
 * (kernel exploit + profile-based payloads) lives in GhostlockScreen.
 */
@Composable
fun GhostlockButton(
    modifier: Modifier = Modifier,
) {
    val navigator = LocalNavigator.current
    val warning = rememberCustomDialog { dismiss ->
        DangerConsentDialog(
            title = stringResource(R.string.root_warning_title),
            message = stringResource(R.string.ghostlock_warning_message),
            onDismiss = dismiss,
            onConfirm = {
                dismiss()
                navigator.push(Route.Ghostlock)
            },
        )
    }

    Button(
        onClick = { warning.show() },
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(stringResource(R.string.home_ghostlock))
    }
}

/**
 * Home-screen entry point into the DirtyFrag one-tap root page (CVE-2026-43284,
 * [Beta]). Cross-OEM fast channel — the second root method next to GhostLock.
 */
@Composable
fun DirtyFragButton(
    modifier: Modifier = Modifier,
) {
    val navigator = LocalNavigator.current
    val warning = rememberCustomDialog { dismiss ->
        DangerConsentDialog(
            title = stringResource(R.string.root_warning_title),
            message = stringResource(R.string.dirtyfrag_warning_message),
            onDismiss = dismiss,
            onConfirm = {
                dismiss()
                navigator.push(Route.DirtyFrag)
            },
        )
    }

    Button(
        onClick = { warning.show() },
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(stringResource(R.string.home_dirtyfrag))
    }
}
