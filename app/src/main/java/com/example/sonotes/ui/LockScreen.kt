package com.example.sonotes.ui

import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LockScreen(
    isFingerprintEnabled: Boolean,
    isPinEnabled: Boolean,
    correctPin: String,
    onUnlockSuccess: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    var showPinInputMode by remember { mutableStateOf(!isFingerprintEnabled) }
    var enteredPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    fun triggerBiometric() {
        if (activity != null && isFingerprintEnabled) {
            val executor = ContextCompat.getMainExecutor(activity)
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onUnlockSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (isPinEnabled) {
                        showPinInputMode = true
                    }
                }
            }

            val biometricPrompt = BiometricPrompt(activity, executor, callback)
            val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock SoNotes")
                .setSubtitle("Use your fingerprint to continue")

            if (isPinEnabled) {
                promptInfoBuilder.setNegativeButtonText("Use PIN instead")
            } else {
                promptInfoBuilder.setNegativeButtonText("Cancel")
            }

            try {
                biometricPrompt.authenticate(promptInfoBuilder.build())
            } catch (_: Exception) {
                if (isPinEnabled) {
                    showPinInputMode = true
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (isFingerprintEnabled) {
            triggerBiometric()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "App Locked",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "SoNotes is Locked",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = if (showPinInputMode) "Enter your PIN to unlock" else "Authentication required to open app",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(32.dp))

            if (showPinInputMode) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = enteredPin,
                        onValueChange = { input ->
                            if (input.length <= 8 && input.all { it.isDigit() }) {
                                enteredPin = input
                                pinError = false
                            }
                        },
                        label = { Text("Enter PIN") },
                        isError = pinError,
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(0.8f)
                    )

                    if (pinError) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Incorrect PIN code. Try again.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    Button(
                        onClick = {
                            if (enteredPin == correctPin && correctPin.isNotBlank()) {
                                onUnlockSuccess()
                            } else {
                                pinError = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(0.8f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Unlock", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    if (isFingerprintEnabled) {
                        Spacer(Modifier.height(12.dp))
                        TextButton(
                            onClick = {
                                showPinInputMode = false
                                triggerBiometric()
                            }
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.height(4.dp))
                            Text("Use Fingerprint")
                        }
                    }
                }
            } else {
                if (isFingerprintEnabled) {
                    OutlinedButton(
                        onClick = { triggerBiometric() },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Tap to Scan Fingerprint")
                    }

                    if (isPinEnabled) {
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = { showPinInputMode = true }) {
                            Text("Use PIN instead")
                        }
                    }
                }
            }
        }
    }
}
