package com.mahood.family

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.mahood.family.core.Family
import com.mahood.family.core.Member
import com.mahood.family.core.data.Crypto
import com.mahood.family.core.sync.DriveClient
import com.mahood.family.core.sync.DriveException
import com.mahood.family.core.sync.SyncEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** First run: your name and colour, Google sign-in, then start a family or join one. */
@Composable
fun SetupScreen(initialInvite: String?, onDone: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableStateOf(Member.palette[0]) }
    var token by remember { mutableStateOf<String?>(null) }
    var email by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var inviteText by rememberSaveable { mutableStateOf(initialInvite ?: "") }
    var familyName by rememberSaveable { mutableStateOf("Mahood family") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(initialInvite) { if (initialInvite != null) inviteText = initialInvite }

    val signIn = rememberGoogleSignIn { t, err ->
        if (t == null) {
            error = err
            return@rememberGoogleSignIn
        }
        token = t
        scope.launch {
            busy = true
            try {
                val me = withContext(Dispatchers.IO) { DriveClient(t).me() }
                email = me.email
                if (name.isBlank()) name = me.name.substringBefore(' ')
                error = null
            } catch (e: Exception) {
                error = "Could not reach Google Drive: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    val scanner = androidx.activity.compose.rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { inviteText = it }
    }

    fun finish(folderId: String, key: String, famName: String) {
        Family.familyName = famName
        Family.folderId = folderId
        Family.familyKey = key
        Family.myEmail = email
        Family.store.put(Member.COLLECTION, email!!, mapOf("name" to name.trim(), "color" to color))
        onDone()
    }

    fun create() {
        val t = token ?: return
        scope.launch {
            busy = true
            error = null
            try {
                val folder = withContext(Dispatchers.IO) { DriveClient(t).createFolder("${familyName.trim()} (family app data)") }
                finish(folder, Crypto.newKey(), familyName.trim())
            } catch (e: Exception) {
                error = "Could not create the family folder: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun join() {
        val t = token ?: return
        val invite = Family.parseInvite(inviteText)
        if (invite == null) {
            error = "That doesn't look like an invite. It should start with mahoodfamily://"
            return
        }
        scope.launch {
            busy = true
            error = null
            try {
                withContext(Dispatchers.IO) { DriveClient(t).folderName(invite.folderId) }
                finish(invite.folderId, invite.key, invite.familyName)
                SyncEngine.sync(Family.context.appContext)
            } catch (e: DriveException) {
                error = if (e.code == 404) {
                    "You don't have access yet. Ask whoever invited you to add $email under Family › Invite, then try again."
                } else "Could not open the family folder: ${e.message}"
            } catch (e: Exception) {
                error = "Could not join: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Home, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        Text("Welcome to the family app", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Your calendar, lists and notes, shared with the family. There's no server: data stays on your phones and syncs, encrypted, through a Google Drive folder.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(name, { name = it }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("Your colour", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Member.palette.forEach { hex ->
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(Member.parseColor(hex))
                        .border(3.dp, if (hex == color) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                        .clickable { color = hex },
                )
            }
        }

        if (email == null) {
            Button(onClick = signIn, enabled = !busy && name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text("Sign in with Google")
            }
        } else {
            Text("Signed in as $email", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            HorizontalDivider()

            Text("Joining someone's family?", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                inviteText, { inviteText = it }, label = { Text("Paste the invite") },
                modifier = Modifier.fillMaxWidth(), maxLines = 3,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("Scan the family invite").setBeepEnabled(false))
                }) {
                    Icon(Icons.Filled.QrCodeScanner, null)
                    Spacer(Modifier.size(8.dp))
                    Text("Scan QR")
                }
                Button(onClick = ::join, enabled = !busy && inviteText.isNotBlank()) { Text("Join family") }
            }

            HorizontalDivider()
            Text("Or start a new family", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(familyName, { familyName = it }, label = { Text("Family name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = ::create, enabled = !busy && familyName.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text("Create family")
            }
        }

        if (busy) CircularProgressIndicator()
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(24.dp))
    }
}
