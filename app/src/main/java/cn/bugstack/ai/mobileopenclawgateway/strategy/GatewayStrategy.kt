package cn.bugstack.ai.mobileopenclawgateway.strategy

import cn.bugstack.ai.mobileopenclawgateway.Command
import cn.bugstack.ai.mobileopenclawgateway.GatewayResponse

interface GatewayStrategy {
    fun performClick(command: Command, onResult: (GatewayResponse) -> Unit)
    fun performDoubleTap(command: Command, onResult: (GatewayResponse) -> Unit)
    fun performLongPress(command: Command, onResult: (GatewayResponse) -> Unit)
    fun performSwipe(command: Command, onResult: (GatewayResponse) -> Unit)
    fun performInput(command: Command, onResult: (GatewayResponse) -> Unit)
    fun performOpenApp(command: Command, onResult: (GatewayResponse) -> Unit)
    fun performGlobalAction(action: Int, command: Command, onResult: (GatewayResponse) -> Unit)
    fun performGetInstalledApps(command: Command, onResult: (GatewayResponse) -> Unit)
    fun performScreenshot(command: Command, onResult: (GatewayResponse) -> Unit)
}
