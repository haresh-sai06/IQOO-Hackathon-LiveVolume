package com.example.util

import android.content.Context
import android.util.Log
import android.view.SurfaceView
import com.example.BuildConfig
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.video.VideoCanvas
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Singleton managing Agora RTC Engine lifecycle, local/remote video feeds,
 * and audio/video controls for real-time two-way calling.
 */
class AgoraManager private constructor(private val context: Context) {

  private var rtcEngine: RtcEngine? = null

  private val _remoteUid = MutableStateFlow<Int?>(null)
  val remoteUid: StateFlow<Int?> = _remoteUid.asStateFlow()

  private val _isJoined = MutableStateFlow(false)
  val isJoined: StateFlow<Boolean> = _isJoined.asStateFlow()

  private val _isRemoteVideoMuted = MutableStateFlow(false)
  val isRemoteVideoMuted: StateFlow<Boolean> = _isRemoteVideoMuted.asStateFlow()

  private val _currentChannel = MutableStateFlow<String?>(null)
  val currentChannel: StateFlow<String?> = _currentChannel.asStateFlow()

  // 3D Point Cloud Data Stream State (Phase 6)
  private val _remotePointCloud = MutableStateFlow<com.example.ml.PointCloud?>(null)
  val remotePointCloud: StateFlow<com.example.ml.PointCloud?> = _remotePointCloud.asStateFlow()

