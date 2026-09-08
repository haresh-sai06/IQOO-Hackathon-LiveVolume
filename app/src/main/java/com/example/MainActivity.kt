package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.data.repository.CallSignalingRepository
import com.example.model.CallSession
import com.example.service.LiveVolumeMessagingService
import com.example.ui.LiveVolumeApp
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    handleCallIntent(intent)
    setContent {
      MyApplicationTheme {
        LiveVolumeApp()
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleCallIntent(intent)
  }

  private fun handleCallIntent(intent: Intent?) {
    if (intent == null) return
    val signalingRepo = CallSignalingRepository.getInstance(this)
    when (intent.action) {
      LiveVolumeMessagingService.ACTION_ANSWER_CALL -> {
        val callId = intent.getStringExtra(LiveVolumeMessagingService.EXTRA_CALL_ID) ?: ""
        val callerName = intent.getStringExtra(LiveVolumeMessagingService.EXTRA_CALLER_NAME) ?: "Live Contact"
        val channelName = intent.getStringExtra(LiveVolumeMessagingService.EXTRA_CHANNEL_NAME) ?: ""
        signalingRepo.acceptCall(
          CallSession(
            callId = callId,
            callerName = callerName,
            channelName = channelName
          )
        )
      }
      LiveVolumeMessagingService.ACTION_DECLINE_CALL -> {
        val callId = intent.getStringExtra(LiveVolumeMessagingService.EXTRA_CALL_ID) ?: ""
        signalingRepo.declineCall(
          CallSession(callId = callId)
        )
      }
    }
  }
}

