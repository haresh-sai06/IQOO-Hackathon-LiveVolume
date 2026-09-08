package com.example.util

import android.util.Log
import com.example.ml.PointCloud
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * High-performance Point Cloud network serialization and chunked streaming codec for Agora RTC Data Stream.
 * Encodes 3D coordinates and RGB colors into compact 8-byte quantized point representations,
 * split into <= 1024-byte packets with frame reassembly.
 */
object PointCloudStreamer {

  private const val TAG = "PointCloudStreamer"
  private const val MAGIC_HEADER: Short = 0x564C // "VL" for Volumetric Live
  private const val MAX_POINTS_PER_CHUNK = 120 // 120 pts * 8 bytes = 960 bytes payload
  private const val HEADER_SIZE = 8 // Magic(2) + FrameId(2) + ChunkIdx(1) + TotalChunks(1) + PointCount(2)

  /**
   * Serializes a PointCloud into a list of chunked ByteArrays ready to be sent over Agora Data Stream.
   *
   * @param pointCloud Source 3D point cloud.
   * @param frameId Monotonically increasing frame sequence number.
   * @param maxPoints Target number of transmitted points (downsamples evenly if pointCloud has more).
   */
  fun serializeToChunks(pointCloud: PointCloud, frameId: Int, maxPoints: Int = 1200): List<ByteArray> {
    val totalAvailable = pointCloud.pointCount
    if (totalAvailable == 0) return emptyList()

    val targetCount = totalAvailable.coerceAtMost(maxPoints)
    val step = (totalAvailable.toFloat() / targetCount).coerceAtLeast(1.0f)

    // Calculate total chunks needed
    val totalChunks = ((targetCount + MAX_POINTS_PER_CHUNK - 1) / MAX_POINTS_PER_CHUNK).coerceIn(1, 255)
    val chunks = ArrayList<ByteArray>(totalChunks)

    val vertexData = pointCloud.vertexData
    var pointsProcessed = 0
    var floatIdxAccum = 0.0f

    for (chunkIdx in 0 until totalChunks) {
      val pointsInThisChunk = (targetCount - pointsProcessed).coerceAtMost(MAX_POINTS_PER_CHUNK)
      val packetSize = HEADER_SIZE + pointsInThisChunk * 8

      val buffer = ByteBuffer.allocate(packetSize).order(ByteOrder.LITTLE_ENDIAN)
      buffer.putShort(MAGIC_HEADER)
      buffer.putShort((frameId and 0xFFFF).toShort())
      buffer.put(chunkIdx.toByte())
      buffer.put(totalChunks.toByte())
      buffer.putShort(pointsInThisChunk.toShort())

      for (p in 0 until pointsInThisChunk) {
        val srcVertexIdx = floatIdxAccum.toInt().coerceIn(0, totalAvailable - 1)
        val baseOffset = srcVertexIdx * 6

        if (baseOffset + 5 < vertexData.size) {
          val worldX = vertexData[baseOffset]
          val worldY = vertexData[baseOffset + 1]
          val worldZ = vertexData[baseOffset + 2]
          val r = vertexData[baseOffset + 3]
          val g = vertexData[baseOffset + 4]
          val b = vertexData[baseOffset + 5]

          // 1. Quantize coordinates: X and Y to [-2.0..2.0], Z to [-4.0..0.0]
          val qX = ((worldX.coerceIn(-2.0f, 2.0f) / 2.0f) * 32767.0f).toInt().toShort()
          val qY = ((worldY.coerceIn(-2.0f, 2.0f) / 2.0f) * 32767.0f).toInt().toShort()
          val qZ = (((worldZ.coerceIn(-4.0f, 0.0f) + 2.0f) / 2.0f) * 32767.0f).toInt().toShort()

          // 2. Quantize color to 16-bit RGB565
          val r5 = (r.coerceIn(0f, 1f) * 31f).toInt()
          val g6 = (g.coerceIn(0f, 1f) * 63f).toInt()
          val b5 = (b.coerceIn(0f, 1f) * 31f).toInt()
          val rgb565 = ((r5 shl 11) or (g6 shl 5) or b5).toShort()

          buffer.putShort(qX)
          buffer.putShort(qY)
          buffer.putShort(qZ)
          buffer.putShort(rgb565)
        } else {
          buffer.putLong(0L) // Padding safety
        }

        pointsProcessed++
        floatIdxAccum += step
      }

      chunks.add(buffer.array())
    }

    return chunks
  }

