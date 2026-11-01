package com.mahood.family

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.identity.Identity
import com.mahood.family.core.sync.GoogleAuth
import kotlinx.coroutines.launch

/**
 * Returns a function that signs in with Google (showing the consent screen if needed)
 * and calls back with an access token, or with an error message.
 */
@Composable
fun rememberGoogleSignIn(onResult: (token: String?, error: String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            onResult(null, "Sign-in was cancelled")
            return@rememberLauncherForActivityResult
        }
        try {
            val auth = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(result.data)
            onResult(auth.accessToken, if (auth.accessToken == null) "No access granted" else null)
        } catch (e: Exception) {
            onResult(null, e.message ?: "Sign-in failed")
        }
    }
    return {
        scope.launch {
            try {
                val auth = GoogleAuth.authorize(context)
                val pending = auth.pendingIntent
                if (auth.hasResolution() && pending != null) {
                    launcher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                } else {
                    onResult(auth.accessToken, null)
                }
            } catch (e: Exception) {
                onResult(null, "Google sign-in failed: ${e.message}. Check the Google Cloud setup (package name and SHA-1).")
            }
        }
    }
}
