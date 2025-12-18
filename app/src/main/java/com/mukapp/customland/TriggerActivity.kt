package com.mukapp.customland

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.dylanc.longan.logDebug
import com.dylanc.longan.logError
import com.mukapp.customland.common.Constants.EXTRA_TARGET_PAGE
import com.mukapp.customland.common.Constants.PREF_ROOT_ENABLED
import com.mukapp.customland.common.Constants.TARGET_PAGE_SETTING
import com.mukapp.customland.common.MMKVHelper
import com.mukapp.customland.service.ACTION_TAKE_SCREENSHOT
import com.mukapp.customland.service.ScreenshotAccessibilityService
import com.mukapp.customland.ui.MainActivity
import com.mukapp.customland.utils.RootUtils
import com.mukapp.customland.utils.isAccessibilityServiceEnabled
import kotlin.concurrent.thread

/** 导出的触发器 Activity 其他应用可以通过 Intent 调起此 Activity 来触发截图识别 此 Activity 完全透明，执行完逻辑后立即关闭 */
class TriggerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logDebug("TriggerActivity 被调起")

        if (!isAccessibilityServiceEnabled(ScreenshotAccessibilityService::class.java)) {
            logDebug("无障碍服务未开启")

            // 检查是否启用了 Root 权限
            val isRootEnabled = MMKVHelper.getBoolean(PREF_ROOT_ENABLED, false)

            if (isRootEnabled) {
                logDebug("用户允许使用 Root，检查 Root 权限是否可用")
                // 在后台线程执行 Root 检查和恢复
                thread {
                    // 先检查 Root 权限是否实际可用
                    val hasRoot = RootUtils.isRootAvailable()
                    if (!hasRoot) {
                        logError("Root 权限已失效")
                        Handler(Looper.getMainLooper()).post {
                            android.widget.Toast.makeText(
                                this, R.string.toast_root_lost, android.widget.Toast.LENGTH_LONG
                            ).show()
                            navigateToSettings()
                        }
                        return@thread
                    }

                    // Root 权限可用，尝试恢复无障碍服务
                    val success = RootUtils.enableAccessibilityServiceByRoot(
                        this, ScreenshotAccessibilityService::class.java
                    )
                    if (success) {
                        logDebug("通过 Root 恢复无障碍服务成功，等待服务启动后发送截图广播")
                        // 等待无障碍服务启动（给系统一点时间）
                        Thread.sleep(500)
                        Handler(Looper.getMainLooper()).post {
                            // 再次检查服务是否已启用
                            if (isAccessibilityServiceEnabled(
                                    ScreenshotAccessibilityService::class.java
                                )
                            ) {
                                val intent = Intent(ACTION_TAKE_SCREENSHOT).apply {
                                    setPackage(packageName)
                                }
                                sendBroadcast(intent)
                                logDebug("截图广播已发送")
                            } else {
                                logError("无障碍服务恢复失败，跳转到设置页")
                                navigateToSettings()
                            }
                        }
                    } else {
                        logError("通过 Root 恢复无障碍服务失败，跳转到设置页")
                        Handler(Looper.getMainLooper()).post { navigateToSettings() }
                    }
                }
                // 不立即关闭，等上面的逻辑执行完
                return
            } else {
                logDebug("用户未允许使用 Root，跳转到设置页")
                navigateToSettings()
            }
        } else {
            // 发送广播以触发无障碍服务中的截图
            val intent = Intent(ACTION_TAKE_SCREENSHOT).apply { setPackage(packageName) }
            sendBroadcast(intent)
            logDebug("截图广播已发送")
        }

        // 立即关闭此 Activity
        finish()
    }

    private fun navigateToSettings() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(EXTRA_TARGET_PAGE, TARGET_PAGE_SETTING)
        }
        startActivity(intent)
        finish()
    }
}
