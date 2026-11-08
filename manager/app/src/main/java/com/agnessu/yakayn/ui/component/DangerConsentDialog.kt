package com.agnessu.yakayn.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.agnessu.yakayn.R
import kotlinx.coroutines.delay

private const val COUNTDOWN_SECONDS = 5

/**
 * One-tap root confirmation dialog: the user must read the device/kernel
 * warning before the OK button unlocks, and it counts down so the warning is
 * not dismissed by a reflex tap.
 */
@Composable
fun DangerConsentDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var secondsLeft by remember { mutableIntStateOf(COUNTDOWN_SECONDS) }

    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft -= 1
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(text = message)
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = secondsLeft == 0,
            ) {
                Text(
                    text = if (secondsLeft > 0) {
                        stringResource(R.string.root_warning_ok_countdown, secondsLeft)
                    } else {
                        stringResource(R.string.root_warning_ok)
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
    )
}
