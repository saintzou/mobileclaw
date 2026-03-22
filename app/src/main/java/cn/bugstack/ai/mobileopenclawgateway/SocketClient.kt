package cn.bugstack.ai.mobileopenclawgateway

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Socket
import java.util.concurrent.Executors

class SocketClient(private val onCommandReceived: (Command) -> Unit) {

    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()

    // Use a single thread for connection management to avoid race conditions
    private val connectionDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    fun connect(host: String, port: Int, onConnected: () -> Unit, onError: (String) -> Unit) {
        disconnect()
        job = scope.launch(connectionDispatcher) {
            while (isActive) {
                try {
                    Log.d("SocketClient", "Connecting to $host:$port")
                    socket = Socket(host, port)
                    writer = PrintWriter(OutputStreamWriter(socket!!.getOutputStream()), true)

                    withContext(Dispatchers.Main) {
                        onConnected()
                    }

                    val reader = InputStreamReader(socket!!.getInputStream())
                    val buffer = CharArray(4096)
                    val sb = StringBuilder()

                    while (isActive) {
                        val count = reader.read(buffer)
                        if (count < 0) break // End of stream

                        val receivedChunk = String(buffer, 0, count)
                        Log.d("SocketClient", "Raw received chunk: $receivedChunk")
                        sb.append(receivedChunk)

                        while (true) {
                            val newlineIndex = sb.indexOf('\n')
                            if (newlineIndex < 0) break

                            val line = sb.substring(0, newlineIndex).trim()
                            sb.delete(0, newlineIndex + 1) // Remove processed line and the newline char

                            if (line.isNotEmpty()) {
                                Log.d("SocketClient", "Processing line: $line")
                                try {
                                    val command = gson.fromJson(line, Command::class.java)
                                    withContext(Dispatchers.Main) {
                                        onCommandReceived(command)
                                    }
                                } catch (e: Exception) {
                                    Log.e("SocketClient", "Error parsing command: ${e.message}")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SocketClient", "Connection error: ${e.message}")
                    withContext(Dispatchers.Main) { onError(e.message ?: "Unknown error") }
                } finally {
                    try {
                        socket?.close()
                    } catch (e: Exception) {
                        // ignore
                    }
                    socket = null
                    writer = null
                }

                if (isActive) {
                    Log.d("SocketClient", "Reconnecting in 3 seconds...")
                    delay(3000)
                }
            }
        }
    }

    fun sendResponse(response: GatewayResponse) {
        scope.launch(Dispatchers.IO) {
            try {
                val json = gson.toJson(response)
                writer?.println(json)
                Log.d("SocketClient", "Sent: $json")
            } catch (e: Exception) {
                Log.e("SocketClient", "Error sending response: ${e.message}")
            }
        }
    }

    fun disconnect() {
        try {
            socket?.close()
        } catch (e: Exception) {
            // ignore
        }
        socket = null
        writer = null
        job?.cancel()
    }
}
