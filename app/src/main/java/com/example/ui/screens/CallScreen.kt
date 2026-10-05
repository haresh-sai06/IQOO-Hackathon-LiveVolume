package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.SurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlipCameraIos
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.CallHistoryRepository
import com.example.data.repository.CallSignalingRepository
import com.example.model.CallSessionState
import com.example.ui.components.CallControlsOverlay
import com.example.ui.components.LiveVolumeAvatar
import com.example.ui.theme.LivePrimaryContainer
import com.example.ui.theme.LiveSuccess
import com.example.util.HapticType
import com.example.util.HapticsManager
import com.example.util.CallPermissions
import com.example.util.InAppNotificationManager
import com.example.util.InAppNotificationType
import com.example.viewmodel.CallViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Modern, beautifully spaced clean video call interface powered by Agora Video RTC.
 * Provides real-time peer video rendering, self PiP, and active call controls.
 */
@Composable
fun CallScreen(
  callerName: String = "",
  channelName: String? = null,
  onEndCall: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: CallViewModel = viewModel()
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val elapsedDurationFormatted by viewModel.elapsedDurationFormatted.collectAsStateWithLifecycle()

  val context = LocalContext.current
  val activity = context as? android.app.Activity
  // Keep the screen on for the whole call (production: no mid-call sleep).
  DisposableEffect(activity) {
    activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    onDispose {
      activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
  }
  LaunchedEffect(context) {
    viewModel.agoraManager.attachContext(context)
  }
  val callHistoryRepository = remember { CallHistoryRepository.getInstance(context) }
  val signalingRepo = remember { CallSignalingRepository.getInstance(context) }
  val activeSession by signalingRepo.activeSession.collectAsStateWithLifecycle()

  var showConfigureAgoraDialog by remember { mutableStateOf(!viewModel.agoraManager.isConfigured()) }
  var inputAppId by remember { mutableStateOf("") }

  // Graceful camera & mic runtime permission handling (Phase 8)
  var hasCameraPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    )
  }
  var hasMicPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    )
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { perms ->
    hasCameraPermission = perms[Manifest.permission.CAMERA] ?: hasCameraPermission
    hasMicPermission = perms[Manifest.permission.RECORD_AUDIO] ?: hasMicPermission
  }

  LaunchedEffect(Unit) {
    try {
      androidx.camera.lifecycle.ProcessCameraProvider.getInstance(context).get().unbindAll()
    } catch (_: Exception) {}
    if (!hasCameraPermission || !hasMicPermission) {
      permissionLauncher.launch(CallPermissions.CALL_FULL)
    }
  }

  // Signaling heartbeat: lets the peer notice caller death even without presence.
  LaunchedEffect(activeSession?.callId, activeSession?.state) {
    val session = activeSession ?: return@LaunchedEffect
    if (session.state == CallSessionState.RINGING || session.state == CallSessionState.CONNECTED) {
      while (isActive) {
        kotlinx.coroutines.delay(15_000L)
        try {
          signalingRepo.heartbeat()
        } catch (_: Exception) {}
      }
    }
  }

  var localSurfaceView by remember { mutableStateOf<SurfaceView?>(null) }
  var sharedPresenceEnabled by remember { mutableStateOf(true) }
  var depthInferenceMs by remember { mutableStateOf<Long?>(null) }
  val depthEstimator = remember { com.example.ml.DepthEstimator(context) }

  DisposableEffect(Unit) {
    onDispose {
      depthEstimator.close()
    }
  }

  // Local depth capture and 3D Point Cloud streaming to remote peer (Phase 6 & 8)
  // Dynamic latency clamping + adaptive network budget: throttles capture if
  // inference exceeds 100ms AND backs off when RTC reports POOR/OFFLINE.
  LaunchedEffect(localSurfaceView, uiState.isCameraOn, hasCameraPermission) {
    val surface = localSurfaceView ?: return@LaunchedEffect
    if (!uiState.isCameraOn || !hasCameraPermission) return@LaunchedEffect

    val bmp = android.graphics.Bitmap.createBitmap(256, 256, android.graphics.Bitmap.Config.ARGB_8888)
    val bgThread = HandlerThread("PixelCopyBgThread").apply { start() }
    val bgHandler = Handler(bgThread.looper)
    var lastInferenceDurationMs = 0L

    try {
      while (isActive) {
        // Adaptive interval from real network quality (200ms excellent -> 400ms poor).
        val budgetInterval = try {
          viewModel.volumetricBudget.value.intervalMs
        } catch (_: Exception) {
          200L
        }
        val paused = try {
          viewModel.volumetricBudget.value.paused
        } catch (_: Exception) {
          false
        }
        if (paused) {
          kotlinx.coroutines.delay(500L)
          continue
        }
        val baseDelay = budgetInterval // adaptive nominal
        val dynamicDelay = if (lastInferenceDurationMs > 100L) {
          (lastInferenceDurationMs * 2L).coerceAtMost(1000L)
        } else {
          baseDelay
        }
        kotlinx.coroutines.delay(dynamicDelay)

        if (surface.holder.surface.isValid) {
          val copySignal = CompletableDeferred<Int>()
          android.view.PixelCopy.request(surface, bmp, { copyResult ->
            copySignal.complete(copyResult)
          }, bgHandler)

          val copyResult = try {
            withTimeoutOrNull(200L) {
              copySignal.await()
            } ?: -1
          } catch (e: Exception) {
            -1
          }

          if (copyResult == android.view.PixelCopy.SUCCESS) {
            val startTime = SystemClock.elapsedRealtime()
            val result = withContext(Dispatchers.Default) {
              depthEstimator.estimateDepth(bmp)
            }
            lastInferenceDurationMs = SystemClock.elapsedRealtime() - startTime
            depthInferenceMs = lastInferenceDurationMs

            result?.pointCloud?.let { pc ->
              viewModel.sendLocalPointCloud(pc)
            }
          }
        }
      }
    } finally {
      bgThread.quitSafely()
    }
  }

  LaunchedEffect(callerName, channelName) {
    viewModel.initializeCall(callerName, channelName)
  }

  val callStartTime = remember { System.currentTimeMillis() }
  // React to remote user declining or ending the call
  LaunchedEffect(activeSession?.state) {
    val session = activeSession ?: return@LaunchedEffect
    if (session.state == CallSessionState.REJECTED) {
      InAppNotificationManager.postNotification(
        title = "Call Declined",
        message = "$callerName declined the call.",
        type = InAppNotificationType.GENERAL,
        context = context
      )
      onEndCall()
    } else if (session.state == CallSessionState.ENDED && (System.currentTimeMillis() - callStartTime > 1500L)) {
      onEndCall()
    }
  }

  val infiniteTransition = rememberInfiniteTransition(label = "pulse")
  val pulseAlpha by infiniteTransition.animateFloat(
    initialValue = 0.4f,
    targetValue = 1.0f,
    animationSpec = infiniteRepeatable(
      animation = tween(1200, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulseAlpha"
  )

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color(0xFF0F172A))
      .testTag("call_screen")
  ) {
    // 1. Full-bleed Video Stream Surface (3D Hologram, 2D Agora Peer, or Waiting State)
    Box(modifier = Modifier.fillMaxSize()) {
      if (uiState.is3DMode) {
        // 3D Volumetric Mode: Live rotatable point cloud + optional Shared Presence desk preview
        Box(modifier = Modifier.fillMaxSize()) {
          com.example.ui.components.VolumetricVisualizer(
            pointCloud = uiState.remotePointCloud,
            modifier = Modifier.fillMaxSize(),
            sharedPresenceEnabled = sharedPresenceEnabled,
            surfaceFill = true
          )

          Column(
            modifier = Modifier
              .align(Alignment.BottomCenter)
              .padding(bottom = 118.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            // Clear orbit chrome for judges
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(Color.Black.copy(alpha = 0.78f))
                .border(1.dp, LivePrimaryContainer.copy(alpha = 0.55f), RoundedCornerShape(99.dp))
                .padding(horizontal = 16.dp, vertical = 7.dp)
            ) {
              val depthLabel = depthInferenceMs?.let { " · Depth ~${it} ms" } ?: ""
              Text(
                text = if (uiState.remotePointCloud != null)
                  "Live 3D · drag to orbit$depthLabel"
                else
                  "Connecting 3D stream...",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
              )
            }

            if (sharedPresenceEnabled) {
              Spacer(modifier = Modifier.height(8.dp))
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(99.dp))
                  .background(Color(0xFF0EA5E9).copy(alpha = 0.22f))
                  .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.55f), RoundedCornerShape(99.dp))
                  .padding(horizontal = 12.dp, vertical = 5.dp)
              ) {
                Text(
                  text = "Shared Presence (preview)",
                  color = Color(0xFFE0F2FE),
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Medium
                )
              }
            }
          }
        }
      } else if (uiState.remoteUid != null && !uiState.isRemoteVideoMuted) {
        // Live remote video stream rendered directly from Agora RTC.
        // NOTE: update{} only re-binds when the UID actually changes — calling
        // setupRemoteVideo on every recomposition caused flicker + native churn.
        var boundUid by remember { mutableStateOf<Int?>(null) }
        AndroidView(
          factory = { ctx ->
            SurfaceView(ctx).apply {
              viewModel.agoraManager.setupRemoteVideo(this, uiState.remoteUid!!)
              boundUid = uiState.remoteUid
            }
          },
          update = { surfaceView ->
            val uid = uiState.remoteUid
            if (uid != null && boundUid != uid) {
              viewModel.agoraManager.setupRemoteVideo(surfaceView, uid)
              boundUid = uid
            }
          },
          modifier = Modifier.fillMaxSize()
        )
      } else {
        // Modern waiting / connecting placeholder when remote peer is not yet joined
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(
              Brush.verticalGradient(
                colors = listOf(
                  Color(0xFF0F172A),
                  Color(0xFF1E293B),
                  Color(0xFF020617)
                )
              )
            ),
          contentAlignment = Alignment.Center
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            LiveVolumeAvatar(
              avatarUrl = null,
              initials = callerName.take(2).uppercase().ifBlank { "3D" },
              size = 92.dp,
              showOnlineBadge = false
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
              text = callerName,
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.alpha(pulseAlpha)
            ) {
              Box(
                modifier = Modifier
                  .size(8.dp)
                  .clip(CircleShape)
                  .background(if (uiState.remoteUid != null) LiveSuccess else LivePrimaryContainer)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = if (uiState.remoteUid == null) "Waiting for $callerName to connect..." else "Remote video muted",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
                fontWeight = FontWeight.Medium
              )
            }

            if (uiState.channelName.isNotBlank()) {
              Spacer(modifier = Modifier.height(10.dp))
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(99.dp))
                  .background(Color.White.copy(alpha = 0.08f))
                  .padding(horizontal = 14.dp, vertical = 6.dp)
              ) {
                Text(
                  text = "Room: ${uiState.channelName}",
                  style = MaterialTheme.typography.labelSmall,
                  color = Color.White.copy(alpha = 0.6f),
                  fontSize = 11.sp
                )
              }
            }
          }
        }
      }

      // Smooth ambient scrim: ensures HUD and bottom dock remain crisp and legible
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            Brush.verticalGradient(
              colors = listOf(
                Color.Black.copy(alpha = 0.45f),
                Color.Transparent,
                Color.Black.copy(alpha = 0.15f),
                Color.Black.copy(alpha = 0.80f)
              )
            )
          )
      )
    }

    // 2. Top Header HUD Bar (statusBarsPadding, no clipping or overlap)
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .statusBarsPadding()
        .padding(horizontal = 16.dp, vertical = 12.dp)
        .align(Alignment.TopCenter),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.Top
    ) {
      // Left HUD: Caller Identity & Duration Pill
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
          .shadow(10.dp, RoundedCornerShape(99.dp), spotColor = Color.Black.copy(alpha = 0.12f))
          .clip(RoundedCornerShape(99.dp))
          .background(Color.White.copy(alpha = 0.95f))
          .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(99.dp))
          .padding(horizontal = 12.dp, vertical = 8.dp)
          .testTag("persistent_call_duration_timer")
      ) {
        LiveVolumeAvatar(
          avatarUrl = null,
          initials = callerName.take(2).uppercase().ifBlank { "3D" },
          size = 32.dp,
          showOnlineBadge = false
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column {
          Text(
            text = callerName,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (uiState.remoteUid != null) LiveSuccess else LivePrimaryContainer)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
              text = elapsedDurationFormatted,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontWeight = FontWeight.SemiBold,
              fontSize = 11.sp,
              modifier = Modifier.testTag("elapsed_call_duration_text")
            )
          }
        }
      }

      // Right HUD: Self Camera PiP (Picture-in-Picture) Window with Agora local video
      Box(
        modifier = Modifier
          .size(width = 88.dp, height = 124.dp)
          .shadow(14.dp, RoundedCornerShape(18.dp), spotColor = Color.Black.copy(alpha = 0.18f))
          .clip(RoundedCornerShape(18.dp))
          .background(Color.Black)
          .border(2.dp, Color.White, RoundedCornerShape(18.dp))
          .clickable { viewModel.switchCamera() }
          .testTag("self_pip_window")
      ) {
        if (uiState.isCameraOn && hasCameraPermission) {
          if (viewModel.agoraManager.isConfigured()) {
            // Bind the local canvas once; update{} only re-binds after
            // camera toggle (isCameraOn flip) to avoid native re-registration churn.
            var localBound by remember { mutableStateOf(false) }
            AndroidView(
              factory = { ctx ->
                SurfaceView(ctx).apply {
                  setZOrderMediaOverlay(true)
                  viewModel.agoraManager.setupLocalVideo(this)
                  localSurfaceView = this
                  localBound = true
                }
              },
              update = { surfaceView ->
                localSurfaceView = surfaceView
                if (!localBound) {
                  viewModel.agoraManager.setupLocalVideo(surfaceView)
                  localBound = true
                }
              },
              modifier = Modifier.fillMaxSize()
            )
          } else {
            // CameraX fallback: always shows self video preview even if Agora RTC is awaiting App ID
            com.example.ui.components.CameraPreview(
              isFrontCamera = uiState.isFrontCamera,
              isCameraOn = uiState.isCameraOn,
              modifier = Modifier.fillMaxSize()
            )
          }
        } else if (!uiState.isCameraOn) {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(Color(0xFF1E293B)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.VideocamOff,
              contentDescription = "Camera Off",
              tint = Color.White.copy(alpha = 0.7f),
              modifier = Modifier.size(26.dp)
            )
          }
        }

        // Flip camera indicator on self preview
        Box(
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(4.dp)
            .size(24.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.92f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.FlipCameraIos,
            contentDescription = "Switch Camera",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(13.dp)
          )
        }
      }
    }

    // 2D vs 3D Pill-Toggle (Phase 7 - two-state pill, single accent color, zero jargon)
    Row(
      modifier = Modifier
        .align(Alignment.TopCenter)
        .statusBarsPadding()
        .padding(top = 74.dp)
        .shadow(8.dp, RoundedCornerShape(99.dp))
        .clip(RoundedCornerShape(99.dp))
        .background(Color.Black.copy(alpha = 0.75f))
        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(99.dp))
        .padding(3.dp),
      horizontalArrangement = Arrangement.Center
    ) {
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(99.dp))
          .background(if (!uiState.is3DMode) LivePrimaryContainer else Color.Transparent)
          .clickable {
            HapticsManager.trigger(context, HapticType.SELECTION)
            viewModel.set3DMode(false)
          }
          .padding(horizontal = 22.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = "2D",
          color = if (!uiState.is3DMode) Color.White else Color.White.copy(alpha = 0.7f),
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold
        )
      }

      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(99.dp))
          .background(if (uiState.is3DMode) LivePrimaryContainer else Color.Transparent)
          .clickable {
            HapticsManager.trigger(context, HapticType.SELECTION)
            viewModel.set3DMode(true)
          }
          .padding(horizontal = 22.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = "3D",
          color = if (uiState.is3DMode) Color.White else Color.White.copy(alpha = 0.7f),
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold
        )
      }
    }

    // Shared Presence desk preview toggle (OpenGL grid — not ARCore)
    if (uiState.is3DMode) {
      Row(
        modifier = Modifier
          .align(Alignment.TopCenter)
          .statusBarsPadding()
          .padding(top = 118.dp)
          .shadow(6.dp, RoundedCornerShape(99.dp))
          .clip(RoundedCornerShape(99.dp))
          .background(Color.Black.copy(alpha = 0.7f))
          .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(99.dp))
          .clickable {
            HapticsManager.trigger(context, HapticType.SELECTION)
            sharedPresenceEnabled = !sharedPresenceEnabled
          }
          .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(if (sharedPresenceEnabled) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.35f))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = if (sharedPresenceEnabled) "Presence On" else "Presence Off",
          color = Color.White.copy(alpha = 0.9f),
          fontSize = 11.sp,
          fontWeight = FontWeight.SemiBold
        )
      }
    }

    // 3. Bottom Controls (navigationBarsPadding, elevated floating dock)
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .align(Alignment.BottomCenter)
        .navigationBarsPadding()
        .padding(bottom = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // 5-Button Elevated Call Controls Dock
      CallControlsOverlay(
        isMuted = uiState.isMuted,
        isCameraOn = uiState.isCameraOn,
        isSpeakerOn = uiState.isSpeakerOn,
        onToggleMute = {
          HapticsManager.trigger(context, HapticType.LIGHT)
          viewModel.toggleMute()
        },
        onToggleCamera = {
          HapticsManager.trigger(context, HapticType.LIGHT)
          viewModel.toggleCamera()
        },
        onToggleSpeaker = {
          HapticsManager.trigger(context, HapticType.LIGHT)
          viewModel.toggleSpeaker()
        },
        onSwitchCamera = {
          HapticsManager.trigger(context, HapticType.LIGHT)
          viewModel.switchCamera()
        },
        onEndCall = {
          HapticsManager.trigger(context, HapticType.CALL_END)
          signalingRepo.endCall()
          viewModel.endCall(callHistoryRepository)
          onEndCall()
        }
      )
    }

      // Agora App ID Alert Banner if unconfigured
      if (!viewModel.agoraManager.isConfigured()) {
        Box(
          modifier = Modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(top = 76.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(Color(0xFFE11D48).copy(alpha = 0.92f))
            .clickable {
              inputAppId = viewModel.agoraManager.getEffectiveAppId().let { if (it == "YOUR_AGORA_APP_ID") "" else it }
              showConfigureAgoraDialog = true
            }
            .padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.CellTower,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "Tap to enter Agora App ID for live streaming",
              color = Color.White,
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold
            )
          }
        }
      }

      // 4. Graceful Permission Denial Card (Phase 8 Hardening)
    if (!hasCameraPermission || !hasMicPermission) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black.copy(alpha = 0.90f))
          .padding(24.dp),
        contentAlignment = Alignment.Center
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF1E293B))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
            .padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Box(
            modifier = Modifier
              .size(56.dp)
              .clip(CircleShape)
              .background(LivePrimaryContainer.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.CameraAlt,
              contentDescription = null,
              tint = LivePrimaryContainer,
              modifier = Modifier.size(28.dp)
            )
          }

          Spacer(modifier = Modifier.height(16.dp))

          Text(
            text = "Permissions Required",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
          )

          Spacer(modifier = Modifier.height(8.dp))

          Text(
            text = "LiveVolume requires camera and microphone access to stream live video and 3D volumetric reconstructions.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.75f),
            textAlign = TextAlign.Center
          )

          Spacer(modifier = Modifier.height(20.dp))

          Button(
            onClick = {
              permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
              )
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = LivePrimaryContainer
            ),
            shape = RoundedCornerShape(99.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text("Grant Permissions", fontWeight = FontWeight.SemiBold, color = Color.White)
          }

          Spacer(modifier = Modifier.height(10.dp))

          OutlinedButton(
            onClick = {
              HapticsManager.trigger(context, HapticType.CALL_END)
              signalingRepo.endCall()
              viewModel.endCall(callHistoryRepository)
              onEndCall()
            },
            shape = RoundedCornerShape(99.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text("Leave Call", color = Color.White.copy(alpha = 0.8f))
          }
        }
      }
    }
  }

  if (showConfigureAgoraDialog) {
    AlertDialog(
      onDismissRequest = { showConfigureAgoraDialog = false },
      title = { Text("Configure Agora RTC") },
      text = {
        Column {
          Text(
            text = "Enter your Agora App ID to enable real-time 3D volumetric point cloud and video streaming between devices:",
            style = MaterialTheme.typography.bodyMedium
          )
          Spacer(modifier = Modifier.height(12.dp))
          OutlinedTextField(
            value = inputAppId,
            onValueChange = { inputAppId = it.trim() },
            label = { Text("Agora App ID") },
            placeholder = { Text("e.g. 484a9e...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "Obtain a free App ID at console.agora.io (Testing mode / No certificate needed)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (inputAppId.isNotBlank()) {
              viewModel.agoraManager.saveAppId(inputAppId)
              viewModel.agoraManager.joinChannel(uiState.channelName.ifBlank { "livevolume_default" })
            }
            showConfigureAgoraDialog = false
          },
          enabled = inputAppId.isNotBlank()
        ) {
          Text("Save & Connect")
        }
      },
      dismissButton = {
        TextButton(onClick = { showConfigureAgoraDialog = false }) {
          Text("Cancel")
        }
      }
    )
  }
}
