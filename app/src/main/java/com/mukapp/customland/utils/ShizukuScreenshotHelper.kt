package com.mukapp.customland.utils

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.rikka.shizuku.Shizuku

object ShizukuScreenshotHelper {

    fun isShizukuAvailable(): Boolean = Shizuku.pingBinder()

    fun hasShizukuPermission(): Boolean =
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    fun captureScreenshot(): Result<Bitmap> {
        var process: Process? = null
        return try {
            process = Shizuku.newProcess(arrayOf("sh", "-c", "screencap -p"), null, null)
            val bitmap = process.inputStream.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
            val errorOutput = process.errorStream.bufferedReader().use { it.readText().trim() }
            val exitCode = process.waitFor()

            if (exitCode != 0 || bitmap == null) {
                val message =
                    if (errorOutput.isNotEmpty()) {
                        errorOutput
                    } else {
                        "screencap failed (exit=$exitCode)"
                    }
                Result.failure(IllegalStateException(message))
            } else {
                Result.success(bitmap)
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            process?.destroy()
        }
    }
}
