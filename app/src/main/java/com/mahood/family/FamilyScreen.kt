package com.mahood.family

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahood.family.core.Family
import com.mahood.family.core.sync.DriveClient
import com.mahood.family.core.sync.GoogleAuth
import com.mahood.family.core.sync.SyncEngine
import com.mahood.family.core.ui.Avatar
import com.mahood.family.core.ui.ScreenTitle
import com.mahood.family.core.ui.SectionLabel
import com.mahood.family.core.ui.relativeTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FamilyScreen(onModulesChanged: () -> Unit, onLeft: () -> Unit) {
    val ctx = Family.context
    val context = LocalContext.current
    val members = ctx.members()
    val status by SyncEngine.status.collectAsState()
    val scope = rememberCoroutineScope()
    var showInvite by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var moduleVersion by remember { mutableIntStateOf(0) }
    var update by remember { mutableStateOf<UpdateChecker.Update?>(null) }

    LaunchedEffect(Unit) { update = UpdateChecker.check() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        ScreenTitle("Family & features", "${Family.familyName ?: "Family"} · ${members.size} members")

        update?.let { u ->
            Card(
                Modifier.fillMaxWidth().padding(bottom = 12.dp), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Version ${u.version} is available", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.apkUrl))) }) { Text("Update") }
                }
            }
        }

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Members", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { showInvite = true }) {
                        Icon(Icons.Filled.PersonAdd, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Invite")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    members.forEach { m ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Avatar(m, 44.dp)
                            Text(m.name, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }

        SectionLabel("MODULES", Modifier.padding(top = 8.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                val enabledIds = remember(moduleVersion) { Family.enabledModules().map { it.id }.toSet() }
                Family.modules.forEach { m ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(m.icon, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.title, fontWeight = FontWeight.SemiBold)
                            Text(m.description, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(m.id in enabledIds, {
                            Family.setEnabled(m, it)
                            moduleVersion++
                            onModulesChanged()
                        })
                    }
                }
            }
        }

        SectionLabel("SYNC & PRIVACY", Modifier.padding(top = 8.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("No server", fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "Data lives on each phone and travels encrypted through the family's Google Drive folder. Google only stores scrambled files.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp),
                )
                Text(
                    when (val s = status) {
                        SyncEngine.Status.Idle -> "Not synced yet"
                        SyncEngine.Status.Syncing -> "Syncing…"
                        is SyncEngine.Status.Done -> "Synced ${relativeTime(s.atMillis)}"
                        is SyncEngine.Status.Failed -> s.message
                    },
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                )
                OutlinedButton(onClick = { scope.launch { SyncEngine.sync(context.applicationContext) } }, Modifier.padding(top = 8.dp)) {
                    Text("Sync now")
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Version ${BuildConfig.VERSION_NAME} · signed in as ${Family.myEmail}", fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { confirmLeave = true }) { Text("Leave family on this phone", color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(24.dp))
    }

    if (showInvite) InviteDialog(onDone = { showInvite = false })

    if (confirmLeave) AlertDialog(
        onDismissRequest = { confirmLeave = false },
        title = { Text("Leave the family?") },
        text = { Text("This removes the family's data from this phone only. Everyone else keeps theirs, and you can rejoin with an invite.") },
        confirmButton = { TextButton(onClick = { Family.leave(); confirmLeave = false; onLeft() }) { Text("Leave") } },
        dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Cancel") } },
    )
}

/** Share the Drive folder with a Google account, then hand them the invite as a QR code or text. */
@Composable
private fun InviteDialog(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var shared by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (shared) "Scan to join" else "Invite to the family") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!shared) {
                    Text("Enter their Google account email. They get access to the family's Drive folder, then you show them the invite.")
                    OutlinedTextField(email, { email = it }, label = { Text("Their Gmail address") }, singleLine = true)
                    TextButton(onClick = { shared = true }) { Text("Already added? Show the invite") }
                } else {
                    QrCode(Family.inviteText())
                    Text("On their phone: install the app, sign in, then scan this or paste the invite you send them.",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (!shared) {
                TextButton(enabled = !busy && email.contains("@"), onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        try {
                            val token = GoogleAuth.tokenSilently(context) ?: throw IllegalStateException("Sign in to Google first")
                            withContext(Dispatchers.IO) { DriveClient(token).shareWith(Family.folderId!!, email.trim()) }
                            shared = true
                        } catch (e: Exception) {
                            error = "Couldn't share: ${e.message}"
                        } finally {
                            busy = false
                        }
                    }
                }) { Text("Add") }
            } else {
                TextButton(onClick = {
                    val text = "Join ${Family.familyName} on our family app. Install it, sign in with Google, then paste this invite:\n\n${Family.inviteText()}"
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Send invite"))
                }) { Text("Send invite") }
            }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Close") } },
    )
}
