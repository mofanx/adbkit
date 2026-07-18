package com.adbkit.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.adbkit.app.ui.strings.LocalStrings

@Composable
fun ConnectionErrorMessage(type: String) {
    val strings = LocalStrings.current
    val message = when (type) {
        "refused" -> strings.connectionRefused
        "unreachable" -> strings.connectionUnreachable
        "offline" -> strings.connectionOffline
        "auth" -> strings.connectionAuthFailed
        "invalid" -> strings.connectionInvalidIp
        else -> strings.connectionFailed
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp)
        )
    }
}
