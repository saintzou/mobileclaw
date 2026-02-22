package cn.bugstack.ai.mobileopenclawgateway

import com.google.gson.annotations.SerializedName

data class Command(
    @SerializedName("id") val id: String,
    @SerializedName("action") val action: String, // click, swipe, input, open, back, home, screenshot
    @SerializedName("params") val params: Map<String, Any>?
)

data class GatewayResponse(
    @SerializedName("id") val id: String,
    @SerializedName("status") val status: String, // success, error
    @SerializedName("message") val message: String?,
    @SerializedName("data") val data: String? // base64 for screenshot
)
