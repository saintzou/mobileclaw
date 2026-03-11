package cn.bugstack.ai.mobileopenclawgateway

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.bugstack.ai.mobileopenclawgateway.ui.theme.MobileOpenClawGatewayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("gateway_prefs", android.content.Context.MODE_PRIVATE)
        val savedHost = prefs.getString("host", "192.168.31.237") ?: "192.168.31.237"
        val savedPort = prefs.getString("port", "8777") ?: "8777"

        setContent {
            MobileOpenClawGatewayTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    GatewayApp(
                        modifier = Modifier.padding(innerPadding),
                        initialHost = savedHost,
                        initialPort = savedPort,
                        onOpenSettings = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        onSaveConfig = { host, port ->
                            prefs.edit().putString("host", host).putString("port", port).apply()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun GatewayApp(
    modifier: Modifier = Modifier,
    initialHost: String,
    initialPort: String,
    onOpenSettings: () -> Unit,
    onSaveConfig: (String, String) -> Unit
) {
    var host by remember { mutableStateOf(initialHost) }
    var port by remember { mutableStateOf(initialPort) }
    
    val connectionStatus by GatewayController.connectionStatus.collectAsState()
    val logs by GatewayController.logs.collectAsState()

    // Auto connect on launch
    LaunchedEffect(Unit) {
        if (connectionStatus == "Disconnected") {
            val p = port.toIntOrNull()
            if (p != null) {
                GatewayController.connect(host, p)
            }
        }
    }

    Column(modifier = modifier.padding(16.dp)) {
        Text("MobileClaw Gateway", style = MaterialTheme.typography.headlineMedium)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("Socket Host") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = port,
            onValueChange = { port = it },
            label = { Text("Socket Port") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { 
                    val p = port.toIntOrNull()
                    if (p != null) {
                        onSaveConfig(host, port)
                        GatewayController.connect(host, p) 
                    }
                },
                enabled = connectionStatus == "Disconnected" || connectionStatus.startsWith("Error")
            ) {
                Text("连接服务端")
            }
            
            Button(
                onClick = { GatewayController.disconnect() },
                enabled = connectionStatus == "Connected" || connectionStatus == "Connecting..."
            ) {
                Text("断开服务端")
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text("Status: $connectionStatus")
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text("打开辅助功能设置")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Logs:", style = MaterialTheme.typography.titleMedium)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            items(logs.reversed()) { log ->
                Text(log, style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
            }
        }
    }
}
