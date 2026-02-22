package cn.bugstack.ai.mobileopenclawgateway

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.ByteArrayOutputStream

class GatewayAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("GatewayService", "Service Connected")
        GatewayController.setService(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Minimal logging or handling if needed
    }

    override fun onInterrupt() {
        Log.d("GatewayService", "Service Interrupted")
        GatewayController.setService(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        GatewayController.setService(null)
    }

    fun executeCommand(command: Command, onResult: (GatewayResponse) -> Unit) {
        Log.d("GatewayService", "Executing command: ${command.action}")
        try {
            when (command.action) {
                "click" -> performClick(command, onResult)
                "swipe" -> performSwipe(command, onResult)
                "input" -> performInput(command, onResult)
                "open" -> performOpenApp(command, onResult)
                "back" -> performGlobalActionWrapped(GLOBAL_ACTION_BACK, command, onResult)
                "home" -> performGlobalActionWrapped(GLOBAL_ACTION_HOME, command, onResult)
                "recents" -> performGlobalActionWrapped(GLOBAL_ACTION_RECENTS, command, onResult)
                "screenshot" -> performScreenshot(command, onResult)
                else -> onResult(GatewayResponse(command.id, "error", "Unknown command: ${command.action}", null))
            }
        } catch (e: Exception) {
            Log.e("GatewayService", "Error executing command", e)
            onResult(GatewayResponse(command.id, "error", e.message, null))
        }
    }

    private fun performClick(command: Command, onResult: (GatewayResponse) -> Unit) {
        val params = command.params
        if (params == null) {
            onResult(GatewayResponse(command.id, "error", "Missing params", null))
            return
        }
        val x = (params["x"] as? Number)?.toFloat() ?: 0f
        val y = (params["y"] as? Number)?.toFloat() ?: 0f

        val path = Path()
        path.moveTo(x, y)
        val builder = GestureDescription.Builder()
        val gestureDescription = builder
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()

        val success = dispatchGesture(gestureDescription, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                onResult(GatewayResponse(command.id, "success", "Click performed at $x, $y", null))
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                onResult(GatewayResponse(command.id, "error", "Click cancelled", null))
            }
        }, null)
        
        if (!success) {
             onResult(GatewayResponse(command.id, "error", "Failed to dispatch click gesture", null))
        }
    }

    private fun performSwipe(command: Command, onResult: (GatewayResponse) -> Unit) {
        val params = command.params
        if (params == null) {
            onResult(GatewayResponse(command.id, "error", "Missing params", null))
            return
        }
        val x1 = (params["x1"] as? Number)?.toFloat() ?: 0f
        val y1 = (params["y1"] as? Number)?.toFloat() ?: 0f
        val x2 = (params["x2"] as? Number)?.toFloat() ?: 0f
        val y2 = (params["y2"] as? Number)?.toFloat() ?: 0f
        val duration = (params["duration"] as? Number)?.toLong() ?: 500L

        val path = Path()
        path.moveTo(x1, y1)
        path.lineTo(x2, y2)
        val builder = GestureDescription.Builder()
        val gestureDescription = builder
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()

        val success = dispatchGesture(gestureDescription, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                onResult(GatewayResponse(command.id, "success", "Swipe performed", null))
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                onResult(GatewayResponse(command.id, "error", "Swipe cancelled", null))
            }
        }, null)

        if (!success) {
            onResult(GatewayResponse(command.id, "error", "Failed to dispatch swipe gesture", null))
        }
    }

    private fun performInput(command: Command, onResult: (GatewayResponse) -> Unit) {
        val params = command.params
        if (params == null) {
            onResult(GatewayResponse(command.id, "error", "Missing params", null))
            return
        }
        val text = params["text"] as? String ?: ""

        val root = rootInActiveWindow
        if (root == null) {
            onResult(GatewayResponse(command.id, "error", "No active window root", null))
            return
        }

        val focusedNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        
        if (focusedNode != null && focusedNode.isEditable) {
            val arguments = Bundle()
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            val success = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            if (success) {
                onResult(GatewayResponse(command.id, "success", "Text input performed", null))
            } else {
                onResult(GatewayResponse(command.id, "error", "Failed to set text", null))
            }
        } else {
             onResult(GatewayResponse(command.id, "error", "No editable focused node found", null))
        }
    }

    private fun performOpenApp(command: Command, onResult: (GatewayResponse) -> Unit) {
        val params = command.params
        if (params == null) {
            onResult(GatewayResponse(command.id, "error", "Missing params", null))
            return
        }
        val packageName = params["package"] as? String ?: ""
        
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                onResult(GatewayResponse(command.id, "success", "App launched: $packageName", null))
            } else {
                onResult(GatewayResponse(command.id, "error", "App not found: $packageName", null))
            }
        } catch (e: Exception) {
            onResult(GatewayResponse(command.id, "error", "Failed to launch app: ${e.message}", null))
        }
    }

    private fun performGlobalActionWrapped(action: Int, command: Command, onResult: (GatewayResponse) -> Unit) {
        Log.d("GatewayService", "Attempting global action: $action")
        val success = performGlobalAction(action)
        Log.d("GatewayService", "Global action $action result: $success")
        if (success) {
            onResult(GatewayResponse(command.id, "success", "Global action $action performed", null))
        } else {
            onResult(GatewayResponse(command.id, "error", "Failed to perform global action $action", null))
        }
    }

    private fun performScreenshot(command: Command, onResult: (GatewayResponse) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val executor = mainExecutor
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                executor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: ScreenshotResult) {
                        try {
                            val hardwareBuffer = screenshotResult.hardwareBuffer
                            val colorSpace = screenshotResult.colorSpace
                            val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                            
                            if (bitmap != null) {
                                val softwareBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                                val outputStream = ByteArrayOutputStream()
                                softwareBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
                                val byteArray = outputStream.toByteArray()
                                val base64String = Base64.encodeToString(byteArray, Base64.NO_WRAP)
                                
                                onResult(GatewayResponse(command.id, "success", "Screenshot taken", base64String))
                                
                                softwareBitmap.recycle()
                                bitmap.recycle() 
                            } else {
                                onResult(GatewayResponse(command.id, "error", "Bitmap is null", null))
                            }
                            hardwareBuffer.close()
                        } catch (e: Exception) {
                            onResult(GatewayResponse(command.id, "error", "Screenshot processing failed: ${e.message}", null))
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        onResult(GatewayResponse(command.id, "error", "Screenshot failed with error code: $errorCode", null))
                    }
                }
            )
        } else {
             onResult(GatewayResponse(command.id, "error", "Screenshot requires Android 11+", null))
        }
    }
}
