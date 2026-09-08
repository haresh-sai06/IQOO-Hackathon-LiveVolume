package com.example.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Interleaved 3D Point Cloud geometry: [X, Y, Z, R, G, B,  X, Y, Z, R, G, B, ...]
 */
data class PointCloud(
  val vertexData: FloatArray,
  val pointCount: Int,
  val timestamp: Long = System.currentTimeMillis()
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    other as PointCloud
    return pointCount == other.pointCount && timestamp == other.timestamp
  }

  override fun hashCode(): Int {
    var result = pointCount
    result = 31 * result + timestamp.hashCode()
    return result
  }
}

/**
 * Result of monocular depth estimation combined with person silhouette segmentation.
 *
 * @param rawDepthMap Normalized float depth values in range [0.0..1.0] for the entire scene.
 * @param personMask Foreground person confidence mask in range [0.0..1.0] (1.0 = human subject).
 * @param maskedDepthMap Depth values isolated to the person's silhouette (background depth is 0.0f).
 * @param pointCloud Unprojected 3D colored point cloud of the person's volumetric silhouette.
 * @param width Width of depth map (256).
 * @param height Height of depth map (256).
 * @param depthLatencyMs Depth estimation inference time in milliseconds.
 * @param segLatencyMs Person segmentation inference time in milliseconds.
 * @param totalLatencyMs Combined ML inference and processing latency in milliseconds.
 * @param delegateUsed Acceleration delegate utilized ("NNAPI", "GPU", or "CPU").
 * @param rawDepthBitmap Visual color map of full-scene depth.
 * @param maskedDepthBitmap Visual color map of person-only depth with background zeroed out (black).
 */
data class DepthResult(
  val rawDepthMap: FloatArray,
  val personMask: FloatArray,
  val maskedDepthMap: FloatArray,
  val pointCloud: PointCloud? = null,
  val width: Int = 256,
  val height: Int = 256,
  val depthLatencyMs: Long = 0L,
  val segLatencyMs: Long = 0L,
  val totalLatencyMs: Long = 0L,
  val delegateUsed: String = "Unknown",
  val rawDepthBitmap: Bitmap,
  val maskedDepthBitmap: Bitmap,

  // Backwards compatibility properties for existing consumers
  val depthMap: FloatArray = maskedDepthMap,
  val depthBitmap: Bitmap = maskedDepthBitmap,
  val latencyMs: Long = totalLatencyMs
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    other as DepthResult
    return totalLatencyMs == other.totalLatencyMs &&
           delegateUsed == other.delegateUsed &&
           rawDepthBitmap == other.rawDepthBitmap &&
           maskedDepthBitmap == other.maskedDepthBitmap
  }

  override fun hashCode(): Int {
    var result = totalLatencyMs.hashCode()
    result = 31 * result + delegateUsed.hashCode()
    result = 31 * result + rawDepthBitmap.hashCode()
    result = 31 * result + maskedDepthBitmap.hashCode()
    return result
  }
}

/**
 * Combined Neural Depth Estimation & Person Segmentation Engine.
 * Runs MiDaS-small monocular depth alongside MediaPipe Selfie Segmentation
 * using hardware acceleration (NNAPI / GPU / CPU fallback).
 */
class DepthEstimator(private val context: Context) {

  // MiDaS depth model
  private var depthInterpreter: Interpreter? = null
  private var depthGpuDelegate: GpuDelegate? = null

  // MediaPipe selfie segmentation model
  private var segInterpreter: Interpreter? = null
  private var segGpuDelegate: GpuDelegate? = null
  private var segOutputChannels = 1

  var delegateUsed: String = "Unknown"
    private set

  private val _latestDepth = MutableStateFlow<DepthResult?>(null)
  val latestDepth: StateFlow<DepthResult?> = _latestDepth.asStateFlow()

  private val scope = CoroutineScope(Dispatchers.Default)
  private var isProcessing = false
  private var frameCounter = 0

  // Pre-allocated reusable buffers for zero GC overhead during live video
  private val depthInputBuffer: ByteBuffer = ByteBuffer.allocateDirect(1 * 256 * 256 * 3 * 4).apply {
    order(ByteOrder.nativeOrder())
  }
  private val depthOutputBuffer = Array(1) { Array(256) { FloatArray(256) } }

  private val segInputBuffer: ByteBuffer = ByteBuffer.allocateDirect(1 * 256 * 256 * 3 * 4).apply {
    order(ByteOrder.nativeOrder())
  }
  private var segOutputBuffer: ByteBuffer? = null

