package com.adbkit.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.adbkit.app.ui.strings.LocalStrings

@Composable
fun OnboardingDialog(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = { onFinish() },
        modifier = modifier,
        title = { Text(strings.onboardingTitle, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OnboardingStep(
                    icon = { Icon(Icons.Filled.Devices, contentDescription = null) },
                    title = strings.onboardingStep1Title,
                    body = strings.onboardingStep1Body
                )
                OnboardingStep(
                    icon = { Icon(Icons.Filled.Security, contentDescription = null) },
                    title = strings.onboardingStep2Title,
                    body = strings.onboardingStep2Body
                )
                OnboardingStep(
                    icon = { Icon(Icons.Filled.Build, contentDescription = null) },
                    title = strings.onboardingStep3Title,
                    body = strings.onboardingStep3Body
                )
                Text(
                    text = strings.onboardingTip,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                Text(strings.onboardingFinish)
            }
        },
        dismissButton = {}
    )
}

@Composable
private fun OnboardingStep(
    icon: @Composable () -> Unit,
    title: String,
    body: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) { icon() }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.Bold)
            Text(text = body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
