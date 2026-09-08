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
 * Result of on-device monocular depth estimation.
 *
 * @param depthMap Normalized float depth values in range [0.0..1.0] (higher = closer).
 * @param width Width of depth map (typically 256).
 * @param height Height of depth map (typically 256).
 * @param latencyMs Inference execution time in milliseconds.
 * @param delegateUsed Acceleration delegate utilized ("NNAPI", "GPU", or "CPU").
 * @param depthBitmap Visual representation of the depth map for rendering/preview.
 */
data class DepthResult(
  val depthMap: FloatArray,
  val width: Int = 256,
  val height: Int = 256,
  val latencyMs: Long,
  val delegateUsed: String,
  val depthBitmap: Bitmap
)

/**
 * On-device neural depth estimation engine powered by TensorFlow Lite and MiDaS-small.
 * Supports hardware acceleration via NNAPI delegate with seamless GPU and multi-threaded CPU fallback.
 */
class DepthEstimator(private val context: Context) {

  private var interpreter: Interpreter? = null
  private var gpuDelegate: GpuDelegate? = null
  var delegateUsed: String = "Unknown"
    private set

  private val _latestDepth = MutableStateFlow<DepthResult?>(null)
  val latestDepth: StateFlow<DepthResult?> = _latestDepth.asStateFlow()

  private val scope = CoroutineScope(Dispatchers.Default)
  private var isProcessing = false
  private var frameCounter = 0

  // Pre-allocated reusable buffers for high-performance memory efficiency
  private val inputBuffer: ByteBuffer = ByteBuffer.allocateDirect(1 * 256 * 256 * 3 * 4).apply {
    order(ByteOrder.nativeOrder())
  }
  private val outputBuffer = Array(1) { Array(256) { FloatArray(256) } }
  private val intValues = IntArray(256 * 256)

  // ImageNet normalization constants used by MiDaS v2.1
  private val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
  private val std = floatArrayOf(0.229f, 0.224f, 0.225f)

  init {
    initInterpreter()
  }

  private fun loadModelFile(modelPath: String): MappedByteBuffer {
    val fileDescriptor = context.assets.openFd(modelPath)
    val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
    val fileChannel = inputStream.channel
    val startOffset = fileDescriptor.startOffset
    val declaredLength = fileDescriptor.declaredLength
    return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
  }

  private fun initInterpreter() {
    val modelBuffer = try {
      loadModelFile("midas_small.tflite")
    } catch (e: Exception) {
      Log.e(TAG, "Failed loading midas_small.tflite from assets: ${e.message}", e)
      return
    }

    // Attempt 1: NNAPI Delegate (Android Neural Networks API)
    try {
      val options = Interpreter.Options().apply {
        setUseNNAPI(true)
        setNumThreads(4)
      }
      interpreter = Interpreter(modelBuffer, options)
      delegateUsed = "NNAPI"
      Log.i(TAG, "Initialized DepthEstimator with NNAPI acceleration delegate")
      return
    } catch (e: Exception) {
      Log.w(TAG, "NNAPI delegate initialization failed: ${e.message}. Falling back to GPU delegate...")
    }

    // Attempt 2: GPU Delegate Fallback
    try {
      val delegate = GpuDelegate()
      gpuDelegate = delegate
      val options = Interpreter.Options().apply {
        addDelegate(delegate)
        setNumThreads(4)
      }
      interpreter = Interpreter(modelBuffer, options)
      delegateUsed = "GPU"
      Log.i(TAG, "Initialized DepthEstimator with GPU acceleration delegate")
      return
    } catch (e: Exception) {
      Log.w(TAG, "GPU delegate initialization failed: ${e.message}. Falling back to CPU delegate...")
      gpuDelegate?.close()
      gpuDelegate = null
    }

    // Attempt 3: Multi-threaded CPU Fallback
    try {
      val options = Interpreter.Options().apply {
        setNumThreads(4)
      }
      interpreter = Interpreter(modelBuffer, options)
      delegateUsed = "CPU (4-threads)"
      Log.i(TAG, "Initialized DepthEstimator with CPU multi-threaded delegate")
    } catch (e: Exception) {
      Log.e(TAG, "Failed initializing TensorFlow Lite Interpreter: ${e.message}", e)
    }
  }

