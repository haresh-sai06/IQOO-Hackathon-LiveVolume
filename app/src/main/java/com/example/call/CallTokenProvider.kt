package com.example.call

import android.content.Context
import android.util.Log
import com.example.BuildConfig

/**
 * Resolves the Agora RTC token + sanitized channel name.
 *
 * Production contract:
 * - Token is OPTIONAL in dev (null => testing mode, no certificate).
 * - In production, point `TOKEN_SERVER_URL` (gradle prop / env) at your token
 *   issuer and fetch `{ "token": "<...>" }` via HTTPS. Never hard-code tokens.
 * - Channel names are restricted to [a-z0-9_-] and <= 64 chars per Agora spec.
 */
object CallTokenProvider {

  private const val TAG = "CallTokenProvider"
  private const val PREFS = "livevolume_call_prefs"
  private const val KEY_TOKEN_URL = "agora_token_server_url"
  private const val MAX_CHANNEL_LEN = 64

  /** Sanitizes any user/room string into a valid Agora channel name. */
  fun sanitizeChannel(raw: String?, fallback: String = "livevolume_room"): String {
    if (raw.isNullOrBlank()) return fallback
    val clean = raw.trim().lowercase()
      .replace(Regex("[^a-z0-9_\\-]"), "_")
      .trim('_')
      .take(MAX_CHANNEL_LEN)
    return clean.ifBlank { fallback }
  }

  fun sanitizeUid(raw: Int, fallback: Int = 0): Int = if (raw < 0) fallback else raw

  fun getTokenServerUrl(context: Context): String {
    // 1. persisted override from Settings
    try {
      context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_TOKEN_URL, null)?.takeIf { it.isNotBlank() }?.let { return it.trim() }
    } catch (_: Exception) {}
    // 2. build config / env (empty = testing mode)
    return try {
      BuildConfig.AGORA_TOKEN_SERVER_URL.trim()
    } catch (_: Exception) {
      ""
    }
  }

  fun saveTokenServerUrl(context: Context, url: String) {
    try {
      context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putString(KEY_TOKEN_URL, url.trim()).apply()
    } catch (e: Exception) {
      Log.w(TAG, "persist token url failed: ${e.message}")
    }
  }

  /**
   * Resolves token for [channel]/[uid]. Returns null in testing mode.
   * Keep this synchronous-fast: cached token if fresh, else network fetch with
   * short timeout. Callers must handle null (join with null token).
   */
  suspend fun fetchToken(
    context: Context,
    channel: String,
    uid: Int
  ): String? {
    val base = getTokenServerUrl(context)
    if (base.isBlank() || base == "YOUR_TOKEN_SERVER_URL") return null
    return try {
      val url = java.net.URL("$base?channel=${java.net.URLEncoder.encode(channel, "UTF-8")}&uid=$uid")
      val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
        connectTimeout = 4_000
        readTimeout = 4_000
        requestMethod = "GET"
      }
      if (conn.responseCode != 200) {
        Log.w(TAG, "token server http=${conn.responseCode}")
        return null
      }
      val body = conn.inputStream.bufferedReader().readText()
      // Minimal parse without extra deps: look for "token":"..."
      val match = Regex("\"token\"\\s*:\\s*\"([^\"]+)\"").find(body)
      match?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
      Log.w(TAG, "token fetch failed: ${e.message}")
      null
    }
  }
}
