package cn.bugstack.ai.mobileopenclawgateway.strategy

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import cn.bugstack.ai.mobileopenclawgateway.Command
import cn.bugstack.ai.mobileopenclawgateway.GatewayAccessibilityService
import cn.bugstack.ai.mobileopenclawgateway.GatewayResponse
import cn.bugstack.ai.mobileopenclawgateway.ImageInfo
import cn.bugstack.ai.mobileopenclawgateway.util.ScreenCaptureManager
import java.io.ByteArrayOutputStream

class MediaProjectionStrategy(service: GatewayAccessibilityService) : BaseGatewayStrategy(service) {
    override fun performScreenshot(command: Command, onResult: (GatewayResponse) -> Unit) {
        // Check if recording is active, if not, it might trigger permission request or fail
        // Since we want robust behavior, we just call capture which handles re-connection if possible
        ScreenCaptureManager.capture(service) { bitmap ->
            if (bitmap != null) {
                try {
                    val outputStream = ByteArrayOutputStream()
                    bitmap.compress(
                        Bitmap.CompressFormat.JPEG,
                        20,
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
                            "Screenshot taken via MediaProjection (Real-time)",
                            imageInfo
                        )
                    )
                    bitmap.recycle()
                } catch (e: Exception) {
                    Log.e("MediaProjectionStrategy", "Screenshot processing error", e)
                    onResult(
                        GatewayResponse(
                            command.id,
                            "error",
                            "Screenshot processing failed: ${e.message}",
                            null
                        )
                    )
                }
            } else {
                onResult(
                    GatewayResponse(
                        command.id,
                        "error",
                        "Failed to capture screenshot. Please ensure Screen Recording is enabled in the app.",
                        null
                    )
                )
            }
        }
    }
}