  /**
   * Stateful receiver for reassembling chunked packets into full PointCloud frames.
   */
  class Receiver {
    private var currentFrameId: Int = -1
    private var expectedChunks: Int = 0
    private val receivedChunks = HashMap<Int, ByteArray>()
    private var lastAssemblyTime: Long = 0L

    /**
     * Processes an incoming raw packet. If all chunks for the frame have arrived,
     * reconstructs and returns the completed PointCloud, otherwise null.
     */
    fun processPacket(packet: ByteArray): PointCloud? {
      if (packet.size < HEADER_SIZE) return null

      val buffer = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
      val magic = buffer.short
      if (magic != MAGIC_HEADER) {
        // Not a point cloud packet (could be other stream data)
        return null
      }

      val frameId = buffer.short.toInt() and 0xFFFF
      val chunkIdx = buffer.get().toInt() and 0xFF
      val totalChunks = buffer.get().toInt() and 0xFF
      val pointsInChunk = buffer.short.toInt() and 0xFFFF

      // New frame received
      if (frameId != currentFrameId) {
        // Drop incomplete older frame
        currentFrameId = frameId
        expectedChunks = totalChunks
        receivedChunks.clear()
      }

      receivedChunks[chunkIdx] = packet

      // Check if all chunks for this frame have arrived
      if (receivedChunks.size == expectedChunks && expectedChunks > 0) {
        val result = assembleFrame(expectedChunks)
        receivedChunks.clear()
        expectedChunks = 0
        lastAssemblyTime = System.currentTimeMillis()
        return result
      }

      return null
    }

    private fun assembleFrame(totalChunks: Int): PointCloud? {
      try {
        var totalPoints = 0
        for (i in 0 until totalChunks) {
          val chunkData = receivedChunks[i] ?: return null
          val buf = ByteBuffer.wrap(chunkData).order(ByteOrder.LITTLE_ENDIAN)
          buf.position(6) // Skip to points count
          totalPoints += buf.short.toInt() and 0xFFFF
        }

        val vertexData = FloatArray(totalPoints * 6)
        var writeOffset = 0

        for (i in 0 until totalChunks) {
          val chunkData = receivedChunks[i] ?: return null
          val buf = ByteBuffer.wrap(chunkData).order(ByteOrder.LITTLE_ENDIAN)
          buf.position(HEADER_SIZE)

          val pointsInChunk = (chunkData.size - HEADER_SIZE) / 8
          for (p in 0 until pointsInChunk) {
            val qX = buf.short
            val qY = buf.short
            val qZ = buf.short
            val rgb565 = buf.short.toInt() and 0xFFFF

            val worldX = (qX.toFloat() / 32767.0f) * 2.0f
            val worldY = (qY.toFloat() / 32767.0f) * 2.0f
            val worldZ = (qZ.toFloat() / 32767.0f) * 2.0f - 2.0f

            val r = ((rgb565 shr 11) and 0x1F) / 31.0f
            val g = ((rgb565 shr 5) and 0x3F) / 63.0f
            val b = (rgb565 and 0x1F) / 31.0f

            if (writeOffset + 5 < vertexData.size) {
              vertexData[writeOffset] = worldX
              vertexData[writeOffset + 1] = worldY
              vertexData[writeOffset + 2] = worldZ
              vertexData[writeOffset + 3] = r
              vertexData[writeOffset + 4] = g
              vertexData[writeOffset + 5] = b
              writeOffset += 6
            }
          }
        }

        return PointCloud(
          vertexData = vertexData,
          pointCount = writeOffset / 6,
          timestamp = System.currentTimeMillis()
        )
      } catch (e: Exception) {
        Log.e(TAG, "Error assembling point cloud frame: ${e.message}", e)
        return null
      }
    }
  }
}
