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
 * (kernel exploit + [Beta] Iqoo/Vivo payloads) lives in GhostlockScreen.
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
