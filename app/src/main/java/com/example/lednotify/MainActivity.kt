package com.example.lednotify

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.lednotify.ui.theme.LedNotifyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LedNotifyTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasOverlayPermission by remember {
        mutableStateOf(isOverlayPermissionGranted(context))
    }
    var hasNotificationListenerAccess by remember {
        mutableStateOf(isNotificationListenerEnabled(context))
    }
    var hasPostNotificationPermission by remember {
        mutableStateOf(isPostNotificationPermissionGranted(context))
    }

    // Refresh permission states whenever the activity returns to foreground
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = isOverlayPermissionGranted(context)
                hasNotificationListenerAccess = isNotificationListenerEnabled(context)
                hasPostNotificationPermission = isPostNotificationPermissionGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Android 13+ Notification Runtime Permission Request
    val postNotificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPostNotificationPermission = granted
        if (granted) {
            Toast.makeText(context, "Notification permission granted", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) && !hasPostNotificationPermission) {
            postNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "AMOLED LED Notify",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Simulate a notification LED dot on AMOLED displays. Pure black overlay saves battery while keeping the blinking LED visible.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Permission status indicators
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Permissions Status",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium
                    )

                    PermissionStatusRow(
                        label = "Overlay Permission",
                        isGranted = hasOverlayPermission
                    )
                    PermissionStatusRow(
                        label = "Notification Listener",
                        isGranted = hasNotificationListenerAccess
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        PermissionStatusRow(
                            label = "Post Notifications",
                            isGranted = hasPostNotificationPermission
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Button 1: Allow Overlay Permission
            Button(
                onClick = {
                    if (Settings.canDrawOverlays(context)) {
                        Toast.makeText(context, "Overlay permission already granted", Toast.LENGTH_SHORT).show()
                    } else {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            "package:${context.packageName}".toUri()
                        )
                        context.startActivity(intent)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "Allow Overlay Permission",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Button 2: Allow Notification Access
            Button(
                onClick = {
                    if (isNotificationListenerEnabled(context)) {
                        Toast.makeText(context, "Notification access already granted", Toast.LENGTH_SHORT).show()
                    } else {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        context.startActivity(intent)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "Allow Notification Access",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Button 3: Test LED
            Button(
                onClick = {
                    if (!Settings.canDrawOverlays(context)) {
                        Toast.makeText(context, "Please allow Overlay Permission first", Toast.LENGTH_LONG).show()
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            "package:${context.packageName}".toUri()
                        )
                        context.startActivity(intent)
                        return@Button
                    }
                    if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) && !hasPostNotificationPermission) {
                        postNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    LedOverlayService.start(context)
                    Toast.makeText(context, "Starting LED Overlay... Tap screen to dismiss", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2E7D32)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "Test LED",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Button 4: Stop LED
            OutlinedButton(
                onClick = {
                    LedOverlayService.stop(context)
                    Toast.makeText(context, "LED Overlay stopped", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "Stop LED",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "When an incoming notification arrives, the screen wakes up with a pure black display and a blinking green LED in the top-right corner. Tap anywhere on the overlay to dismiss it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun PermissionStatusRow(label: String, isGranted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = if (isGranted) "Granted" else "Missing",
            color = if (isGranted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun isOverlayPermissionGranted(context: Context): Boolean {
    return Settings.canDrawOverlays(context)
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
    return enabledListeners.contains(context.packageName)
}

private fun isPostNotificationPermissionGranted(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}
