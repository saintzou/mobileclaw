package cn.bugstack.ai.mobileopenclawgateway.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import cn.bugstack.ai.mobileopenclawgateway.MediaProjectionService
import cn.bugstack.ai.mobileopenclawgateway.ScreenCaptureActivity
import java.util.concurrent.CopyOnWriteArrayList

object ScreenCaptureManager {
    private const val TAG = "ScreenCaptureManager"

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var serviceIntent: Intent? = null

    // 缓存的权限结果
    private var projectionResultCode: Int = 0
    private var projectionResultData: Intent? = null

    // 等待截图的回调队列
    private val pendingCallbacks = CopyOnWriteArrayList<(Bitmap?) -> Unit>()

    // 缓存最新一帧
    @Volatile
    private var lastCapturedBitmap: Bitmap? = null

    // 后台线程处理截图
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    // 是否正在录制（服务已启动且 VirtualDisplay 已创建）
    var isRecording: Boolean = false
        private set

    fun requestPermission(context: Context) {
        val intent = Intent(context, ScreenCaptureActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun setPermissionResult(code: Int, data: Intent?) {
        projectionResultCode = code
        projectionResultData = data

        if (code == Activity.RESULT_OK && data != null) {
            // 权限已获取
        }
    }

    // 新增：由 Activity 调用以启动服务
    fun startService(context: Context, code: Int, data: Intent) {
        val intent = Intent(context, MediaProjectionService::class.java).apply {
            action = MediaProjectionService.ACTION_START
            putExtra(MediaProjectionService.EXTRA_RESULT_CODE, code)
            putExtra(MediaProjectionService.EXTRA_RESULT_DATA, data)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopService(context: Context) {
        val intent = Intent(context, MediaProjectionService::class.java).apply {
            action = MediaProjectionService.ACTION_STOP
        }
        context.startService(intent)
        stopRecording()
    }

    // 由 Service 调用
    fun onServiceStarted(context: Context, code: Int, data: Intent) {
        Log.d(TAG, "Service started, initializing MediaProjection")
        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        try {
            // 启动后台线程
            startBackgroundThread()

            mediaProjection = projectionManager.getMediaProjection(code, data)
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    stopRecording()
                }
            }, backgroundHandler)

            createVirtualDisplay(context)
            isRecording = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create MediaProjection", e)
            isRecording = false
        }
    }

    private fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("ScreenCaptureThread")
            backgroundThread?.start()
            backgroundHandler = Handler(backgroundThread!!.looper)
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Interrupted while stopping background thread", e)
        }
    }

    private fun createVirtualDisplay(context: Context) {
        if (mediaProjection == null) return

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        // 使用 2 个缓冲区的 ImageReader
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            backgroundHandler
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            try {
                // 获取最新图片
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener

                try {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width

                    val bitmap = Bitmap.createBitmap(
                        width + rowPadding / pixelStride,
                        height,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)

                    val finalBitmap = if (rowPadding == 0) {
                        bitmap
                    } else {
                        val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                        bitmap.recycle()
                        cropped
                    }

                    // 更新缓存
                    synchronized(this) {
                        lastCapturedBitmap?.recycle()
                        lastCapturedBitmap = finalBitmap
                    }

                    // 如果有等待的回调，立即分发
                    if (pendingCallbacks.isNotEmpty()) {
                        val callbacksToNotify = ArrayList(pendingCallbacks)
                        pendingCallbacks.clear()

                        // 由于 Bitmap 可能在下一次更新被 recycle，这里传递副本或者确保同步
                        // 简单起见，传递副本比较安全，或者直接在 synchronized 块中处理
                        // 为了性能，我们传递当前 Bitmap 的副本
                        val bitmapToSend = finalBitmap.copy(Bitmap.Config.ARGB_8888, false)

                        // 回调到主线程或者当前线程？通常 callback 会处理耗时操作，建议在后台线程
                        callbacksToNotify.forEach { it(bitmapToSend) }
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error converting image", e)
                } finally {
                    image.close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onImageAvailable", e)
            }
        }, backgroundHandler)
    }

    private fun stopRecording() {
        isRecording = false
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
            mediaProjection?.stop()
            mediaProjection = null

            synchronized(this) {
                lastCapturedBitmap?.recycle()
                lastCapturedBitmap = null
            }

            stopBackgroundThread()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        }

        // 清理等待的回调
        if (pendingCallbacks.isNotEmpty()) {
            val callbacks = ArrayList(pendingCallbacks)
            pendingCallbacks.clear()
            callbacks.forEach { it(null) }
        }
    }

    fun capture(context: Context, callback: (Bitmap?) -> Unit) {
        if (isRecording) {
            // 1. 尝试直接从缓存获取
            var cached: Bitmap? = null
            synchronized(this) {
                if (lastCapturedBitmap != null && !lastCapturedBitmap!!.isRecycled) {
                    cached = lastCapturedBitmap!!.copy(Bitmap.Config.ARGB_8888, false)
                }
            }

            if (cached != null) {
                // 立即回调
                callback(cached)
            } else {
                // 缓存为空（刚启动或出错），加入队列等待下一帧
                pendingCallbacks.add(callback)
            }
        } else {
            // 未开始录制，先尝试启动（如果有权限）
            if (projectionResultCode == Activity.RESULT_OK && projectionResultData != null) {
                // 有权限缓存，尝试启动服务
                startService(context, projectionResultCode, projectionResultData!!)

                pendingCallbacks.add(callback)

                // 设置一个超时，以防服务启动失败
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!isRecording && pendingCallbacks.contains(callback)) {
                        pendingCallbacks.remove(callback)
                        callback(null)
                    }
                }, 3000)
            } else {
                Log.e(TAG, "Capture requested but no recording active and no permission")
                callback(null)
                requestPermission(context)
            }
        }
    }
}