  private var dataStreamId: Int = 0
  private var streamFrameId: Int = 0
  private val pointCloudReceiver = PointCloudStreamer.Receiver()
  private val dataStreamScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)

  private var currentAppId: String = BuildConfig.AGORA_APP_ID

  private val rtcEventHandler = object : IRtcEngineEventHandler() {
    override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
      Log.d(TAG, "Joined Agora channel: $channel with uid: $uid in ${elapsed}ms")
      _isJoined.value = true
      initDataStream()
    }

    override fun onUserJoined(uid: Int, elapsed: Int) {
      Log.d(TAG, "Remote user joined Agora channel: $uid")
      _remoteUid.value = uid
      _isRemoteVideoMuted.value = false
    }

    override fun onUserOffline(uid: Int, reason: Int) {
      Log.d(TAG, "Remote user offline: $uid, reason: $reason")
      if (_remoteUid.value == uid) {
        _remoteUid.value = null
        _remotePointCloud.value = null
      }
    }

    override fun onUserMuteVideo(uid: Int, muted: Boolean) {
      Log.d(TAG, "Remote user $uid video muted: $muted")
      if (_remoteUid.value == uid) {
        _isRemoteVideoMuted.value = muted
      }
    }

    override fun onStreamMessage(uid: Int, streamId: Int, data: ByteArray?) {
      if (data != null) {
        val completedPointCloud = pointCloudReceiver.processPacket(data)
        if (completedPointCloud != null) {
          _remotePointCloud.value = completedPointCloud
        }
      }
    }

    override fun onStreamMessageError(uid: Int, streamId: Int, error: Int, missed: Int, cached: Int) {
      Log.w(TAG, "Agora Stream message error: code $error, missed: $missed, cached: $cached")
    }

    override fun onError(err: Int) {
      Log.e(TAG, "Agora RTC error: $err")
    }
  }

  fun setCustomAppId(appId: String) {
    if (appId.isNotBlank() && appId != currentAppId) {
      currentAppId = appId
      destroy()
      initEngine()
    }
  }

  private fun initEngine() {
    if (rtcEngine != null) return

    val appIdToUse = currentAppId.ifBlank { BuildConfig.AGORA_APP_ID }
    if (appIdToUse.isBlank() || appIdToUse == "YOUR_AGORA_APP_ID") {
      Log.w(TAG, "Agora App ID is not configured. Please set your Agora App ID.")
    }

    try {
      val config = RtcEngineConfig().apply {
        mContext = context.applicationContext
        mAppId = appIdToUse
        mEventHandler = rtcEventHandler
        mChannelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
      }
      rtcEngine = RtcEngine.create(config).apply {
        enableVideo()
        enableAudio()
        setEnableSpeakerphone(true)
      }
      Log.i(TAG, "Agora RTC Engine initialized successfully")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to initialize Agora RTC Engine: ${e.message}", e)
    }
  }

  fun joinChannel(channelName: String, token: String? = null, uid: Int = 0) {
    initEngine()
    val engine = rtcEngine ?: run {
      Log.e(TAG, "Cannot join channel: RTC Engine is null")
      return
    }

    _currentChannel.value = channelName
    val options = ChannelMediaOptions().apply {
      channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
      clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
      autoSubscribeAudio = true
      autoSubscribeVideo = true
      publishCameraTrack = true
      publishMicrophoneTrack = true
    }

    val res = engine.joinChannel(token, channelName, uid, options)
    Log.d(TAG, "joinChannel result code: $res for channel $channelName")
  }

  fun setupLocalVideo(surfaceView: SurfaceView) {
    initEngine()
    val engine = rtcEngine ?: return
    val canvas = VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, 0)
    engine.setupLocalVideo(canvas)
    engine.startPreview()
  }

  fun setupRemoteVideo(surfaceView: SurfaceView, uid: Int) {
    initEngine()
    val engine = rtcEngine ?: return
    val canvas = VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, uid)
    engine.setupRemoteVideo(canvas)
  }

  fun muteLocalAudio(isMuted: Boolean) {
    rtcEngine?.muteLocalAudioStream(isMuted)
  }

  fun enableLocalVideo(isEnabled: Boolean) {
    rtcEngine?.enableLocalVideo(isEnabled)
    if (isEnabled) {
      rtcEngine?.startPreview()
    } else {
      rtcEngine?.stopPreview()
    }
  }

  fun switchCamera() {
    rtcEngine?.switchCamera()
  }

  fun enableSpeakerphone(isSpeaker: Boolean) {
    rtcEngine?.setEnableSpeakerphone(isSpeaker)
  }

  private fun initDataStream() {
    val engine = rtcEngine ?: return
    try {
      val config = io.agora.rtc2.DataStreamConfig().apply {
        syncWithAudio = false
        ordered = false
      }
      dataStreamId = engine.createDataStream(config)
      Log.i(TAG, "Created Agora Data Stream ID: $dataStreamId")
    } catch (e: Exception) {
      Log.e(TAG, "Failed creating Agora Data Stream: ${e.message}", e)
    }
  }

  fun sendPointCloud(pointCloud: com.example.ml.PointCloud) {
    val engine = rtcEngine ?: return
    if (!_isJoined.value || dataStreamId == 0) return

    dataStreamScope.launch {
      try {
        val chunks = PointCloudStreamer.serializeToChunks(pointCloud, streamFrameId++)
        for (chunk in chunks) {
          engine.sendStreamMessage(dataStreamId, chunk)
        }
      } catch (e: Exception) {
        Log.e(TAG, "Failed sending point cloud chunk over Agora Data Stream: ${e.message}")
      }
    }
  }

  fun leaveChannel() {
    try {
      rtcEngine?.stopPreview()
      rtcEngine?.leaveChannel()
    } catch (e: Exception) {
      Log.w(TAG, "Error leaving channel: ${e.message}")
    }
    _isJoined.value = false
    _remoteUid.value = null
    _remotePointCloud.value = null
    _currentChannel.value = null
    dataStreamId = 0
  }

  fun destroy() {
    leaveChannel()
    RtcEngine.destroy()
    rtcEngine = null
  }

  companion object {
    private const val TAG = "AgoraManager"

    @Volatile
    private var INSTANCE: AgoraManager? = null

    fun getInstance(context: Context): AgoraManager {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: AgoraManager(context.applicationContext).also { INSTANCE = it }
      }
    }
  }
}
