package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.model.Contact
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Contacts repository backed by Cloud Firestore.
 *
 * Data model (deliberately two collections):
 *
 *  - `directory/{uid}`      — a LiveVolume user's *discoverable* profile.
 *                             Readable by every authenticated user so callers
 *                             can find each other by name / UID.
 *  - `users/{uid}/contacts` — that user's *private* address book (manually
 *                             added entries and imports from the device).
 *
 * Previously every user's address book was written into one global `contacts`
 * collection and then read back wholesale, which leaked one user's imported
 * phone numbers to every other user. The two sources are now merged only on
 * this device, and a user can only read their own address book.
 */
class RealtimeContactsRepository private constructor(private val context: Context) {

  private val prefs: SharedPreferences =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
  val contactsFlow: StateFlow<List<Contact>> = _contacts.asStateFlow()

  private val scope = CoroutineScope(Dispatchers.IO)
  private var firestore: FirebaseFirestore? = null

  private var privateListener: ListenerRegistration? = null
  private var directoryListener: ListenerRegistration? = null

  private var currentUid: String = ""

  /** This user's own address book, mirrored in prefs for offline use. */
  private var privateContacts: List<Contact> = emptyList()

  /** Other LiveVolume users discoverable for calls. */
  private var directoryContacts: List<Contact> = emptyList()

  private val favoriteIds: MutableSet<String> = loadFavoritesFromPrefs().toMutableSet()

  init {
    privateContacts = loadContactsFromPrefs()
    publish()
    try {
      if (FirebaseApp.getApps(context).isNotEmpty()) {
        firestore = FirebaseFirestore.getInstance()
      }
    } catch (e: Exception) {
      Log.w(TAG, "Firestore not initialized or offline: ${e.message}")
    }
  }

  /**
   * Binds the repository to the signed-in account and starts the realtime
   * listeners. Safe to call again after a different user logs in.
   */
  fun start(uid: String) {
    if (uid.isBlank() || uid == currentUid) return
    currentUid = uid
    attachListeners()
  }

  private fun attachListeners() {
    val db = firestore ?: return
    val uid = currentUid
    if (uid.isBlank()) return

    privateListener?.remove()
    privateListener = db.collection("users").document(uid).collection("contacts")
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          Log.w(TAG, "Private contacts listen error: ${error.message}")
          return@addSnapshotListener
        }
        if (snapshot == null) return@addSnapshotListener

