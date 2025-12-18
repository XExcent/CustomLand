package com.mukapp.customland.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.dylanc.longan.logDebug
import com.dylanc.longan.logError
import com.mukapp.customland.logic.AiRecognizer
import com.mukapp.customland.common.Constants.SCREENSHOT_DELAY_MS
import com.mukapp.customland.logic.NotificationHandler
import com.mukapp.customland.R
import com.mukapp.customland.logic.RecognizerResult
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// 广播 Action，用于从 TileService 触发
const val ACTION_TAKE_SCREENSHOT = "com.mukapp.customland.ACTION_TAKE_SCREENSHOT"

@SuppressLint("AccessibilityPolicy")
class ScreenshotAccessibilityService : AccessibilityService() {
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val screenshotReceiver =
        object : BroadcastReceiver() {
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
                            processImage(mutableBitmap)
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

    private fun processImage(bitmap: Bitmap) {
        // 启动协程执行AI任务和发送通知
        serviceScope.launch(Dispatchers.IO) {
            var notificationId: Int? = null
            var screenshotPath: String? = null

            try {
                // 保存截图到内部存储
                screenshotPath = saveScreenshot(bitmap)

                // 1. 调用AI识别 (占位符)
                notificationId =
                    NotificationHandler.sendNotification(
                        applicationContext,
                        RecognizerResult(title = getString(R.string.status_recognizing))
                    )
                val recognitionResult =
                    AiRecognizer.analyze(applicationContext, bitmap, screenshotPath)
                // 发送通知（无论成功还是失败）
                NotificationHandler.sendNotification(
                    applicationContext,
                    recognitionResult,
                    notificationId
                )
            } catch (e: Exception) {
                logError("识别失败", e)
                // 取消"识别中"通知
                notificationId?.let {
                    NotificationHandler.cancelNotification(applicationContext, it)
                }
                // 构建错误结果并发送通知
                val errorResult =
                    RecognizerResult(
                        title = e::class.simpleName ?: "Exception",
                        content = "识别异常",
                        error = true,
                        errorMessage = e.message ?: e.toString(),
                        screenshotPath = screenshotPath
                    )
                NotificationHandler.sendNotification(applicationContext, errorResult)
            } finally {
                // 释放Bitmap
                bitmap.recycle()
            }
        }
    }

    /** 保存截图到内部存储 */
    private fun saveScreenshot(bitmap: Bitmap): String {
        val screenshotsDir = File(applicationContext.filesDir, "screenshots")
        if (!screenshotsDir.exists()) {
            screenshotsDir.mkdirs()
        }

        val timestamp = System.currentTimeMillis()
        val filename = "screenshot_$timestamp.jpg"
        val file = File(screenshotsDir, filename)

        file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }

        logDebug("截图已保存：${file.absolutePath}")
        return "screenshots/$filename" // 返回相对路径
    }

    // 收起通知栏
    fun closeNotificationShade() {
        // 使用返回操作来关闭通知栏，因为它被证明是有效的
        val success = performGlobalAction(GLOBAL_ACTION_BACK)
        logDebug("执行返回操作来关闭通知栏，是否成功: $success")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 我们不需要监听事件，只提供截图功能
    }

    override fun onInterrupt() {
        // 服务被中断
    }

    override fun onDestroy() {
        // 取消所有协程并反注册接收器
        serviceJob.cancel()
        unregisterReceiver(screenshotReceiver)
        logDebug("辅助服务已销毁")
        super.onDestroy()
    }
}
