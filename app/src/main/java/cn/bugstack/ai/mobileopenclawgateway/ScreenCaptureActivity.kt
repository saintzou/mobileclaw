package cn.bugstack.ai.mobileopenclawgateway

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import cn.bugstack.ai.mobileopenclawgateway.util.ScreenCaptureManager

class ScreenCaptureActivity : Activity() {

    private val REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 透明 Activity，不需要 setContentView
        
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE) {
            ScreenCaptureManager.setPermissionResult(resultCode, data)
            finish()
        }
    }
}
