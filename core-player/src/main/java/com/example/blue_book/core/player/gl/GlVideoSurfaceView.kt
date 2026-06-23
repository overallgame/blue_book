package com.example.blue_book.core.player.gl

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GlVideoSurfaceView(context: Context) : GLSurfaceView(context) {

    private val renderer = VideoRenderer(this)

    /** GL context 被重建（退后台/旋转等）后递增，GlSurfaceProvider 据此刷新 Surface */
    internal var contextGeneration: Int = 0
        private set

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun provideSurface(onReady: (Surface) -> Unit) {
        renderer.onSurfaceReady = onReady
    }

    fun releaseSurface() {
        renderer.release()
    }

    fun setFilter(type: FilterType) {
        renderer.setFilter(type)
        requestRender()
    }

    fun setIntensity(value: Float) {
        renderer.setIntensity(value)
        requestRender()
    }

    internal class VideoRenderer(
        private val view: GlVideoSurfaceView
    ) : Renderer, SurfaceTexture.OnFrameAvailableListener {
        private var oesTexId: Int = 0
        private var surfaceTexture: SurfaceTexture? = null
        private var surface: Surface? = null
        var onSurfaceReady: ((Surface) -> Unit)? = null

        private var program: Int = 0
        private var aPositionLoc: Int = 0
        private var aTexCoordLoc: Int = 0
        private var uTexTransformLoc: Int = 0
        private var uModeLoc: Int = 0
        private var uIntensityLoc: Int = 0

        private val vertexData: FloatBuffer = createFloatBuffer(floatArrayOf(
            -1f, -1f, 0f, 1f,
             1f, -1f, 1f, 1f,
            -1f,  1f, 0f, 0f,
             1f,  1f, 1f, 0f
        ))
        private val texMatrix = FloatArray(16)
        private var filterType: FilterType = FilterType.NONE
        private var intensity: Float = 1.0f

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            // 释放上一个 context 的 Surface / SurfaceTexture
            release()

            view.contextGeneration++
            oesTexId = createOesTexture()
            surfaceTexture = SurfaceTexture(oesTexId).apply {
                setOnFrameAvailableListener(this@VideoRenderer)
            }
            surface = Surface(surfaceTexture)
            onSurfaceReady?.invoke(surface!!)

            try {
                program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            } catch (e: RuntimeException) {
                Log.e("GlVideoSurfaceView", "Shader init failed, falling back", e)
                program = 0
                return
            }
            aPositionLoc = GLES20.glGetAttribLocation(program, "aPosition")
            aTexCoordLoc = GLES20.glGetAttribLocation(program, "aTexCoord")
            uTexTransformLoc = GLES20.glGetUniformLocation(program, "uTexTransform")
            uModeLoc = GLES20.glGetUniformLocation(program, "uMode")
            uIntensityLoc = GLES20.glGetUniformLocation(program, "uIntensity")
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            GLES20.glViewport(0, 0, width, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            if (program == 0) return
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            surfaceTexture?.let {
                it.updateTexImage()
                it.getTransformMatrix(texMatrix)
            }

            GLES20.glUseProgram(program)

            vertexData.position(0)
            GLES20.glEnableVertexAttribArray(aPositionLoc)
            GLES20.glVertexAttribPointer(aPositionLoc, 2, GLES20.GL_FLOAT, false, 16, vertexData)

            vertexData.position(2)
            GLES20.glEnableVertexAttribArray(aTexCoordLoc)
            GLES20.glVertexAttribPointer(aTexCoordLoc, 2, GLES20.GL_FLOAT, false, 16, vertexData)

            GLES20.glUniformMatrix4fv(uTexTransformLoc, 1, false, texMatrix, 0)
            val mode = when (filterType) { FilterType.GRAY -> 1; FilterType.WARM -> 2; else -> 0 }
            GLES20.glUniform1i(uModeLoc, mode)
            GLES20.glUniform1f(uIntensityLoc, intensity)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexId)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            checkGlError()
        }

        override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
            view.requestRender()
        }

        fun release() {
            // Surface 由此处独立管理，不二次释放
            surface?.release()
            surface = null
            surfaceTexture?.release()
            surfaceTexture = null
        }

        fun setFilter(type: FilterType) { filterType = type }
        fun setIntensity(v: Float) { intensity = v }

        private fun checkGlError() {
            val error = GLES20.glGetError()
            if (error != GLES20.GL_NO_ERROR) {
                Log.e("GlVideoSurfaceView", "GL error: ${GLUtils.getEGLErrorString(error)} ($error)")
            }
        }

        private fun createOesTexture(): Int {
            val tex = IntArray(1)
            GLES20.glGenTextures(1, tex, 0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex[0])
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            return tex[0]
        }

        private fun createProgram(vs: String, fs: String): Int {
            val v = loadShader(GLES20.GL_VERTEX_SHADER, vs)
            val f = loadShader(GLES20.GL_FRAGMENT_SHADER, fs)
            val p = GLES20.glCreateProgram()
            GLES20.glAttachShader(p, v)
            GLES20.glAttachShader(p, f)
            GLES20.glLinkProgram(p)
            val status = IntArray(1)
            GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, status, 0)
            if (status[0] == 0) {
                val msg = GLES20.glGetProgramInfoLog(p)
                GLES20.glDeleteProgram(p)
                throw RuntimeException("link program failed: $msg")
            }
            return p
        }

        private fun loadShader(type: Int, src: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, src)
            GLES20.glCompileShader(s)
            val compiled = IntArray(1)
            GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                val msg = GLES20.glGetShaderInfoLog(s)
                GLES20.glDeleteShader(s)
                throw RuntimeException("compile shader failed: $msg")
            }
            return s
        }

        private fun createFloatBuffer(data: FloatArray): FloatBuffer {
            val bb = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder())
            val fb = bb.asFloatBuffer()
            fb.put(data)
            fb.position(0)
            return fb
        }

        companion object {
            private const val VERTEX_SHADER = """
                attribute vec4 aPosition;
                attribute vec2 aTexCoord;
                uniform mat4 uTexTransform;
                varying vec2 vTexCoord;
                void main() {
                    gl_Position = aPosition;
                    vec4 tc = vec4(aTexCoord, 0.0, 1.0);
                    vTexCoord = (uTexTransform * tc).xy;
                }
            """

            private const val FRAGMENT_SHADER = """
                #extension GL_OES_EGL_image_external : require
                precision highp float;
                varying vec2 vTexCoord;
                uniform samplerExternalOES sTexture;
                uniform int uMode;
                uniform float uIntensity;
                void main() {
                    vec4 c = texture2D(sTexture, vTexCoord);
                    if (uMode == 1) {
                        float y = dot(c.rgb, vec3(0.299, 0.587, 0.114));
                        gl_FragColor = vec4(mix(c.rgb, vec3(y), clamp(uIntensity, 0.0, 1.0)), 1.0);
                    } else if (uMode == 2) {
                        vec3 warm = clamp(vec3(c.r + 0.1, c.g + 0.05, c.b - 0.05), 0.0, 1.0);
                        gl_FragColor = vec4(mix(c.rgb, warm, clamp(uIntensity, 0.0, 1.0)), 1.0);
                    } else {
                        gl_FragColor = c;
                    }
                }
            """
        }
    }
}
