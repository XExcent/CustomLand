package com.mukapp.customland

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.dylanc.longan.logDebug
import com.mukapp.customland.Constants.EXTRA_TARGET_PAGE
import com.mukapp.customland.Constants.TARGET_PAGE_SETTING
import com.mukapp.customland.service.ACTION_TAKE_SCREENSHOT
import com.mukapp.customland.service.ScreenshotAccessibilityService
import com.mukapp.customland.utils.isAccessibilityServiceEnabled

/** 导出的触发器 Activity 其他应用可以通过 Intent 调起此 Activity 来触发截图识别 此 Activity 完全透明，执行完逻辑后立即关闭 */
class TriggerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logDebug("TriggerActivity 被调起")

        if (!isAccessibilityServiceEnabled(ScreenshotAccessibilityService::class.java)) {
            logDebug("无障碍服务未开启，跳转到设置页")
            // 服务未开启，跳转到 App 设置页并提示
            val intent =
                    Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra(EXTRA_TARGET_PAGE, TARGET_PAGE_SETTING)
                    }
            startActivity(intent)
        } else {
            // 发送广播以触发无障碍服务中的截图
            val intent = Intent(ACTION_TAKE_SCREENSHOT).apply { setPackage(packageName) }
            sendBroadcast(intent)
            logDebug("截图广播已发送")
        }

        // 立即关闭此 Activity
        finish()
    }
}
