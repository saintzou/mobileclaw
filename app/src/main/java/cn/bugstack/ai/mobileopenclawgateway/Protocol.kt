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
    @SerializedName("image") val image: ImageInfo? = null
)

data class ImageInfo(
    @SerializedName("data") val data: String?, // base64 for screenshot
    @SerializedName("screenWidth") val screenWidth: Int,
    @SerializedName("screenHeight") val screenHeight: Int,
    @SerializedName("screenOrientation") val screenOrientation: Int,
)
