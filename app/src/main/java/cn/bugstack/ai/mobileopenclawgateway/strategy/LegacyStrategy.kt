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

class LegacyStrategy(service: GatewayAccessibilityService) : BaseGatewayStrategy(service) {
    override fun performScreenshot(command: Command, onResult: (GatewayResponse) -> Unit) {
        // 使用 MediaProjection 进行截图
        ScreenCaptureManager.capture(service) { bitmap ->
            if (bitmap != null) {
                try {
                    val outputStream = ByteArrayOutputStream()
                    bitmap.compress(
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
                            "Screenshot taken via MediaProjection",
                            imageInfo
                        )
                    )
                    bitmap.recycle()
                } catch (e: Exception) {
                    Log.e("LegacyStrategy", "Screenshot processing error", e)
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
                        "Failed to capture screenshot (Permission denied or error)",
                        null
                    )
                )
            }
        }
    }
}
