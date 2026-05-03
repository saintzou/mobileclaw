package cn.bugstack.ai.mobileopenclawgateway

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * saintzou
 */
object GatewayController {
    private var service: GatewayAccessibilityService? = null
    private val socketClient = SocketClient(::onCommandReceived)

    private val _connectionStatus = MutableStateFlow("Disconnected")
    val connectionStatus = _connectionStatus.asStateFlow()

    private val _logs = MutableStateFlow(listOf<String>())
    val logs = _logs.asStateFlow()

    fun setService(s: GatewayAccessibilityService?) {
        service = s
        log("Accessibility Service " + (if (s != null) "Connected" else "Disconnected"))
    }

    fun connect(host: String, port: Int) {
        _connectionStatus.value = "Connecting..."
        socketClient.connect(host, port,
            onConnected = {
                _connectionStatus.value = "Connected"
                log("Connected to $host:$port")
            },
            onError = { error ->
                _connectionStatus.value = "Error: $error"
                log("Connection Error: $error")
            }
        )
    }

    fun disconnect() {
        socketClient.disconnect()
        _connectionStatus.value = "Disconnected"
        log("Disconnected")
    }

    private fun onCommandReceived(command: Command) {
        log("Command: ${command.action}")
        if (service == null) {
            log("Error: Service not active")
            sendError(command.id, "Accessibility Service not active")
            return
        }

        service?.executeCommand(command) { response ->
            log("Result: ${response.status} - ${response.message}")
            socketClient.sendResponse(response)
        }
    }

    fun sendError(id: String, message: String) {
        socketClient.sendResponse(GatewayResponse(id, "error", message, null))
    }

    fun log(msg: String) {
        Log.d("GatewayController", msg)
        val current = _logs.value.toMutableList()
        if (current.size > 50) current.removeAt(0)
        current.add(msg)
        _logs.value = current
    }
}
