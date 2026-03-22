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
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import cn.bugstack.ai.mobileopenclawgateway.ScreenCaptureActivity
import java.lang.Exception
import java.util.ArrayList

object ScreenCaptureManager {
    private const val TAG = "ScreenCaptureManager"
    private var mediaProjection: MediaProjection? = null
    private var projectionResultCode: Int = 0
    private var projectionResultData: Intent? = null
    private var mediaProjectionManager: MediaProjectionManager? = null

    // 存储待执行的任务：Context 和 Callback
    private data class PendingCapture(val context: Context, val callback: (Bitmap?) -> Unit)
    private val pendingCaptures = ArrayList<PendingCapture>()
    
    fun setPermissionResult(code: Int, data: Intent?) {
        projectionResultCode = code
        projectionResultData = data
        if (code == Activity.RESULT_OK && data != null) {
            // 注意：这里需要一个Context来获取 MediaProjectionManager，
            // 但我们在 onActivityResult 里没有合适的 Context (除了 Activity 本身)。
            // 我们可以等到 processPendingCaptures 时再初始化 mediaProjection。
            processPendingCaptures()
        } else {
            // 权限被拒绝
            val callbacks = ArrayList(pendingCaptures)
            pendingCaptures.clear()
            callbacks.forEach { it.callback(null) }
        }
    }

    fun capture(context: Context, callback: (Bitmap?) -> Unit) {
        if (mediaProjection != null) {
            takeScreenshot(context, mediaProjection!!, callback)
            return
        }

        // 检查是否已有缓存的权限数据
        if (projectionResultCode == Activity.RESULT_OK && projectionResultData != null) {
             if (mediaProjectionManager == null) {
                mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            }
            try {
                mediaProjection = mediaProjectionManager?.getMediaProjection(projectionResultCode, projectionResultData!!)
                if (mediaProjection != null) {
                    takeScreenshot(context, mediaProjection!!, callback)
                    return
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create MediaProjection from cached result", e)
                // 可能是缓存的数据失效了，重新请求
                projectionResultCode = 0
                projectionResultData = null
                mediaProjection = null
            }
        }

        // 需要请求权限
        pendingCaptures.add(PendingCapture(context, callback))
        
        if (pendingCaptures.size == 1) { // 避免重复启动 Activity
            val intent = Intent(context, ScreenCaptureActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    private fun processPendingCaptures() {
        if (pendingCaptures.isEmpty()) return
        
        // 取出第一个任务的 Context 来初始化 MediaProjection
        val firstTask = pendingCaptures[0]
        val context = firstTask.context
        
        if (mediaProjectionManager == null) {
            mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        }

        if (projectionResultCode == Activity.RESULT_OK && projectionResultData != null) {
            mediaProjection = mediaProjectionManager?.getMediaProjection(projectionResultCode, projectionResultData!!)
        }

        if (mediaProjection != null) {
            val tasks = ArrayList(pendingCaptures)
            pendingCaptures.clear()
            tasks.forEach { task ->
                takeScreenshot(task.context, mediaProjection!!, task.callback)
            }
        } else {
             // 依然无法获取 MediaProjection，失败
            val tasks = ArrayList(pendingCaptures)
            pendingCaptures.clear()
            tasks.forEach { task ->
                task.callback(null)
            }
        }
    }

    private fun takeScreenshot(context: Context, projection: MediaProjection, callback: (Bitmap?) -> Unit) {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        
        val handler = Handler(Looper.getMainLooper())
        
        var virtualDisplay: VirtualDisplay? = null

        try {
            virtualDisplay = projection.createVirtualDisplay(
                "ScreenCapture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface,
                null,
                handler
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create VirtualDisplay", e)
            callback(null)
            return
        }

        // 使用 flag 确保只回调一次
        var callbackInvoked = false

        val timeoutRunnable = Runnable {
            if (!callbackInvoked) {
                callbackInvoked = true
                Log.e(TAG, "Screenshot timeout")
                try {
                    virtualDisplay?.release()
                    imageReader.close()
                } catch (e: Exception) {
                    // ignore
                }
                callback(null)
            }
        }
        
        // 设置 2 秒超时
        handler.postDelayed(timeoutRunnable, 2000)

        imageReader.setOnImageAvailableListener({ reader ->
            if (callbackInvoked) return@setOnImageAvailableListener
            
            try {
                val image = reader.acquireLatestImage()
                if (image != null) {
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
                    
                    image.close()
                    
                    // 成功获取到图片
                    callbackInvoked = true
                    handler.removeCallbacks(timeoutRunnable)
                    
                    try {
                        virtualDisplay?.release()
                        reader.close()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error closing resources", e)
                    }
                    
                    callback(finalBitmap)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing image", e)
                if (!callbackInvoked) {
                    callbackInvoked = true
                    handler.removeCallbacks(timeoutRunnable)
                    try {
                        virtualDisplay?.release()
                        reader.close()
                    } catch (closeEx: Exception) {
                        // ignore
                    }
                    callback(null)
                }
            }
        }, handler)
    }
}
