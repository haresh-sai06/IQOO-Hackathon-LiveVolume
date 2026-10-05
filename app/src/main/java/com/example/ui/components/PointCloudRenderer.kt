package com.example.ui.components

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

/**
 * OpenGL ES 2.0 point-cloud renderer with soft spherical points, optional
 * Shared Presence desk/floor grid, and orbit camera. Stays on GLES — no Filament.
 */
class PointCloudRenderer : GLSurfaceView.Renderer {

  // Camera Orbit Parameters
  @Volatile var rotationYaw: Float = 0f
  @Volatile var rotationPitch: Float = 12f
  @Volatile var cameraDistance: Float = 1.85f
  @Volatile var pointSize: Float = 18f

  /** When true, draw a virtual desk/floor grid under the cloud (preview, not AR). */
  @Volatile var sharedPresenceEnabled: Boolean = false

  /** Soft surface-ish fill: larger points + higher alpha falloff. */
  @Volatile var surfaceFill: Boolean = true

  private val targetX = 0f
  private val targetY = 0.15f
  private val targetZ = -1.25f

  // Desk plane sits slightly below the typical bust centroid
  private val deskY = -0.55f

  private val projMatrix = FloatArray(16)
  private val viewMatrix = FloatArray(16)
  private val mvpMatrix = FloatArray(16)

  private var programId = 0
  private var uMVPMatrixHandle = 0
  private var uPointSizeHandle = 0
  private var uSoftnessHandle = 0
  private var aPositionHandle = 0
  private var aColorHandle = 0

  private var gridProgramId = 0
  private var gridMvpHandle = 0
  private var gridColorHandle = 0
  private var gridPosHandle = 0

  private val maxPoints = 22000
  private val vertexStride = 6 * 4
  private val vertexBuffer: FloatBuffer = ByteBuffer
    .allocateDirect(maxPoints * vertexStride)
    .order(ByteOrder.nativeOrder())
    .asFloatBuffer()

  @Volatile
  private var activePointCount = 0

  private var gridVertexBuffer: FloatBuffer? = null
  private var gridVertexCount = 0

  fun updatePointCloud(data: FloatArray, count: Int) {
    val safeCount = count.coerceAtMost(maxPoints)
    synchronized(this) {
      vertexBuffer.position(0)
      vertexBuffer.put(data, 0, safeCount * 6)
      vertexBuffer.position(0)
      activePointCount = safeCount
    }
  }

  override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
    GLES20.glClearColor(0.02f, 0.035f, 0.08f, 1.0f)
    GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    GLES20.glDepthFunc(GLES20.GL_LEQUAL)
    GLES20.glEnable(GLES20.GL_BLEND)
    GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

