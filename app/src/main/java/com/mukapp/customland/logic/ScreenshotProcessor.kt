package com.mukapp.customland.logic

import android.content.Context
import android.graphics.Bitmap
import com.dylanc.longan.logDebug
import com.dylanc.longan.logError
import com.mukapp.customland.BaseApplication
import com.mukapp.customland.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Shared screenshot processing pipeline for both capture methods. */
object ScreenshotProcessor {

    fun processBitmap(context: Context, bitmap: Bitmap) {
        val appContext = context.applicationContext
        BaseApplication.getInstance().applicationScope.launch(Dispatchers.IO) {
            var notificationId: Int? = null
            var screenshotPath: String? = null
            var notificationUpdated = false

            try {
                screenshotPath = saveScreenshot(appContext, bitmap)
                notificationId =
                    NotificationHandler.sendNotification(
                        appContext,
                        RecognizerResult(title = appContext.getString(R.string.status_recognizing))
                    )
                val recognitionResult =
                    AiRecognizer.analyze(appContext, bitmap, screenshotPath)
                NotificationHandler.sendNotification(appContext, recognitionResult, notificationId)
                notificationUpdated = true
            } catch (e: Exception) {
                logError("识别失败", e)
                val errorResult =
                    RecognizerResult(
                        title = e::class.simpleName ?: "Exception",
                        content = "识别异常",
                        error = true,
                        errorMessage = e.message ?: e.toString(),
                        screenshotPath = screenshotPath
                    )
                if (notificationId != null) {
                    NotificationHandler.sendNotification(appContext, errorResult, notificationId)
                    notificationUpdated = true
                } else {
                    NotificationHandler.sendNotification(appContext, errorResult)
                    notificationUpdated = true
                }
            } finally {
                if (!notificationUpdated && notificationId != null) {
                    NotificationHandler.cancelNotification(appContext, notificationId)
                }
                bitmap.recycle()
            }
        }
    }

    private fun saveScreenshot(context: Context, bitmap: Bitmap): String {
        val screenshotsDir = File(context.filesDir, "screenshots")
        if (!screenshotsDir.exists()) {
            screenshotsDir.mkdirs()
        }

        val timestamp = System.currentTimeMillis()
        val filename = "screenshot_$timestamp.jpg"
        val file = File(screenshotsDir, filename)

        file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }

        logDebug("截图已保存：${file.absolutePath}")
        return "screenshots/$filename"
    }
}
