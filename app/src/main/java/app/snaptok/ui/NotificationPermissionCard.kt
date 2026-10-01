package app.snaptok.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

@Composable
fun NotificationPermissionCard(enabled: Boolean) {
    val context=LocalContext.current
    fun allowed()=Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(allowed()) }
    val request=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted=it }
    LifecycleResumeEffect(Unit) { granted=allowed();onPauseOrDispose { } }
    if (enabled && !granted) Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
        Text("Know when it is saved",style=MaterialTheme.typography.titleSmall)
        Text("Allow notifications for progress, completion and errors.",style=MaterialTheme.typography.bodySmall)
        TextButton(onClick={ if(Build.VERSION.SDK_INT>=33) request.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Enable notifications") }
        TextButton(onClick={ context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,context.packageName)) }) { Text("Open Android settings") }
    } }
}
