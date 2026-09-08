package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.model.Contact
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Real-time Contacts Repository backed by Cloud Firestore and persistent local cache.
 * All mock data has been completely eliminated.
 */
class RealtimeContactsRepository private constructor(private val context: Context) {

  private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
  val contactsFlow: StateFlow<List<Contact>> = _contacts.asStateFlow()

  private val scope = CoroutineScope(Dispatchers.IO)
  private var firestore: FirebaseFirestore? = null
  private var firestoreListener: ListenerRegistration? = null

  init {
    // 1. Load real contacts saved in local storage first
    val localSaved = loadContactsFromPrefs()
    _contacts.value = localSaved

    // 2. Connect to Cloud Firestore real-time collection
    try {
      if (FirebaseApp.getApps(context).isNotEmpty()) {
        firestore = FirebaseFirestore.getInstance()
        attachFirestoreRealtimeListener()
      }
    } catch (e: Exception) {
      Log.w(TAG, "Firestore not initialized or offline: ${e.message}")
    }
  }

  private fun attachFirestoreRealtimeListener() {
    val db = firestore ?: return
    firestoreListener?.remove()

    firestoreListener = db.collection("contacts")
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          Log.w(TAG, "Firestore contacts listen error: ${error.message}")
          return@addSnapshotListener
        }

