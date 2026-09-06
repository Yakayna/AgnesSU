package com.agnessu.yakayn.ui.component

import androidx.compose.runtime.Composable
import com.agnessu.yakayn.domain.model.KernelStatus

@Composable
inline fun KsuIsValid(
    status: KernelStatus,
    content: @Composable () -> Unit
) {
    if (status.isValid)
        content()
}
