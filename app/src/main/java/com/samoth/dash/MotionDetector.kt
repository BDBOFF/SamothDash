package com.samoth.dash

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlin.math.abs

/**
 * Détecte un mouvement devant la tablette avec la caméra frontale, en très basse résolution.
 * Aucune image n'est conservée ni envoyée : seule la différence entre deux images est calculée.
 */
class MotionDetector(context: Context, private val onMotion: () -> Unit) {
    private val manager = context.getSystemService(CameraManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var thread: HandlerThread? = null
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var previous: IntArray? = null
    private var lastFrame = 0L
    private var hits = 0
    private var stopped = false

    @SuppressLint("MissingPermission")
    fun start() {
        try {
            val id = manager.cameraIdList.firstOrNull {
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            } ?: manager.cameraIdList.firstOrNull()
            if (id == null) {
                Log.i(TAG, "aucune caméra")
                return
            }
            val sizes = manager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(ImageFormat.YUV_420_888) ?: return
            val size = sizes.filter { it.width >= 160 }.minByOrNull { it.width * it.height } ?: sizes.first()
            val t = HandlerThread("motion").apply { start() }
            thread = t
            val handler = Handler(t.looper)
            val r = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 2)
            reader = r
            r.setOnImageAvailableListener({ source ->
                val image = runCatching { source.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
                try { process(image) } finally { image.close() }
            }, handler)
            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    if (stopped) { device.close(); return }
                    camera = device
                    startSession(device, r, handler)
                }
                override fun onDisconnected(device: CameraDevice) { device.close() }
                override fun onError(device: CameraDevice, error: Int) {
                    Log.i(TAG, "erreur caméra $error")
                    device.close()
                }
            }, handler)
            Log.i(TAG, "détection démarrée ${size.width}x${size.height}")
        } catch (e: Exception) {
            Log.i(TAG, "caméra indisponible : ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun startSession(device: CameraDevice, r: ImageReader, handler: Handler) {
        runCatching {
            device.createCaptureSession(listOf(r.surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    if (stopped) return
                    session = s
                    runCatching {
                        val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(r.surface)
                            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                        }.build()
                        s.setRepeatingRequest(request, null, handler)
                    }
                }
                override fun onConfigureFailed(s: CameraCaptureSession) {
                    Log.i(TAG, "configuration caméra impossible")
                }
            }, handler)
        }
    }

    /** Compare une grille de luminosité : un changement localisé sur 2 images de suite = mouvement. */
    private fun process(image: Image) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastFrame < FRAME_MS) return
        lastFrame = now
        val plane = image.planes[0]
        val buffer = plane.buffer
        val cells = IntArray(COLS * ROWS)
        val cw = image.width / COLS
        val ch = image.height / ROWS
        if (cw < 1 || ch < 1) return
        for (row in 0 until ROWS) for (col in 0 until COLS) {
            var sum = 0
            var count = 0
            var y = row * ch
            while (y < (row + 1) * ch) {
                var x = col * cw
                while (x < (col + 1) * cw) {
                    val i = y * plane.rowStride + x * plane.pixelStride
                    if (i < buffer.limit()) { sum += buffer.get(i).toInt() and 0xFF; count++ }
                    x += 4
                }
                y += 4
            }
            cells[row * COLS + col] = if (count > 0) sum / count else 0
        }
        val old = previous
        previous = cells
        if (old == null) return
        // L'exposition automatique décale toute l'image : on retire ce décalage moyen
        var shift = 0
        for (i in cells.indices) shift += cells[i] - old[i]
        shift /= cells.size
        var changed = 0
        for (i in cells.indices) if (abs(cells[i] - old[i] - shift) > CELL_DIFF) changed++
        hits = if (changed >= MIN_CELLS) hits + 1 else 0
        if (hits >= 2) {
            hits = 0
            Log.i(TAG, "mouvement ($changed cases)")
            main.post { if (!stopped) onMotion() }
        }
    }

    fun stop() {
        stopped = true
        runCatching { session?.close() }
        runCatching { camera?.close() }
        runCatching { reader?.close() }
        thread?.quitSafely()
        session = null
        camera = null
        reader = null
        thread = null
    }

    private companion object {
        const val TAG = "SamothDash"
        const val COLS = 16
        const val ROWS = 12
        const val FRAME_MS = 500L
        const val CELL_DIFF = 14
        const val MIN_CELLS = 6
    }
}
