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
 * High-performance hardware-accelerated OpenGL ES 2.0 Point Cloud Renderer.
 * Renders anti-aliased 3D point spheres with perspective projection and full orbit camera navigation.
 */
class PointCloudRenderer : GLSurfaceView.Renderer {

  // Camera Orbit Parameters
  var rotationYaw: Float = 0f
  var rotationPitch: Float = 0f
  var cameraDistance: Float = 1.6f
  var pointSize: Float = 14f

  private val targetX = 0f
  private val targetY = 0f
  private val targetZ = -1.3f

  // Transformation Matrices
  private val projMatrix = FloatArray(16)
  private val viewMatrix = FloatArray(16)
  private val mvpMatrix = FloatArray(16)

  // Shader GL Handles
  private var programId = 0
  private var uMVPMatrixHandle = 0
  private var uPointSizeHandle = 0
  private var aPositionHandle = 0
  private var aColorHandle = 0

  // Point Cloud GPU Buffer (Holds up to 20,000 vertices: x, y, z, r, g, b)
  private val maxPoints = 20000
  private val vertexStride = 6 * 4 // 6 floats per vertex * 4 bytes per float = 24 bytes
  private val vertexBuffer: FloatBuffer = ByteBuffer
    .allocateDirect(maxPoints * vertexStride)
    .order(ByteOrder.nativeOrder())
    .asFloatBuffer()

  @Volatile
  private var activePointCount = 0

  /**
   * Updates the point cloud geometry with new 3D points and colors.
   */
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
    GLES20.glClearColor(0.015f, 0.025f, 0.06f, 1.0f)
    GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    GLES20.glDepthFunc(GLES20.GL_LEQUAL)

    // Compile Vertex Shader
    val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_CODE)
    // Compile Fragment Shader
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
        return
      }

      uMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
      uPointSizeHandle = GLES20.glGetUniformLocation(program, "uPointSize")
      aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
      aColorHandle = GLES20.glGetAttribLocation(program, "aColor")
    }

    Log.i(TAG, "PointCloudRenderer initialized successfully")
  }

  override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
    GLES20.glViewport(0, 0, width, height)
    val aspectRatio = width.toFloat() / height.coerceAtLeast(1).toFloat()
    Matrix.perspectiveM(projMatrix, 0, 48.0f, aspectRatio, 0.1f, 100.0f)
  }

  override fun onDrawFrame(gl: GL10?) {
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

    if (programId == 0 || activePointCount == 0) return

    GLES20.glUseProgram(programId)

    // 1. Calculate Orbit Camera View Matrix
    val pitchRad = Math.toRadians(rotationPitch.toDouble()).toFloat()
    val yawRad = Math.toRadians(rotationYaw.toDouble()).toFloat()

    val camX = targetX + cameraDistance * cos(pitchRad) * sin(yawRad)
    val camY = targetY + cameraDistance * sin(pitchRad)
    val camZ = targetZ + cameraDistance * cos(pitchRad) * cos(yawRad)

    Matrix.setLookAtM(viewMatrix, 0, camX, camY, camZ, targetX, targetY, targetZ, 0f, 1f, 0f)
    Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, viewMatrix, 0)

    // 2. Set Shader Uniforms
    GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvpMatrix, 0)
    GLES20.glUniform1f(uPointSizeHandle, pointSize)

    // 3. Bind Vertex Attributes (Interleaved: [X, Y, Z, R, G, B])
    synchronized(this) {
      vertexBuffer.position(0)
      GLES20.glEnableVertexAttribArray(aPositionHandle)
      GLES20.glVertexAttribPointer(
        aPositionHandle,
        3,
        GLES20.GL_FLOAT,
        false,
        vertexStride,
        vertexBuffer
      )

      vertexBuffer.position(3)
      GLES20.glEnableVertexAttribArray(aColorHandle)
      GLES20.glVertexAttribPointer(
        aColorHandle,
        3,
        GLES20.GL_FLOAT,
        false,
        vertexStride,
        vertexBuffer
      )

      // 4. Draw Point Cloud
      GLES20.glDrawArrays(GLES20.GL_POINTS, 0, activePointCount)

      GLES20.glDisableVertexAttribArray(aPositionHandle)
      GLES20.glDisableVertexAttribArray(aColorHandle)
    }
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
        vColor = aColor;
        vec4 pos = uMVPMatrix * vec4(aPosition, 1.0);
        gl_Position = pos;
        // Perspective point attenuation: Points closer to camera appear slightly larger
        gl_PointSize = clamp(uPointSize * (2.0 / max(pos.w, 0.1)), 4.0, 36.0);
      }
    """

    private const val FRAGMENT_SHADER_CODE = """
      precision mediump float;
      varying vec3 vColor;

      void main() {
        // Draw soft anti-aliased circular point spheres
        vec2 coord = gl_PointCoord - vec2(0.5);
        float distSq = dot(coord, coord);
        if (distSq > 0.25) {
          discard;
        }
        // Subtle volumetric radial lighting highlight
        float alpha = 1.0 - smoothstep(0.18, 0.25, distSq);
        vec3 litColor = vColor * (1.1 - distSq * 0.8);
        gl_FragColor = vec4(litColor, alpha);
      }
    """
  }
}