  /**
   * Processes a CameraX ImageProxy frame, downsampling to every 2nd–3rd frame for performance.
   */
  fun processImageProxy(imageProxy: ImageProxy) {
    frameCounter++
    // Sample every 2nd frame for smooth performance without heating the device
    if (frameCounter % 2 != 0 || isProcessing || interpreter == null) {
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

        val result = estimateDepthInternal(scaled, startTime)
        result?.let {
          _latestDepth.value = it
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error during depth estimation frame processing: ${e.message}", e)
      } finally {
        isProcessing = false
        imageProxy.close()
      }
    }
  }

  /**
   * Executes depth estimation on an input Bitmap.
   */
  fun estimateDepth(bitmap: Bitmap): DepthResult? {
    val tflite = interpreter ?: return null
    val startTime = SystemClock.elapsedRealtime()
    val scaled = Bitmap.createScaledBitmap(bitmap, 256, 256, true)
    return estimateDepthInternal(scaled, startTime)
  }

  private fun estimateDepthInternal(scaled: Bitmap, startTime: Long): DepthResult? {
    val tflite = interpreter ?: return null

    // 1. Prepare input tensor with ImageNet normalization
    inputBuffer.rewind()
    scaled.getPixels(intValues, 0, 256, 0, 0, 256, 256)

    for (pixel in intValues) {
      val r = ((pixel shr 16 and 0xFF) / 255.0f - mean[0]) / std[0]
      val g = ((pixel shr 8 and 0xFF) / 255.0f - mean[1]) / std[1]
      val b = ((pixel and 0xFF) / 255.0f - mean[2]) / std[2]
      inputBuffer.putFloat(r)
      inputBuffer.putFloat(g)
      inputBuffer.putFloat(b)
    }

    // 2. Run TFLite inference
    synchronized(this) {
      tflite.run(inputBuffer, outputBuffer)
    }

    val latencyMs = SystemClock.elapsedRealtime() - startTime

    // 3. Extract and normalize depth map to [0.0..1.0]
    var minVal = Float.MAX_VALUE
    var maxVal = Float.MIN_VALUE
    val flatDepth = FloatArray(256 * 256)
    val rawGrid = outputBuffer[0]

    var idx = 0
    for (y in 0 until 256) {
      val row = rawGrid[y]
      for (x in 0 until 256) {
        val v = row[x]
        flatDepth[idx++] = v
        if (v < minVal) minVal = v
        if (v > maxVal) maxVal = v
      }
    }

    val range = if (maxVal - minVal > 1e-6f) (maxVal - minVal) else 1.0f
    val depthPixels = IntArray(256 * 256)

    for (i in flatDepth.indices) {
      val norm = ((flatDepth[i] - minVal) / range).coerceIn(0.0f, 1.0f)
      flatDepth[i] = norm

      // Color mapping: Turbo / Spectral gradient from blue (far) to red/orange (near)
      val gray = (norm * 255).toInt()
      depthPixels[i] = Color.rgb(gray, (gray * 0.8f).toInt(), 255 - gray)
    }

    val depthBmp = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply {
      setPixels(depthPixels, 0, 256, 0, 0, 256, 256)
    }

    Log.d(TAG, "Depth inference took ${latencyMs}ms using $delegateUsed delegate (min: %.1f, max: %.1f)".format(minVal, maxVal))

    return DepthResult(
      depthMap = flatDepth,
      width = 256,
      height = 256,
      latencyMs = latencyMs,
      delegateUsed = delegateUsed,
      depthBitmap = depthBmp
    )
  }

  fun createAnalyzer(): ImageAnalysis.Analyzer {
    return ImageAnalysis.Analyzer { imageProxy ->
      processImageProxy(imageProxy)
    }
  }

  fun close() {
    interpreter?.close()
    interpreter = null
    gpuDelegate?.close()
    gpuDelegate = null
  }

  companion object {
    private const val TAG = "DepthEstimator"
  }
}
