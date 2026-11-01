package com.mahood.family.core.sync

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Minimal Google Drive v3 REST client: just what the sync engine needs. Call from a background thread. */
class DriveClient(private val token: String) {
    data class DriveFile(val id: String, val name: String)
    data class User(val email: String, val name: String)

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val api = "https://www.googleapis.com/drive/v3"
    private val json = "application/json; charset=utf-8".toMediaType()

    private fun call(request: Request.Builder): Response {
        val response = http.newCall(request.header("Authorization", "Bearer $token").build()).execute()
        if (!response.isSuccessful) {
            val body = response.body?.string().orEmpty()
            response.close()
            throw DriveException(response.code, body.take(300))
        }
        return response
    }

    private fun callJson(request: Request.Builder): JSONObject =
        call(request).use { JSONObject(it.body!!.string()) }

    fun me(): User {
        val o = callJson(Request.Builder().url("$api/about?fields=user(displayName,emailAddress)")).getJSONObject("user")
        return User(o.getString("emailAddress"), o.optString("displayName", ""))
    }

    fun createFolder(name: String): String {
        val meta = JSONObject().put("name", name).put("mimeType", "application/vnd.google-apps.folder")
        return callJson(Request.Builder().url("$api/files?fields=id").post(meta.toString().toRequestBody(json))).getString("id")
    }

    /** Give another Google account edit access to the family folder (Google emails them). */
    fun shareWith(folderId: String, email: String) {
        val body = JSONObject().put("type", "user").put("role", "writer").put("emailAddress", email)
        call(
            Request.Builder().url("$api/files/$folderId/permissions?sendNotificationEmail=true")
                .post(body.toString().toRequestBody(json))
        ).close()
    }

    /** Checks we can see the folder; throws DriveException(404) if it is not shared with us. */
    fun folderName(folderId: String): String =
        callJson(Request.Builder().url("$api/files/$folderId?fields=name")).getString("name")

    fun list(folderId: String): List<DriveFile> {
        val out = mutableListOf<DriveFile>()
        var pageToken: String? = null
        val q = URLEncoder.encode("'$folderId' in parents and trashed = false", "UTF-8")
        do {
            val url = buildString {
                append("$api/files?q=$q&pageSize=1000&fields=nextPageToken,files(id,name)&orderBy=createdTime")
                if (pageToken != null) append("&pageToken=").append(pageToken)
            }
            val o = callJson(Request.Builder().url(url))
            val files = o.getJSONArray("files")
            for (i in 0 until files.length()) {
                val f = files.getJSONObject(i)
                out += DriveFile(f.getString("id"), f.getString("name"))
            }
            pageToken = o.optString("nextPageToken").takeIf { it.isNotEmpty() }
        } while (pageToken != null)
        return out
    }

    fun upload(folderId: String, name: String, bytes: ByteArray): String {
        val meta = JSONObject().put("name", name).put("parents", org.json.JSONArray().put(folderId))
        val body = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(meta.toString().toRequestBody(json))
            .addPart(bytes.toRequestBody("application/octet-stream".toMediaType()))
            .build()
        return callJson(
            Request.Builder().url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id").post(body)
        ).getString("id")
    }

    fun download(fileId: String): ByteArray =
        call(Request.Builder().url("$api/files/$fileId?alt=media")).use { it.body!!.bytes() }
}

class DriveException(val code: Int, detail: String) : IOException("Drive error $code: $detail")