  private val intValues = IntArray(256 * 256)
  private val rawPixelBuffer = IntArray(256 * 256)
  private val maskedPixelBuffer = IntArray(256 * 256)
  private val pointCloudBuffer = FloatArray(16384 * 6)

  // ImageNet normalization constants for MiDaS
  private val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
  private val std = floatArrayOf(0.229f, 0.224f, 0.225f)

  @Volatile var isReady: Boolean = false
    private set

  init {
    CoroutineScope(Dispatchers.Default).launch {
      try {
        initDepthInterpreter()
        initSegInterpreter()
        isReady = true
        Log.i(TAG, "DepthEstimator background initialization complete. Ready for inference.")
      } catch (t: Throwable) {
        Log.e(TAG, "Failed initializing DepthEstimator: ${t.message}", t)
      }
    }
  }

  private fun loadModelFile(modelPath: String): MappedByteBuffer {
    val fileDescriptor = context.assets.openFd(modelPath)
    val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
    val fileChannel = inputStream.channel
    val startOffset = fileDescriptor.startOffset
    val declaredLength = fileDescriptor.declaredLength
    return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
  }

  private fun initDepthInterpreter() {
    val modelBuffer = try {
      loadModelFile("midas_small.tflite")
    } catch (t: Throwable) {
      Log.e(TAG, "Failed loading midas_small.tflite from assets: ${t.message}", t)
      return
    }

    // 1. NNAPI Delegate
    try {
      val options = Interpreter.Options().apply {
        setUseNNAPI(true)
        setNumThreads(4)
      }
      depthInterpreter = Interpreter(modelBuffer, options)
      delegateUsed = "NNAPI"
      Log.i(TAG, "Initialized MiDaS depth model with NNAPI delegate")
      return
    } catch (t: Throwable) {
      Log.w(TAG, "NNAPI failed for MiDaS: ${t.message}. Trying GPU...")
    }

    // 2. GPU Delegate Fallback
    try {
      val delegate = GpuDelegate()
      depthGpuDelegate = delegate
      val options = Interpreter.Options().apply {
        addDelegate(delegate)
        setNumThreads(4)
      }
      depthInterpreter = Interpreter(modelBuffer, options)
      delegateUsed = "GPU"
      Log.i(TAG, "Initialized MiDaS depth model with GPU delegate")
      return
    } catch (t: Throwable) {
      Log.w(TAG, "GPU failed for MiDaS: ${t.message}. Falling back to CPU...")
      try { depthGpuDelegate?.close() } catch (ignored: Throwable) {}
      depthGpuDelegate = null
    }

    // 3. Multi-threaded CPU Fallback
    try {
      val options = Interpreter.Options().apply {
        setNumThreads(4)
      }
      depthInterpreter = Interpreter(modelBuffer, options)
      delegateUsed = "CPU (4-threads)"
      Log.i(TAG, "Initialized MiDaS depth model with CPU delegate")
    } catch (t: Throwable) {
      Log.e(TAG, "Failed initializing MiDaS depth interpreter: ${t.message}", t)
    }
  }

  private fun initSegInterpreter() {
    val modelBuffer = try {
      loadModelFile("selfie_segmentation.tflite")
    } catch (t: Throwable) {
      Log.e(TAG, "Failed loading selfie_segmentation.tflite from assets: ${t.message}", t)
      return
    }

    // Attempt GPU Delegate first for segmentation
    try {
      val delegate = GpuDelegate()
      segGpuDelegate = delegate
      val options = Interpreter.Options().apply {
        addDelegate(delegate)
        setNumThreads(2)
      }
      val interp = Interpreter(modelBuffer, options)
      setupSegOutput(interp)
      segInterpreter = interp
      Log.i(TAG, "Initialized Selfie Segmentation with GPU delegate")
      return
    } catch (t: Throwable) {
      Log.w(TAG, "GPU failed for Selfie Segmentation: ${t.message}. Trying CPU...")
      try { segGpuDelegate?.close() } catch (ignored: Throwable) {}
      segGpuDelegate = null
    }

    // CPU fallback
    try {
      val options = Interpreter.Options().apply {
        setNumThreads(3)
      }
      val interp = Interpreter(modelBuffer, options)
      setupSegOutput(interp)
      segInterpreter = interp
      Log.i(TAG, "Initialized Selfie Segmentation with CPU delegate")
    } catch (t: Throwable) {
      Log.e(TAG, "Failed initializing Selfie Segmentation interpreter: ${t.message}", t)
    }
  }