        if (snapshot != null && !snapshot.isEmpty) {
          val remoteContacts = snapshot.documents.mapNotNull { doc ->
            try {
              Contact(
                id = doc.getString("id") ?: doc.id,
                name = doc.getString("name") ?: "Unknown",
                initials = doc.getString("initials") ?: "U",
                phone = doc.getString("phone") ?: "",
                status = doc.getString("status") ?: "Available",
                avatarUrl = doc.getString("avatarUrl"),
                isSpatialReady = doc.getBoolean("isSpatialReady") ?: false,
                isOnline = doc.getBoolean("isOnline") ?: false,
                isFavorite = doc.getBoolean("isFavorite") ?: false,
                section = doc.getString("section") ?: "A"
              )
            } catch (e: Exception) {
              null
            }
          }

          if (remoteContacts.isNotEmpty()) {
            _contacts.value = remoteContacts.sortedBy { it.name }
            saveContactsToPrefs(remoteContacts)
          }
        }
      }
  }

  fun addContact(name: String, phone: String, isSpatialReady: Boolean = true) {
    val initials = name.trim().split(" ")
      .filter { it.isNotEmpty() }
      .take(2)
      .map { it.first().uppercase() }
      .joinToString("")
      .ifEmpty { "U" }

    val section = name.trim().firstOrNull()?.uppercase() ?: "A"
    val newId = "contact_${System.currentTimeMillis()}"

    val newContact = Contact(
      id = newId,
      name = name.trim(),
      initials = initials,
      phone = phone.trim(),
      status = if (isSpatialReady) "3D Live Ready" else "Mobile",
      avatarUrl = null,
      isSpatialReady = isSpatialReady,
      isOnline = true,
      isFavorite = false,
      section = section
    )

    // Immediate reactive update
    _contacts.update { current ->
      val updated = (current + newContact).sortedBy { it.name }
      saveContactsToPrefs(updated)
      updated
    }

    // Sync to Firestore in real time
    firestore?.let { db ->
      scope.launch {
        try {
          val data = hashMapOf(
            "id" to newContact.id,
            "name" to newContact.name,
            "initials" to newContact.initials,
            "phone" to newContact.phone,
            "status" to newContact.status,
            "avatarUrl" to newContact.avatarUrl,
            "isSpatialReady" to newContact.isSpatialReady,
            "isOnline" to newContact.isOnline,
            "isFavorite" to newContact.isFavorite,
            "section" to newContact.section
          )
          db.collection("contacts").document(newContact.id).set(data)
        } catch (e: Exception) {
          Log.w(TAG, "Failed to save contact to Firestore: ${e.message}")
        }
      }
    }
  }

  fun importPhoneContacts(newContacts: List<Contact>): Int {
    if (newContacts.isEmpty()) return 0
    var addedCount = 0
    _contacts.update { current ->
      val existingPhones = current.map { it.phone.filter { ch -> ch.isDigit() } }.filter { it.isNotEmpty() }.toSet()
      val existingNames = current.map { it.name.lowercase().trim() }.toSet()

      val toAdd = mutableListOf<Contact>()
      for (c in newContacts) {
        val digits = c.phone.filter { it.isDigit() }
        if (digits.isNotEmpty() && existingPhones.contains(digits)) continue
        if (existingNames.contains(c.name.lowercase().trim())) continue
        toAdd.add(c)
      }
      addedCount = toAdd.size
      val merged = (current + toAdd).sortedBy { it.name }
      saveContactsToPrefs(merged)

      // Sync to Firestore in background
      firestore?.let { db ->
        scope.launch {
          for (c in toAdd) {
            try {
              val data = hashMapOf(
                "id" to c.id,
                "name" to c.name,
                "initials" to c.initials,
                "phone" to c.phone,
                "status" to c.status,
                "avatarUrl" to c.avatarUrl,
                "isSpatialReady" to c.isSpatialReady,
                "isOnline" to c.isOnline,
                "isFavorite" to c.isFavorite,
                "section" to c.section
              )
              db.collection("contacts").document(c.id).set(data)
            } catch (e: Exception) {
              Log.w(TAG, "Failed to sync imported contact: ${e.message}")
            }
          }
        }
      }
      merged
    }
    return addedCount
  }

  fun toggleFavorite(contactId: String) {
    var updatedContact: Contact? = null
    _contacts.update { list ->
      val updated = list.map { contact ->
        if (contact.id == contactId) {
          val newFav = !contact.isFavorite
          contact.copy(isFavorite = newFav).also { updatedContact = it }
        } else {
          contact
        }
      }
      saveContactsToPrefs(updated)
      updated
    }

    updatedContact?.let { contact ->
      firestore?.let { db ->
        scope.launch {
          try {
            db.collection("contacts").document(contact.id)
              .update("isFavorite", contact.isFavorite)
          } catch (e: Exception) {
            Log.w(TAG, "Failed to toggle favorite in Firestore: ${e.message}")
          }
        }
      }
    }
  }

  fun updateContactPresence(contactId: String, isOnline: Boolean) {
    _contacts.update { list ->
      val updated = list.map { contact ->
        if (contact.id == contactId) contact.copy(isOnline = isOnline) else contact
      }
      saveContactsToPrefs(updated)
      updated
    }
  }

  private fun saveContactsToPrefs(contacts: List<Contact>) {
    try {
      val array = JSONArray()
      for (c in contacts) {
        val obj = JSONObject()
        obj.put("id", c.id)
        obj.put("name", c.name)
        obj.put("initials", c.initials)
        obj.put("phone", c.phone)
        obj.put("status", c.status)
        obj.put("avatarUrl", c.avatarUrl)
        obj.put("isSpatialReady", c.isSpatialReady)
        obj.put("isOnline", c.isOnline)
        obj.put("isFavorite", c.isFavorite)
        obj.put("section", c.section)
        array.put(obj)
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
      val list = mutableListOf<Contact>()
      for (i in 0 until array.length()) {
        val obj = array.getJSONObject(i)
        list.add(
          Contact(
            id = obj.optString("id", "contact_$i"),
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
        )
      }
      list.sortedBy { it.name }
    } catch (e: Exception) {
      emptyList()
    }
  }

  companion object {
    private const val TAG = "RealtimeContactsRepo"
    private const val PREFS_NAME = "livevolume_real_contacts_prefs"
    private const val KEY_CONTACTS_JSON = "saved_contacts_json"

    @Volatile
    private var INSTANCE: RealtimeContactsRepository? = null

    fun getInstance(context: Context): RealtimeContactsRepository {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: RealtimeContactsRepository(context.applicationContext).also { INSTANCE = it }
      }
    }
  }
}
