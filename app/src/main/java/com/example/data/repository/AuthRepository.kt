package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.model.UserProfile
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Authentication repository backed by Firebase Auth + Cloud Firestore.
 *
 * There are NO mock or offline-fallback identities here. If the backend is not
 * configured (missing `google-services.json`) or the credentials are wrong, the
 * call fails and the error is surfaced to the UI. A persisted session is only
 * ever written after a real Firebase sign-in succeeds.
 */
class AuthRepository private constructor(private val context: Context) {

  private val prefs: SharedPreferences =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val _currentUser = MutableStateFlow<UserProfile?>(null)
  val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

  private val scope = CoroutineScope(Dispatchers.IO)
  private var firebaseAuth: FirebaseAuth? = null

  /** True when `google-services.json` is present and Firebase actually initialized. */
  val isBackendConfigured: Boolean
    get() = try {
      FirebaseApp.getApps(context).isNotEmpty()
    } catch (_: Exception) {
      false
    }

  init {
    try {
      if (isBackendConfigured) {
        firebaseAuth = FirebaseAuth.getInstance()
        firebaseAuth?.currentUser?.let { user ->
          val profile = profileFromFirebaseUser(
            id = user.uid,
            fallbackName = user.displayName,
            email = user.email.orEmpty(),
            phone = user.phoneNumber.orEmpty()
          )
          _currentUser.value = profile
          saveUserToPrefs(profile)
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "Firebase Auth not initialized: ${e.message}")
    }

    // Restore a previously persisted real session when Firebase has not re-emitted
    // yet (e.g. cold start while the device is offline).
    if (_currentUser.value == null) {
      restorePersistedSession()
    }
  }

  private fun restorePersistedSession() {
    val savedId = prefs.getString(KEY_USER_ID, null) ?: return
    val savedName = prefs.getString(KEY_NAME, null) ?: return
    _currentUser.value = UserProfile(
      id = savedId,
      name = savedName,
      email = prefs.getString(KEY_EMAIL, "").orEmpty(),
      phone = prefs.getString(KEY_PHONE, "").orEmpty(),
      status = prefs.getString(KEY_STATUS, DEFAULT_STATUS) ?: DEFAULT_STATUS,
      avatarUrl = prefs.getString(KEY_AVATAR, null),
      isSpatialReady = true,
      isOnline = true
    )
    // Re-register the device token on cold start so push fan-out keeps working
    // after a token rotation or a re-install.
    syncFcmToken(savedId)
  }

  suspend fun signIn(email: String, password: String): Result<UserProfile> {
    val cleanEmail = email.trim()
    val cleanPassword = password.trim()
    if (cleanEmail.isBlank() || cleanPassword.isBlank()) {
      return Result.failure(IllegalArgumentException("Please enter your email and password."))
    }
    val auth = firebaseAuth ?: return Result.failure(backendUnavailableError())
    return try {
      val authResult = auth.signInWithEmailAndPassword(cleanEmail, cleanPassword).await()
      val user = authResult.user
        ?: return Result.failure(IllegalStateException("Sign-in succeeded but no account was returned."))
      val profile = profileFromFirebaseUser(
        id = user.uid,
        fallbackName = user.displayName,
        email = user.email ?: cleanEmail,
        phone = user.phoneNumber.orEmpty()
      )
      persistSession(profile)
      Result.success(profile)
    } catch (e: Exception) {
      Log.e(TAG, "Sign-in failed", e)
      Result.failure(friendlyAuthError(e, isRegister = false))
    }
  }

  suspend fun register(name: String, email: String, password: String): Result<UserProfile> {
    val cleanName = name.trim()
    val cleanEmail = email.trim()
    val cleanPassword = password.trim()
    if (cleanName.isBlank() || cleanEmail.isBlank() || cleanPassword.isBlank()) {
      return Result.failure(IllegalArgumentException("Please fill out all registration fields."))
    }
    val auth = firebaseAuth ?: return Result.failure(backendUnavailableError())
    return try {
      val authResult = auth.createUserWithEmailAndPassword(cleanEmail, cleanPassword).await()
      val user = authResult.user
        ?: return Result.failure(IllegalStateException("Registration succeeded but no account was returned."))
      // Keep the Auth display name in sync with the Firestore directory entry.
      try {
        user.updateProfile(
          UserProfileChangeRequest.Builder().setDisplayName(cleanName).build()
        ).await()
      } catch (e: Exception) {
        Log.w(TAG, "Unable to set display name: ${e.message}")
      }
      val profile = profileFromFirebaseUser(
        id = user.uid,
        fallbackName = cleanName,
        email = user.email ?: cleanEmail,
        phone = user.phoneNumber.orEmpty()
      )
      persistSession(profile)
      Result.success(profile)
    } catch (e: Exception) {
      Log.e(TAG, "Registration failed", e)
      Result.failure(friendlyAuthError(e, isRegister = true))
    }
  }

  fun updateProfile(updated: UserProfile) {
    _currentUser.value = updated
    saveUserToPrefs(updated)
  }

  fun signOut() {
    try {
      firebaseAuth?.signOut()
    } catch (e: Exception) {
      Log.w(TAG, "Sign out error", e)
    }
    prefs.edit().clear().apply()
    _currentUser.value = null
  }

  /** Persists the session locally, publishes the directory profile and registers FCM. */
  private fun persistSession(profile: UserProfile) {
    _currentUser.value = profile
    saveUserToPrefs(profile)
    publishDirectoryProfile(profile)
    syncFcmToken(profile.id)
  }

  private fun saveUserToPrefs(profile: UserProfile) {
    prefs.edit()
      .putString(KEY_USER_ID, profile.id)
      .putString(KEY_NAME, profile.name)
      .putString(KEY_EMAIL, profile.email)
      .putString(KEY_PHONE, profile.phone)
      .putString(KEY_STATUS, profile.status)
      .putString(KEY_AVATAR, profile.avatarUrl)
      .apply()
  }

  /**
   * Makes registered users discoverable for calls. The Firebase UID is the
   * routing identity; display names are never used as identifiers.
   */
  private fun publishDirectoryProfile(profile: UserProfile) {
    if (!isBackendConfigured || profile.id.isBlank()) return
    scope.launch {
      try {
        FirebaseFirestore.getInstance().collection("directory").document(profile.id).set(
          hashMapOf(
            "id" to profile.id,
            "registeredUserId" to profile.id,
            "name" to profile.name,
            "initials" to initialsOf(profile.name),
            "phone" to profile.phone,
            "status" to profile.status,
            "avatarUrl" to profile.avatarUrl,
            "isSpatialReady" to profile.isSpatialReady,
            "isOnline" to profile.isOnline,
            "isFavorite" to false,
            "section" to (profile.name.firstOrNull()?.uppercase() ?: "#")
          )
        )
      } catch (e: Exception) {
        Log.w(TAG, "Unable to publish user directory profile: ${e.message}")
      }
    }
  }

  /**
   * Registers this device's FCM token under `users/{uid}` so the signaling
   * backend can fan out incoming-call pushes. The token is only known once the
   * app has authenticated, so this must run on every auth, not just on refresh.
   */
  private fun syncFcmToken(uid: String) {
    if (!isBackendConfigured || uid.isBlank()) return
    try {
      FirebaseMessaging.getInstance().token
        .addOnSuccessListener { token ->
          if (!token.isNullOrBlank()) {
            CallSignalingRepository.getInstance(context).syncFcmToken(uid, token)
          }
        }
        .addOnFailureListener { e ->
          Log.w(TAG, "FCM token lookup failed: ${e.message}")
        }
    } catch (e: Exception) {
      Log.w(TAG, "FCM token registration skipped: ${e.message}")
    }
  }

  private fun profileFromFirebaseUser(
    id: String,
    fallbackName: String?,
    email: String,
    phone: String
  ): UserProfile = UserProfile(
    id = id,
    name = fallbackName?.takeIf { it.isNotBlank() }
      ?: email.substringBefore("@").replaceFirstChar { it.uppercase() }.ifBlank { "User" },
    email = email,
    phone = phone,
    status = DEFAULT_STATUS,
    avatarUrl = null,
    isSpatialReady = true,
    isOnline = true
  )

  private fun initialsOf(name: String): String = name.trim()
    .split(" ")
    .filter { it.isNotBlank() }
    .take(2)
    .joinToString("") { it.first().uppercase() }
    .ifBlank { "U" }

  private fun backendUnavailableError(): Exception = IllegalStateException(
    "LiveVolume is not connected to its backend. Add app/google-services.json and rebuild."
  )

  private fun friendlyAuthError(e: Exception, isRegister: Boolean): Exception = when (e) {
    is FirebaseAuthInvalidCredentialsException ->
      IllegalArgumentException("Incorrect email or password. Please try again.")
    is FirebaseAuthInvalidUserException ->
      IllegalArgumentException("No LiveVolume account exists for that email address.")
    is FirebaseAuthUserCollisionException ->
      IllegalArgumentException("An account already exists for that email address.")
    is FirebaseAuthWeakPasswordException ->
      IllegalArgumentException("Please choose a password with at least 6 characters.")
    else -> IllegalStateException(
      e.localizedMessage ?: if (isRegister) {
        "Could not create your account. Check your connection and try again."
      } else {
        "Sign-in failed. Check your connection and try again."
      }
    )
  }

  companion object {
    private const val TAG = "AuthRepository"
    private const val DEFAULT_STATUS = "3D Live Ready"
    private const val PREFS_NAME = "livevolume_real_auth_prefs"
    private const val KEY_USER_ID = "auth_user_id"
    private const val KEY_NAME = "auth_user_name"
    private const val KEY_EMAIL = "auth_user_email"
    private const val KEY_PHONE = "auth_user_phone"
    private const val KEY_STATUS = "auth_user_status"
    private const val KEY_AVATAR = "auth_user_avatar"

    @Volatile
    private var INSTANCE: AuthRepository? = null

    fun getInstance(context: Context): AuthRepository {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: AuthRepository(context.applicationContext).also { INSTANCE = it }
      }
    }
  }
}
