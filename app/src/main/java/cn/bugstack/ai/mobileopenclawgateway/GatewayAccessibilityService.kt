package cn.bugstack.ai.mobileopenclawgateway

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import cn.bugstack.ai.mobileopenclawgateway.strategy.AndroidRStrategy
import cn.bugstack.ai.mobileopenclawgateway.strategy.GatewayStrategy
import cn.bugstack.ai.mobileopenclawgateway.strategy.LegacyStrategy

class GatewayAccessibilityService : AccessibilityService() {

    private lateinit var strategy: GatewayStrategy

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("GatewayService", "Service Connected")
        
        strategy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            AndroidRStrategy(this)
        } else {
            LegacyStrategy(this)
        }
        
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
                "click" -> strategy.performClick(command, onResult)
                "double_tap" -> strategy.performDoubleTap(command, onResult)
                "long_press" -> strategy.performLongPress(command, onResult)
                "swipe" -> strategy.performSwipe(command, onResult)
                "input" -> strategy.performInput(command, onResult)
                "open" -> strategy.performOpenApp(command, onResult)
                "back" -> strategy.performGlobalAction(GLOBAL_ACTION_BACK, command, onResult)
                "home" -> strategy.performGlobalAction(GLOBAL_ACTION_HOME, command, onResult)
                "recents" -> strategy.performGlobalAction(GLOBAL_ACTION_RECENTS, command, onResult)
                "screenshot" -> strategy.performScreenshot(command, onResult)
                "apps" -> strategy.performGetInstalledApps(command, onResult)
                else -> onResult(
                    GatewayResponse(
                        command.id,
                        "error",
                        "Unknown command: ${command.action}",
                        null
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("GatewayService", "Error executing command", e)
            onResult(GatewayResponse(command.id, "error", e.message, null))
        }
    }
}
