package com.agnessu.yakayn.ui.screen.main

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.agnessu.yakayn.R
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

    Button(
        onClick = { navigator.push(Route.Ghostlock) },
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
 * Home-screen entry point into the Samsung one-tap root page (Root-My-Galaxy
 * engine, [Beta]). Lives next to the GhostLock button on the install card.
 */
@Composable
fun SamsungRootButton(
    modifier: Modifier = Modifier,
) {
    val navigator = LocalNavigator.current

    Button(
        onClick = { navigator.push(Route.SamsungRoot) },
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(stringResource(R.string.home_samsung_root))
    }
}

/**
 * Home-screen entry point into the DirtyFrag one-tap root page (CVE-2026-43284,
 * [Beta]). Cross-OEM fast channel — the third root method next to GhostLock and
 * Samsung.
 */
@Composable
fun DirtyFragButton(
    modifier: Modifier = Modifier,
) {
    val navigator = LocalNavigator.current

    Button(
        onClick = { navigator.push(Route.DirtyFrag) },
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(stringResource(R.string.home_dirtyfrag))
    }
}
