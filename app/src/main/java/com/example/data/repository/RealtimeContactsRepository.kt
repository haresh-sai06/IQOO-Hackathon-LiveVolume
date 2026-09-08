package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.model.Contact
import com.example.model.DataRepository
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

/**
 * Real-time Contacts Repository backed by Cloud Firestore with live snapshot listeners
 * and an offline-first reactive cache.
 */
class RealtimeContactsRepository private constructor(private val context: Context) {

  private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
  val contactsFlow: StateFlow<List<Contact>> = _contacts.asStateFlow()

  private val scope = CoroutineScope(Dispatchers.IO)
  private var firestore: FirebaseFirestore? = null
  private var firestoreListener: ListenerRegistration? = null

  init {
    // Initialize with default contacts first
    _contacts.value = DataRepository.allContacts

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
          }
        } else if (snapshot != null && snapshot.isEmpty) {
          // If Firestore contacts collection is empty, seed it with default contacts
          seedInitialContactsToFirestore(db)
        }
      }
  }

  private fun seedInitialContactsToFirestore(db: FirebaseFirestore) {
    scope.launch {
      try {
        for (contact in DataRepository.allContacts) {
          val data = hashMapOf(
            "id" to contact.id,
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
          db.collection("contacts").document(contact.id).set(data)
        }
      } catch (e: Exception) {
        Log.w(TAG, "Failed seeding contacts to Firestore: ${e.message}")
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
      name = name,
      initials = initials,
      phone = phone,
      status = if (isSpatialReady) "3D Live Ready" else "Mobile",
      avatarUrl = null,
      isSpatialReady = isSpatialReady,
      isOnline = true,
      isFavorite = false,
      section = section
    )

    // Immediate reactive update
    _contacts.update { current ->
      (current + newContact).sortedBy { it.name }
    }

    // Sync to Firestore
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

  fun toggleFavorite(contactId: String) {
    var updatedContact: Contact? = null
    _contacts.update { list ->
      list.map { contact ->
        if (contact.id == contactId) {
          val newFav = !contact.isFavorite
          contact.copy(isFavorite = newFav).also { updatedContact = it }
        } else {
          contact
        }
      }
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
      list.map { contact ->
        if (contact.id == contactId) contact.copy(isOnline = isOnline) else contact
      }
    }
  }

  companion object {
    private const val TAG = "RealtimeContactsRepo"

    @Volatile
    private var INSTANCE: RealtimeContactsRepository? = null

    fun getInstance(context: Context): RealtimeContactsRepository {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: RealtimeContactsRepository(context.applicationContext).also { INSTANCE = it }
      }
    }
  }
}
