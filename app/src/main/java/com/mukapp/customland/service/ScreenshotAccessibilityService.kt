package com.mukapp.customland.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.dylanc.longan.logDebug
import com.dylanc.longan.logError
import com.mukapp.customland.common.Constants.SCREENSHOT_DELAY_MS
import com.mukapp.customland.logic.ScreenshotProcessor
import com.mukapp.customland.logic.NotificationHandler

// 广播 Action，用于从 TileService 触发
const val ACTION_TAKE_SCREENSHOT = "com.mukapp.customland.ACTION_TAKE_SCREENSHOT"

@SuppressLint("AccessibilityPolicy")
class ScreenshotAccessibilityService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())

    private val screenshotReceiver =
        object : BroadcastReceiver() {
            @RequiresPermission("android.permission.BROADCAST_CLOSE_SYSTEM_DIALOGS")
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == ACTION_TAKE_SCREENSHOT) {
                    logDebug("收到截图广播")

                    // 只有从磁贴触发时才关闭通知栏
                    val fromTile = intent.getBooleanExtra("FROM_TILE", false)
                    if (fromTile) {
                        closeNotificationShade()
                        mainHandler.postDelayed(
                            { takeScreenshotInternal() },
                            SCREENSHOT_DELAY_MS
                        )
                    } else {
                        // 其他方式触发，不需要延迟和关闭通知栏
                        takeScreenshotInternal()
                    }
                }
            }
        }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // 注册广播接收器
        val filter = IntentFilter(ACTION_TAKE_SCREENSHOT)

        // 修复：使用 ContextCompat.registerReceiver 并明确指定 NOT_EXPORTED
        ContextCompat.registerReceiver(
            this, // Context
            screenshotReceiver, // Receiver
            filter, // Filter
            ContextCompat.RECEIVER_NOT_EXPORTED // Flag
        )

        logDebug("辅助服务已连接且接收器已注册")
    }

    private fun takeScreenshotInternal() {
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainHandler::post, // 在主线程执行回调
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    logDebug("截图成功")
                    val buffer = screenshot.hardwareBuffer
                    val colorSpace = screenshot.colorSpace

                    var bitmap: Bitmap? = null
                    try {
                        // 将 HardwareBuffer 转换为 Bitmap
                        bitmap = Bitmap.wrapHardwareBuffer(buffer, colorSpace)
                        if (bitmap != null) {
                            // 复制一份，因为原始的 buffer 会被释放
                            val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                            ScreenshotProcessor.processBitmap(applicationContext, mutableBitmap)
                        } else {
                            logError("无法封装硬件缓冲区")
                        }
                    } catch (e: Exception) {
                        logError("位图转换失败", e)
                        NotificationHandler.sendStandardNotification(
                            applicationContext,
                            "截图失败",
                            e.message.toString()
                        )
                    } finally {
                        bitmap?.recycle()
                        buffer.close()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    logError("截图失败，错误代码：$errorCode")
                    NotificationHandler.sendStandardNotification(
                        applicationContext,
                        "截图失败",
                        "错误码: $errorCode"
                    )
                }
            }
        )
    }

    // 收起通知栏
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun closeNotificationShade() {
        // 需要 Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val success = performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            if (!success) {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        } else {
            // 降级处理，见方案二
            sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 我们不需要监听事件，只提供截图功能
    }

    override fun onInterrupt() {
        // 服务被中断
    }

    override fun onDestroy() {
        // 反注册接收器
        unregisterReceiver(screenshotReceiver)
        logDebug("辅助服务已销毁")
        super.onDestroy()
    }
}
