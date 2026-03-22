package cn.bugstack.ai.mobileopenclawgateway.strategy

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.util.Base64
import android.view.Display
import android.util.Log
import androidx.annotation.RequiresApi
import cn.bugstack.ai.mobileopenclawgateway.Command
import cn.bugstack.ai.mobileopenclawgateway.GatewayAccessibilityService
import cn.bugstack.ai.mobileopenclawgateway.GatewayResponse
import cn.bugstack.ai.mobileopenclawgateway.ImageInfo
import java.io.ByteArrayOutputStream

@RequiresApi(Build.VERSION_CODES.R)
class AndroidRStrategy(service: GatewayAccessibilityService) : BaseGatewayStrategy(service) {
    override fun performScreenshot(command: Command, onResult: (GatewayResponse) -> Unit) {
        performScreenshotInternal(command, onResult, 3)
    }

    private fun performScreenshotInternal(command: Command, onResult: (GatewayResponse) -> Unit, retryCount: Int) {
        val executor = service.mainExecutor
        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            executor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshotResult: AccessibilityService.ScreenshotResult) {
                    try {
                        val hardwareBuffer = screenshotResult.hardwareBuffer
                        val colorSpace = screenshotResult.colorSpace
                        val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)

                        if (bitmap != null) {
                            val softwareBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                            val outputStream = ByteArrayOutputStream()
                            softwareBitmap.compress(
                                Bitmap.CompressFormat.JPEG,
                                40,
                                outputStream
                            )
                            val byteArray = outputStream.toByteArray()
                            val base64String = Base64.encodeToString(byteArray, Base64.NO_WRAP)

                            val screenWidth = service.resources.displayMetrics.widthPixels
                            val screenHeight = service.resources.displayMetrics.heightPixels
                            val screenOrientation = service.resources.configuration.orientation
                            val imageInfo = ImageInfo(
                                base64String,
                                screenWidth,
                                screenHeight,
                                screenOrientation,
                            )

                            onResult(
                                GatewayResponse(
                                    command.id,
                                    "success",
                                    "Screenshot taken",
                                    imageInfo
                                )
                            )

                            softwareBitmap.recycle()
                            bitmap.recycle()
                        } else {
                            onResult(
                                GatewayResponse(
                                    command.id,
                                    "error",
                                    "Bitmap is null",
                                    null
                                )
                            )
                        }
                        hardwareBuffer.close()
                    } catch (e: Exception) {
                        Log.e("GatewayService", "Screenshot processing error", e)
                        if (retryCount > 0) {
                            Log.w("GatewayService", "Screenshot processing failed, retrying... ($retryCount left)")
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                performScreenshotInternal(command, onResult, retryCount - 1)
                            }, 500)
                        } else {
                            onResult(
                                GatewayResponse(
                                    command.id,
                                    "error",
                                    "Screenshot processing failed: ${e.message}",
                                    null
                                )
                            )
                        }
                    }
                }

                override fun onFailure(errorCode: Int) {
                    Log.e("GatewayService", "Screenshot capture failed with code: $errorCode")
                    if (retryCount > 0) {
                        Log.w("GatewayService", "Retrying screenshot... ($retryCount left)")
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            performScreenshotInternal(command, onResult, retryCount - 1)
                        }, 500)
                    } else {
                        onResult(
                            GatewayResponse(
                                command.id,
                                "error",
                                "Screenshot failed with error code: $errorCode",
                                null
                            )
                        )
                    }
                }
            }
        )
    }
}