  private fun setupSegOutput(interp: Interpreter) {
    val outputTensor = interp.getOutputTensor(0)
    val shape = outputTensor.shape()
    segOutputChannels = shape.getOrNull(3) ?: 1
    val bytes = 1 * 256 * 256 * segOutputChannels * 4
    segOutputBuffer = ByteBuffer.allocateDirect(bytes).apply {
      order(ByteOrder.nativeOrder())
    }
    Log.i(TAG, "Selfie Segmentation output shape: ${shape.contentToString()}, channels: $segOutputChannels")
  }

  /**
   * Processes a CameraX ImageProxy frame, sampling every 2nd frame for smooth performance.
   */
  fun processImageProxy(imageProxy: ImageProxy) {
    frameCounter++
    if (frameCounter % 2 != 0 || isProcessing || depthInterpreter == null) {
      imageProxy.close()
      return
    }

    isProcessing = true
    val startTime = SystemClock.elapsedRealtime()

    scope.launch {
      try {
        val bitmap = imageProxy.toBitmap()
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        val matrix = Matrix().apply {
          if (rotationDegrees != 0) {
            postRotate(rotationDegrees.toFloat())
          }
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        val scaled = Bitmap.createScaledBitmap(rotated, 256, 256, true)

        val result = estimateDepthAndSegmentInternal(scaled, startTime)
        result?.let {
          _latestDepth.value = it
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error during depth & segmentation processing: ${e.message}", e)
      } finally {
        isProcessing = false
        imageProxy.close()
      }
    }
  }

  /**
   * Executes depth estimation and silhouette segmentation directly on a Bitmap.
   */
  fun estimateDepth(bitmap: Bitmap): DepthResult? {
    if (depthInterpreter == null) return null
    val scaled = Bitmap.createScaledBitmap(bitmap, 256, 256, true)
    val result = estimateDepthAndSegmentInternal(scaled, SystemClock.elapsedRealtime())
    result?.let { _latestDepth.value = it }
    return result
  }

  /**
   * Executes combined depth estimation and person silhouette segmentation.
   */
  private fun estimateDepthAndSegmentInternal(scaled: Bitmap, pipelineStartTime: Long): DepthResult? {
    val depthTflite = depthInterpreter ?: return null

    // 1. Fill input buffers from the scaled 256x256 bitmap
    scaled.getPixels(intValues, 0, 256, 0, 0, 256, 256)

    depthInputBuffer.rewind()
    segInputBuffer.rewind()

    for (pixel in intValues) {
      val rByte = (pixel shr 16 and 0xFF)
      val gByte = (pixel shr 8 and 0xFF)
      val bByte = (pixel and 0xFF)

      // MiDaS ImageNet normalization
      val rNorm = (rByte / 255.0f - mean[0]) / std[0]
      val gNorm = (gByte / 255.0f - mean[1]) / std[1]
      val bNorm = (bByte / 255.0f - mean[2]) / std[2]
      depthInputBuffer.putFloat(rNorm)
      depthInputBuffer.putFloat(gNorm)
      depthInputBuffer.putFloat(bNorm)

      // MediaPipe Selfie Segmentation standard [0.0..1.0] normalization
      segInputBuffer.putFloat(rByte / 255.0f)
      segInputBuffer.putFloat(gByte / 255.0f)
      segInputBuffer.putFloat(bByte / 255.0f)
    }

    // 2. Run MiDaS Depth Inference
    val depthStart = SystemClock.elapsedRealtime()
    synchronized(this) {
      depthTflite.run(depthInputBuffer, depthOutputBuffer)
    }
    val depthLatency = SystemClock.elapsedRealtime() - depthStart

    // 3. Run Selfie Segmentation Inference (if available)
    val segStart = SystemClock.elapsedRealtime()
    val personMask = FloatArray(256 * 256)
    var segLatency = 0L

    val segTflite = segInterpreter
    val segOutBuf = segOutputBuffer
    if (segTflite != null && segOutBuf != null) {
      segOutBuf.rewind()
      synchronized(segTflite) {
        segTflite.run(segInputBuffer, segOutBuf)
      }
      segLatency = SystemClock.elapsedRealtime() - segStart

      segOutBuf.rewind()
      val floatBuf = segOutBuf.asFloatBuffer()
      if (segOutputChannels == 1) {
        floatBuf.get(personMask)
      } else {
        // 2 channels: channel 0 is background, channel 1 is person
        for (i in 0 until 256 * 256) {
          val bg = floatBuf.get()
          val person = floatBuf.get()
          val expPerson = Math.exp(person.toDouble())
          val expBg = Math.exp(bg.toDouble())
          personMask[i] = (expPerson / (expPerson + expBg)).toFloat()
        }
      }
    } else {
      // Fallback: If segmentation is unavailable, assume full scene is foreground
      personMask.fill(1.0f)
    }

    // 4. Extract and normalize raw depth map to [0.0..1.0]
    var minVal = Float.MAX_VALUE
    var maxVal = Float.MIN_VALUE
    val flatRawDepth = FloatArray(256 * 256)
    val rawGrid = depthOutputBuffer[0]

    var idx = 0
    for (y in 0 until 256) {
      val row = rawGrid[y]
      for (x in 0 until 256) {
        val v = row[x]
        flatRawDepth[idx++] = v
        if (v < minVal) minVal = v
        if (v > maxVal) maxVal = v
      }
    }

    val range = if (maxVal - minVal > 1e-6f) (maxVal - minVal) else 1.0f
    val maskedDepth = FloatArray(256 * 256)
    val personThreshold = 0.5f

    // 5. Apply silhouette masking and build visualizations
    for (i in flatRawDepth.indices) {
      val normDepth = ((flatRawDepth[i] - minVal) / range).coerceIn(0.0f, 1.0f)
      flatRawDepth[i] = normDepth

      // Raw depth Turbo color mapping
      val gray = (normDepth * 255).toInt()
      rawPixelBuffer[i] = Color.rgb(gray, (gray * 0.8f).toInt(), 255 - gray)

      // Masked depth: only retain depth values inside person silhouette
      val isPerson = personMask[i] >= personThreshold
      if (isPerson) {
        maskedDepth[i] = normDepth
        maskedPixelBuffer[i] = rawPixelBuffer[i]
      } else {
        maskedDepth[i] = 0.0f
        // Pure black for background to isolate caller's volumetric silhouette
        maskedPixelBuffer[i] = Color.BLACK
      }
    }

    val rawBmp = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply {
      setPixels(rawPixelBuffer, 0, 256, 0, 0, 256, 256)
    }
    val maskedBmp = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply {
      setPixels(maskedPixelBuffer, 0, 256, 0, 0, 256, 256)
    }

    // 6. Generate 3D point cloud from masked depth & camera RGB (Phase 5 & 8)
    var pointCount = 0
    val focalLength = 220f
    val stride = if (isPerformanceFallbackEnabled) 3 else 2 // Dynamic performance fallback

    for (y in 0 until 256 step stride) {
      for (x in 0 until 256 step stride) {
        val idx = y * 256 + x
        val d = maskedDepth[idx]
        if (d <= 0.005f) continue // Skip background

        val z = 0.75f + (1.0f - d) * 1.15f
        val worldX = ((x - 128f) / focalLength) * z
        val worldY = -((y - 128f) / focalLength) * z
        val worldZ = -z

        val pixel = intValues[idx]
        val r = (pixel shr 16 and 0xFF) / 255.0f
        val g = (pixel shr 8 and 0xFF) / 255.0f
        val b = (pixel and 0xFF) / 255.0f

        val base = pointCount * 6
        if (base + 5 < pointCloudBuffer.size) {
          pointCloudBuffer[base] = worldX
          pointCloudBuffer[base + 1] = worldY
          pointCloudBuffer[base + 2] = worldZ
          pointCloudBuffer[base + 3] = r
          pointCloudBuffer[base + 4] = g
          pointCloudBuffer[base + 5] = b
          pointCount++
        }
      }
    }

    val pointCloud = PointCloud(
      vertexData = pointCloudBuffer.copyOf(pointCount * 6),
      pointCount = pointCount
    )

    val totalLatency = SystemClock.elapsedRealtime() - pipelineStartTime

    Log.d(TAG, "Depth: ${depthLatency}ms | Seg: ${segLatency}ms | Pts: $pointCount | Total: ${totalLatency}ms | Delegate: $delegateUsed")

    return DepthResult(
      rawDepthMap = flatRawDepth,
      personMask = personMask,
      maskedDepthMap = maskedDepth,
      pointCloud = pointCloud,
      width = 256,
      height = 256,
      depthLatencyMs = depthLatency,
      segLatencyMs = segLatency,
      totalLatencyMs = totalLatency,
      delegateUsed = delegateUsed,
      rawDepthBitmap = rawBmp,
      maskedDepthBitmap = maskedBmp
    )
  }

  fun createAnalyzer(): ImageAnalysis.Analyzer {
    return ImageAnalysis.Analyzer { imageProxy ->
      processImageProxy(imageProxy)
    }
  }

  fun close() {
    depthInterpreter?.close()
    depthInterpreter = null
    depthGpuDelegate?.close()
    depthGpuDelegate = null

    segInterpreter?.close()
    segInterpreter = null
    segGpuDelegate?.close()
    segGpuDelegate = null
  }

  companion object {
    private const val TAG = "DepthEstimator"
    var isPerformanceFallbackEnabled: Boolean = false
  }
}
