package cn.bugstack.ai.mobileopenclawgateway.strategy

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Path
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import cn.bugstack.ai.mobileopenclawgateway.Command
import cn.bugstack.ai.mobileopenclawgateway.GatewayAccessibilityService
import cn.bugstack.ai.mobileopenclawgateway.GatewayResponse
import com.google.gson.Gson

abstract class BaseGatewayStrategy(protected val service: GatewayAccessibilityService) : GatewayStrategy {

    override fun performClick(command: Command, onResult: (GatewayResponse) -> Unit) {
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

        val success = service.dispatchGesture(gestureDescription, object : AccessibilityService.GestureResultCallback() {
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

    override fun performDoubleTap(command: Command, onResult: (GatewayResponse) -> Unit) {
        val params = command.params
        if (params == null) {
            onResult(GatewayResponse(command.id, "error", "Missing params", null))
            return
        }
        val x = (params["x"] as? Number)?.toFloat() ?: 0f
        val y = (params["y"] as? Number)?.toFloat() ?: 0f

        val builder = GestureDescription.Builder()
        val path1 = Path()
        path1.moveTo(x, y)
        builder.addStroke(GestureDescription.StrokeDescription(path1, 0, 50))
        
        val path2 = Path()
        path2.moveTo(x, y)
        builder.addStroke(GestureDescription.StrokeDescription(path2, 100, 50))

        val gestureDescription = builder.build()

        val success = service.dispatchGesture(gestureDescription, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                onResult(GatewayResponse(command.id, "success", "Double tap performed at $x, $y", null))
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                onResult(GatewayResponse(command.id, "error", "Double tap cancelled", null))
            }
        }, null)

        if (!success) {
            onResult(GatewayResponse(command.id, "error", "Failed to dispatch double tap gesture", null))
        }
    }

    override fun performLongPress(command: Command, onResult: (GatewayResponse) -> Unit) {
        val params = command.params
        if (params == null) {
            onResult(GatewayResponse(command.id, "error", "Missing params", null))
            return
        }
        val x = (params["x"] as? Number)?.toFloat() ?: 0f
        val y = (params["y"] as? Number)?.toFloat() ?: 0f
        val duration = (params["duration"] as? Number)?.toLong() ?: 1000L

        val path = Path()
        path.moveTo(x, y)
        val builder = GestureDescription.Builder()
        val gestureDescription = builder
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()

        val success = service.dispatchGesture(gestureDescription, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                onResult(GatewayResponse(command.id, "success", "Long press performed at $x, $y", null))
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                onResult(GatewayResponse(command.id, "error", "Long press cancelled", null))
            }
        }, null)

        if (!success) {
            onResult(GatewayResponse(command.id, "error", "Failed to dispatch long press gesture", null))
        }
    }

    override fun performSwipe(command: Command, onResult: (GatewayResponse) -> Unit) {
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

        val success = service.dispatchGesture(gestureDescription, object : AccessibilityService.GestureResultCallback() {
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

    override fun performInput(command: Command, onResult: (GatewayResponse) -> Unit) {
        val params = command.params
        if (params == null) {
            onResult(GatewayResponse(command.id, "error", "Missing params", null))
            return
        }
        val text = params["text"] as? String ?: ""

        val root = service.rootInActiveWindow
        if (root == null) {
            onResult(GatewayResponse(command.id, "error", "No active window root", null))
            return
        }

        val focusedNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: root.findFocus(
            AccessibilityNodeInfo.FOCUS_ACCESSIBILITY
        )

        if (focusedNode != null && focusedNode.isEditable) {
            val arguments = Bundle()
            arguments.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
            val success =
                focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            if (success) {
                onResult(GatewayResponse(command.id, "success", "Text input performed", null))
            } else {
                onResult(GatewayResponse(command.id, "error", "Failed to set text", null))
            }
        } else {
            onResult(GatewayResponse(command.id, "error", "No editable focused node found", null))
        }
    }

    override fun performOpenApp(command: Command, onResult: (GatewayResponse) -> Unit) {
        val params = command.params
        if (params == null) {
            onResult(GatewayResponse(command.id, "error", "Missing params", null))
            return
        }
        val packageName = params["package"] as? String ?: ""

        try {
            val intent = service.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                service.startActivity(intent)
                onResult(GatewayResponse(command.id, "success", "App launched: $packageName", null))
            } else {
                onResult(GatewayResponse(command.id, "error", "App not found: $packageName", null))
            }
        } catch (e: Exception) {
            onResult(
                GatewayResponse(
                    command.id,
                    "error",
                    "Failed to launch app: ${e.message}",
                    null
                )
            )
        }
    }

    override fun performGlobalAction(
        action: Int,
        command: Command,
        onResult: (GatewayResponse) -> Unit
    ) {
        Log.d("GatewayService", "Attempting global action: $action")
        val success = service.performGlobalAction(action)
        Log.d("GatewayService", "Global action $action result: $success")
        if (success) {
            onResult(
                GatewayResponse(
                    command.id,
                    "success",
                    "Global action $action performed",
                    null
                )
            )
        } else {
            onResult(
                GatewayResponse(
                    command.id,
                    "error",
                    "Failed to perform global action $action",
                    null
                )
            )
        }
    }

    override fun performGetInstalledApps(command: Command, onResult: (GatewayResponse) -> Unit) {
        try {
            val pm = service.packageManager
            val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
            val appList = ArrayList<Map<String, String>>()

            for (packageInfo in packages) {
                if ((packageInfo.applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM)) == 0) {
                    val appName = packageInfo.applicationInfo?.loadLabel(pm).toString()
                    val packageName = packageInfo.packageName

                    val appData = HashMap<String, String>()
                    appData["name"] = appName
                    appData["package"] = packageName
                    appList.add(appData)
                }
            }

            val jsonApps = Gson().toJson(appList)
            onResult(GatewayResponse(command.id, "success", "Installed apps retrieved: $jsonApps", null))

        } catch (e: Exception) {
            onResult(
                GatewayResponse(
                    command.id,
                    "error",
                    "Failed to get installed apps: ${e.message}",
                    null
                )
            )
        }
    }
}
