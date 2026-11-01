package com.mahood.family.core.sync

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

/**
 * Google sign-in for Drive access, using Google Play services' AuthorizationClient.
 * The app is registered as an Android OAuth client (package name + signing SHA-1),
 * so no client secret or API key is embedded in the app.
 */
object GoogleAuth {
    /**
     * Full Drive scope so each phone can read the change files other family members
     * upload to the shared folder. While the Google Cloud project is in "testing"
     * mode this works for the listed test users without Google verification.
     */
    const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive"

    private fun request() = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_SCOPE), Scope("email"), Scope("profile")))
        .build()

    /** May return a result that needs the user to approve (hasResolution() == true). */
    suspend fun authorize(context: Context): AuthorizationResult =
        Identity.getAuthorizationClient(context).authorize(request()).await()

    /** Access token if the user has already approved, without showing any UI; null otherwise. */
    suspend fun tokenSilently(context: Context): String? = try {
        val result = authorize(context)
        if (result.hasResolution()) null else result.accessToken
    } catch (e: Exception) {
        null
    }
}

class NeedsSignInException : Exception("Google sign-in needed")
