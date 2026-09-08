package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.DataRepository
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("LiveVolume", appName)
  }

  @Test
  fun `verify contacts and repository data`() {
    assertTrue(DataRepository.allContacts.isNotEmpty())
    assertTrue(DataRepository.callRecords.isNotEmpty())
    assertEquals("Sarah Chen", DataRepository.myProfile.name)
  }

  @Test
  fun `verify CallViewModel state management`() {
    val viewModel = com.example.viewmodel.CallViewModel()
    val initialState = viewModel.uiState.value

    assertEquals(false, initialState.isMuted)
    assertEquals(true, initialState.isCameraOn)
    assertEquals(com.example.viewmodel.ConnectionStatus.CONNECTED, initialState.connectionStatus)

    viewModel.toggleMute()
    assertEquals(true, viewModel.uiState.value.isMuted)

    viewModel.toggleCamera()
    assertEquals(false, viewModel.uiState.value.isCameraOn)

    viewModel.switchCamera()
    assertEquals(false, viewModel.uiState.value.isFrontCamera)

    viewModel.setConnectionStatus(com.example.viewmodel.ConnectionStatus.RECONNECTING)
    assertEquals(com.example.viewmodel.ConnectionStatus.RECONNECTING, viewModel.uiState.value.connectionStatus)

    viewModel.endCall()
    assertEquals(com.example.viewmodel.ConnectionStatus.DISCONNECTED, viewModel.uiState.value.connectionStatus)
  }

  @Test
  fun `verify AudioPermissionHelper constants and checks`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals("android.permission.RECORD_AUDIO", com.example.util.AudioPermissionHelper.RECORD_AUDIO_PERMISSION)
    // Runs cleanly without throwing exceptions
    val status = com.example.util.AudioPermissionHelper.verifyBinauralAudioCapability(context)
    assertTrue(status != null)
  }

  @Test
  fun `verify AudioProcessingService HRTF filter binaural processing`() {
    val service = com.example.service.AudioProcessingService()
    val testMonoPcm = ShortArray(128) { (it * 100).toShort() }

    // Test straight ahead (azimuth = 0)
    val stereoAhead = service.applyBinauralSpatializationHrtf(
      inputPcm = testMonoPcm,
      sampleCount = testMonoPcm.size,
      azimuthDegrees = 0f,
      elevationDegrees = 0f
    )
    assertEquals(testMonoPcm.size * 2, stereoAhead.size)

    // Test hard right (azimuth = +90 degrees)
    val stereoRight = service.applyBinauralSpatializationHrtf(
      inputPcm = testMonoPcm,
      sampleCount = testMonoPcm.size,
      azimuthDegrees = 90f,
      elevationDegrees = 0f
    )
    assertEquals(testMonoPcm.size * 2, stereoRight.size)
  }

  @Test
  fun `verify connection strength latency and color transitions`() {
    val viewModel = com.example.viewmodel.CallViewModel()

    // Low latency -> Green (Excellent)
    viewModel.updateLatency(20)
    assertEquals(com.example.viewmodel.ConnectionQualityLevel.EXCELLENT, viewModel.uiState.value.connectionQuality)
    assertEquals(com.example.ui.theme.LiveSuccess, viewModel.uiState.value.connectionQuality.color)

    // Moderate latency -> Amber (Moderate)
    viewModel.updateLatency(100)
    assertEquals(com.example.viewmodel.ConnectionQualityLevel.MODERATE, viewModel.uiState.value.connectionQuality)

    // High latency -> Red (Poor)
    viewModel.updateLatency(220)
    assertEquals(com.example.viewmodel.ConnectionQualityLevel.POOR, viewModel.uiState.value.connectionQuality)
    assertEquals(com.example.ui.theme.LiveError, viewModel.uiState.value.connectionQuality.color)
  }

  @Test
  fun `verify History NavigationTab exists`() {
    val tabs = com.example.ui.components.NavigationTab.entries
    assertTrue(tabs.any { it.name == "HISTORY" && it.label == "History" })
  }

  @Test
  fun `verify Guides is removed from bottom navbar and moved to settings destination`() {
    val tabs = com.example.ui.components.NavigationTab.entries
    assertEquals(4, tabs.size)
    assertTrue(tabs.none { it.name == "GUIDES" })
    assertTrue(tabs.any { it.name == "RECENTS" })
    assertTrue(tabs.any { it.name == "CONTACTS" })
    assertTrue(tabs.any { it.name == "HISTORY" })
    assertTrue(tabs.any { it.name == "SETTINGS" })

    // Verify Screen.Guides exists as standalone destination
    assertEquals("guides", com.example.ui.Screen.Guides.route)
  }

  @Test
  fun `verify CallViewModel coroutine timer and duration formatted`() {
    val viewModel = com.example.viewmodel.CallViewModel()
    viewModel.initializeCall("Sarah Chen")
    assertEquals("00:00", viewModel.elapsedDurationFormatted.value)
    assertEquals(0L, viewModel.elapsedSeconds.value)
    viewModel.endCall()
  }

  @Test
  fun `verify Room CallHistoryDao insertion and search by name`() {
    kotlinx.coroutines.runBlocking {
      val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
      val db = androidx.room.Room.inMemoryDatabaseBuilder(
        context,
        com.example.data.local.AppDatabase::class.java
      ).allowMainThreadQueries().build()

      val dao = db.callHistoryDao()
      val testEntity = com.example.data.local.CallHistoryEntity(
        contactName = "Alex Rivera",
        phoneNumber = "+1 555-0199",
        avatarUrl = null,
        initials = "AR",
        callType = "3D Spatial",
        direction = "Outgoing",
        durationSeconds = 125,
        durationFormatted = "02:05",
        timestamp = System.currentTimeMillis(),
        timestampFormatted = "Just now",
        period = "TODAY",
        isOnline = true,
        isSpatial = true
      )

      val insertedId = dao.insertCall(testEntity)
      assertTrue(insertedId > 0)
      assertEquals(1, dao.getCount())

      val retrieved = dao.getCallById(insertedId)
      assertTrue(retrieved != null)
      assertEquals("Alex Rivera", retrieved?.contactName)
      assertEquals("02:05", retrieved?.durationFormatted)

      // Test search filter by name
      val searchResults = dao.searchCallHistoryDirect("Alex")
      assertEquals(1, searchResults.size)
      assertEquals("Alex Rivera", searchResults[0].contactName)

      val noResults = dao.searchCallHistoryDirect("Nonexistent")
      assertEquals(0, noResults.size)

      db.close()
    }
  }
}