    val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_CODE)
    val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_CODE)

    programId = GLES20.glCreateProgram().also { program ->
      GLES20.glAttachShader(program, vertexShader)
      GLES20.glAttachShader(program, fragmentShader)
      GLES20.glLinkProgram(program)

      val linkStatus = IntArray(1)
      GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
      if (linkStatus[0] == 0) {
        val error = GLES20.glGetProgramInfoLog(program)
        Log.e(TAG, "Failed linking Point Cloud shader program: $error")
        GLES20.glDeleteProgram(program)
        programId = 0
        return
      }

      uMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
      uPointSizeHandle = GLES20.glGetUniformLocation(program, "uPointSize")
      uSoftnessHandle = GLES20.glGetUniformLocation(program, "uSoftness")
      aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
      aColorHandle = GLES20.glGetAttribLocation(program, "aColor")
    }

    buildGridProgram()
    buildDeskGrid()

    Log.i(TAG, "PointCloudRenderer initialized (Shared Presence grid ready)")
  }

  private fun buildGridProgram() {
    val vs = loadShader(GLES20.GL_VERTEX_SHADER, GRID_VERTEX_SHADER)
    val fs = loadShader(GLES20.GL_FRAGMENT_SHADER, GRID_FRAGMENT_SHADER)
    gridProgramId = GLES20.glCreateProgram().also { program ->
      GLES20.glAttachShader(program, vs)
      GLES20.glAttachShader(program, fs)
      GLES20.glLinkProgram(program)
      val linkStatus = IntArray(1)
      GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
      if (linkStatus[0] == 0) {
        Log.e(TAG, "Grid shader link failed: ${GLES20.glGetProgramInfoLog(program)}")
        GLES20.glDeleteProgram(program)
        gridProgramId = 0
        return
      }
      gridMvpHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
      gridColorHandle = GLES20.glGetUniformLocation(program, "uColor")
      gridPosHandle = GLES20.glGetAttribLocation(program, "aPosition")
    }
  }

  private fun buildDeskGrid() {
    val half = 1.4f
    val steps = 10
    val verts = ArrayList<Float>((steps + 1) * 4 * 3 + 6)
    // Grid lines along Z
    for (i in 0..steps) {
      val t = -half + (2f * half * i / steps)
      verts.add(-half); verts.add(deskY); verts.add(t)
      verts.add(half); verts.add(deskY); verts.add(t)
    }
    // Grid lines along X
    for (i in 0..steps) {
      val t = -half + (2f * half * i / steps)
      verts.add(t); verts.add(deskY); verts.add(-half)
      verts.add(t); verts.add(deskY); verts.add(half)
    }
    // Desk plane outline (slightly brighter, drawn as same lines)
    val arr = verts.toFloatArray()
    gridVertexCount = arr.size / 3
    gridVertexBuffer = ByteBuffer
      .allocateDirect(arr.size * 4)
      .order(ByteOrder.nativeOrder())
      .asFloatBuffer()
      .put(arr)
      .apply { position(0) }
  }

  override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
    GLES20.glViewport(0, 0, width, height)
    val aspectRatio = width.toFloat() / height.coerceAtLeast(1).toFloat()
    Matrix.perspectiveM(projMatrix, 0, 46.0f, aspectRatio, 0.1f, 100.0f)
  }

  override fun onDrawFrame(gl: GL10?) {
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

    val pitchRad = Math.toRadians(rotationPitch.toDouble()).toFloat()
    val yawRad = Math.toRadians(rotationYaw.toDouble()).toFloat()

    val camX = targetX + cameraDistance * cos(pitchRad) * sin(yawRad)
    val camY = targetY + cameraDistance * sin(pitchRad)
    val camZ = targetZ + cameraDistance * cos(pitchRad) * cos(yawRad)

    Matrix.setLookAtM(viewMatrix, 0, camX, camY, camZ, targetX, targetY, targetZ, 0f, 1f, 0f)
    Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, viewMatrix, 0)

    if (sharedPresenceEnabled && gridProgramId != 0 && gridVertexBuffer != null) {
      drawDeskGrid()
    }

    if (programId == 0 || activePointCount == 0) return

    GLES20.glUseProgram(programId)
    GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvpMatrix, 0)
    val size = if (surfaceFill) pointSize * 1.25f else pointSize
    GLES20.glUniform1f(uPointSizeHandle, size)
    GLES20.glUniform1f(uSoftnessHandle, if (surfaceFill) 0.72f else 0.55f)

    synchronized(this) {
      vertexBuffer.position(0)
      GLES20.glEnableVertexAttribArray(aPositionHandle)
      GLES20.glVertexAttribPointer(
        aPositionHandle, 3, GLES20.GL_FLOAT, false, vertexStride, vertexBuffer
      )

      vertexBuffer.position(3)
      GLES20.glEnableVertexAttribArray(aColorHandle)
      GLES20.glVertexAttribPointer(
        aColorHandle, 3, GLES20.GL_FLOAT, false, vertexStride, vertexBuffer
      )

      GLES20.glDrawArrays(GLES20.GL_POINTS, 0, activePointCount)

      GLES20.glDisableVertexAttribArray(aPositionHandle)
      GLES20.glDisableVertexAttribArray(aColorHandle)
    }
  }

  private fun drawDeskGrid() {
    val buf = gridVertexBuffer ?: return
    GLES20.glUseProgram(gridProgramId)
    GLES20.glUniformMatrix4fv(gridMvpHandle, 1, false, mvpMatrix, 0)
    // Soft cyan desk lines — PPT “Shared Presence” emotion without ARCore
    GLES20.glUniform4f(gridColorHandle, 0.35f, 0.75f, 1.0f, 0.45f)
    buf.position(0)
    GLES20.glEnableVertexAttribArray(gridPosHandle)
    GLES20.glVertexAttribPointer(gridPosHandle, 3, GLES20.GL_FLOAT, false, 12, buf)
    GLES20.glLineWidth(1.5f)
    GLES20.glDrawArrays(GLES20.GL_LINES, 0, gridVertexCount)
    GLES20.glDisableVertexAttribArray(gridPosHandle)
  }

  private fun loadShader(type: Int, shaderCode: String): Int {
    return GLES20.glCreateShader(type).also { shader ->
      GLES20.glShaderSource(shader, shaderCode)
      GLES20.glCompileShader(shader)
      val compiled = IntArray(1)
      GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
      if (compiled[0] == 0) {
        val error = GLES20.glGetShaderInfoLog(shader)
        Log.e(TAG, "Shader compilation failed (type $type): $error")
        GLES20.glDeleteShader(shader)
      }
    }
  }

  companion object {
    private const val TAG = "PointCloudRenderer"

    private const val VERTEX_SHADER_CODE = """
      uniform mat4 uMVPMatrix;
      uniform float uPointSize;
      attribute vec3 aPosition;
      attribute vec3 aColor;
      varying vec3 vColor;

      void main() {
        // Mild saturation + lift so RGB from the camera reads as a denser bust
        float luma = dot(aColor, vec3(0.299, 0.587, 0.114));
        vec3 boosted = mix(vec3(luma), aColor, 1.35);
        vColor = clamp(boosted * 1.08, 0.0, 1.0);
        vec4 pos = uMVPMatrix * vec4(aPosition, 1.0);
        gl_Position = pos;
        gl_PointSize = clamp(uPointSize * (2.15 / max(pos.w, 0.12)), 5.0, 48.0);
      }
    """

    private const val FRAGMENT_SHADER_CODE = """
      precision mediump float;
      varying vec3 vColor;
      uniform float uSoftness;

      void main() {
        vec2 coord = gl_PointCoord - vec2(0.5);
        float distSq = dot(coord, coord);
        if (distSq > 0.25) discard;
        // Soft anti-aliased discs — denser points read as a soft surface
        float edge = mix(0.22, 0.16, uSoftness);
        float alpha = (1.0 - smoothstep(edge, 0.25, distSq)) * mix(0.85, 0.98, uSoftness);
        float highlight = 1.15 - distSq * 1.1;
        vec3 litColor = vColor * highlight;
        gl_FragColor = vec4(litColor, alpha);
      }
    """

    private const val GRID_VERTEX_SHADER = """
      uniform mat4 uMVPMatrix;
      attribute vec3 aPosition;
      void main() {
        gl_Position = uMVPMatrix * vec4(aPosition, 1.0);
      }
    """

    private const val GRID_FRAGMENT_SHADER = """
      precision mediump float;
      uniform vec4 uColor;
      void main() {
        gl_FragColor = uColor;
      }
    """
  }
}