        privateContacts = snapshot.documents.mapNotNull { it.toContact() }.sortedBy { it.name }
        saveContactsToPrefs(privateContacts)
        publish()
      }

    directoryListener?.remove()
    directoryListener = db.collection("directory")
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          Log.w(TAG, "Directory listen error: ${error.message}")
          return@addSnapshotListener
        }
        if (snapshot == null) return@addSnapshotListener

        directoryContacts = snapshot.documents
          .filter { it.id != uid }
          .mapNotNull { it.toContact() }
        publish()
      }
  }

  private fun DocumentSnapshot.toContact(): Contact? = try {
    val name = getString("name")
    if (name.isNullOrBlank()) {
      null
    } else {
      Contact(
        id = getString("id") ?: id,
        registeredUserId = getString("registeredUserId") ?: getString("userId"),
        name = name,
        initials = getString("initials") ?: "U",
        phone = getString("phone").orEmpty(),
        status = getString("status") ?: "Available",
        avatarUrl = getString("avatarUrl"),
        isSpatialReady = getBoolean("isSpatialReady") ?: false,
        isOnline = getBoolean("isOnline") ?: false,
        isFavorite = false,
        section = getString("section") ?: (name.firstOrNull()?.uppercase() ?: "A")
      )
    }
  } catch (e: Exception) {
    null
  }

  /** Merges the private address book with the directory, then applies favorites. */
  private fun publish() {
    val seenKeys = mutableSetOf<String>()
    val merged = mutableListOf<Contact>()

    fun identityOf(contact: Contact): String =
      contact.registeredUserId?.lowercase()
        ?: contact.phone.filter { it.isDigit() }.ifEmpty { contact.name.lowercase() }

    for (contact in privateContacts + directoryContacts) {
      if (!seenKeys.add(identityOf(contact))) continue
      merged.add(contact.copy(isFavorite = contact.id in favoriteIds))
    }

    _contacts.value = merged.sortedBy { it.name.lowercase() }
  }

  fun addContact(name: String, phone: String, isSpatialReady: Boolean = true) {
    val cleanName = name.trim()
    if (cleanName.isBlank()) return

    val initials = cleanName.split(" ")
      .filter { it.isNotEmpty() }
      .take(2)
      .map { it.first().uppercase() }
      .joinToString("")
      .ifEmpty { "U" }

    val newContact = Contact(
      id = "contact_${System.currentTimeMillis()}",
      name = cleanName,
      initials = initials,
      phone = phone.trim(),
      status = if (isSpatialReady) "3D Live Ready" else "Mobile",
      avatarUrl = null,
      isSpatialReady = isSpatialReady,
      isOnline = true,
      isFavorite = false,
      section = cleanName.firstOrNull()?.uppercase() ?: "A"
    )

    privateContacts = (privateContacts + newContact).sortedBy { it.name }
    saveContactsToPrefs(privateContacts)
    publish()
    writePrivateContact(newContact)
  }

  /** Imports device contacts; returns how many were newly added. */
  fun importPhoneContacts(newContacts: List<Contact>): Int {
    if (newContacts.isEmpty()) return 0

    val existingPhones = privateContacts
      .map { it.phone.filter { ch -> ch.isDigit() } }
      .filter { it.isNotEmpty() }
      .toSet()
    val existingNames = privateContacts.map { it.name.lowercase().trim() }.toSet()

    val toAdd = newContacts.filter { candidate ->
      val digits = candidate.phone.filter { it.isDigit() }
      val phoneIsNew = digits.isEmpty() || !existingPhones.contains(digits)
      phoneIsNew && !existingNames.contains(candidate.name.lowercase().trim())
    }

    if (toAdd.isEmpty()) return 0

    privateContacts = (privateContacts + toAdd).sortedBy { it.name }
    saveContactsToPrefs(privateContacts)
    publish()
    toAdd.forEach { writePrivateContact(it) }
    return toAdd.size
  }

  /** Returns a saved entry; callers must not invent a recipient. */
  fun findContactByName(name: String): Contact? = _contacts.value.firstOrNull {
    it.name.equals(name.trim(), ignoreCase = true)
  }

  fun toggleFavorite(contactId: String) {
    val nowFavorite = !favoriteIds.contains(contactId)
    if (nowFavorite) favoriteIds.add(contactId) else favoriteIds.remove(contactId)
    saveFavoritesToPrefs()

    privateContacts = privateContacts.map { contact ->
      if (contact.id == contactId) contact.copy(isFavorite = nowFavorite) else contact
    }
    saveContactsToPrefs(privateContacts)
    publish()

    // Only the user's own address-book entries are writable; directory entries
    // keep their favorite flag on this device only.
    if (privateContacts.none { it.id == contactId }) return
    val db = firestore ?: return
    val uid = currentUid
    if (uid.isBlank()) return
    scope.launch {
      try {
        db.collection("users").document(uid).collection("contacts")
          .document(contactId)
          .update("isFavorite", nowFavorite)
      } catch (e: Exception) {
        Log.w(TAG, "Failed to persist favorite: ${e.message}")
      }
    }
  }

  fun updateContactPresence(contactId: String, isOnline: Boolean) {
    privateContacts = privateContacts.map { contact ->
      if (contact.id == contactId) contact.copy(isOnline = isOnline) else contact
    }
    saveContactsToPrefs(privateContacts)
    publish()
  }

  private fun writePrivateContact(contact: Contact) {
    val db = firestore ?: return
    val uid = currentUid
    if (uid.isBlank()) return
    scope.launch {
      try {
        db.collection("users").document(uid).collection("contacts")
          .document(contact.id)
          .set(
            hashMapOf(
              "id" to contact.id,
              "registeredUserId" to contact.registeredUserId,
              "name" to contact.name,
              "initials" to contact.initials,
              "phone" to contact.phone,
              "status" to contact.status,
              "avatarUrl" to contact.avatarUrl,
              "isSpatialReady" to contact.isSpatialReady,
              "isOnline" to contact.isOnline,
              "isFavorite" to contact.isFavorite,
              "section" to contact.section
            )
          )
      } catch (e: Exception) {
        Log.w(TAG, "Failed to save contact: ${e.message}")
      }
    }
  }

  private fun saveContactsToPrefs(contacts: List<Contact>) {
    try {
      val array = JSONArray()
      for (c in contacts) {
        array.put(
          JSONObject().apply {
            put("id", c.id)
            put("registeredUserId", c.registeredUserId)
            put("name", c.name)
            put("initials", c.initials)
            put("phone", c.phone)
            put("status", c.status)
            put("avatarUrl", c.avatarUrl)
            put("isSpatialReady", c.isSpatialReady)
            put("isOnline", c.isOnline)
            put("isFavorite", c.isFavorite)
            put("section", c.section)
          }
        )
      }
      prefs.edit().putString(KEY_CONTACTS_JSON, array.toString()).apply()
    } catch (e: Exception) {
      Log.w(TAG, "Error saving contacts: ${e.message}")
    }
  }

  private fun loadContactsFromPrefs(): List<Contact> {
    val json = prefs.getString(KEY_CONTACTS_JSON, null) ?: return emptyList()
    return try {
      val array = JSONArray(json)
      (0 until array.length()).mapNotNull { i ->
        val obj = array.optJSONObject(i) ?: return@mapNotNull null
        Contact(
          id = obj.optString("id", "contact_$i"),
          registeredUserId = if (obj.isNull("registeredUserId")) null
            else obj.optString("registeredUserId"),
          name = obj.optString("name", "Contact"),
          initials = obj.optString("initials", "C"),
          phone = obj.optString("phone", ""),
          status = obj.optString("status", "Available"),
          avatarUrl = if (obj.isNull("avatarUrl")) null else obj.optString("avatarUrl"),
          isSpatialReady = obj.optBoolean("isSpatialReady", true),
          isOnline = obj.optBoolean("isOnline", false),
          isFavorite = obj.optBoolean("isFavorite", false),
          section = obj.optString("section", "A")
        )
      }.sortedBy { it.name }
    } catch (e: Exception) {
      emptyList()
    }
  }

  private fun loadFavoritesFromPrefs(): Set<String> =
    prefs.getStringSet(KEY_FAVORITES, null)?.toSet() ?: emptySet()

  private fun saveFavoritesToPrefs() {
    prefs.edit().putStringSet(KEY_FAVORITES, favoriteIds.toSet()).apply()
  }

  companion object {
    private const val TAG = "RealtimeContactsRepo"
    private const val PREFS_NAME = "livevolume_real_contacts_prefs"
    private const val KEY_CONTACTS_JSON = "saved_contacts_json"
    private const val KEY_FAVORITES = "favorite_contact_ids"

    @Volatile
    private var INSTANCE: RealtimeContactsRepository? = null

    fun getInstance(context: Context): RealtimeContactsRepository {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: RealtimeContactsRepository(context.applicationContext).also { INSTANCE = it }
      }
    }
  }
}
