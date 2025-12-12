package com.mukapp.customland

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.drawable.Icon
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import com.dylanc.longan.logDebug
import com.dylanc.longan.logError
import com.mukapp.customland.Constants.MAX_HISTORY_SIZE
import com.mukapp.customland.Constants.PREF_HISTORY_JSON
import com.mukapp.customland.Constants.PREF_NOTIFICATION_HISTORY
import com.xzakota.hyper.notification.focus.FocusNotification
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** 通知处理器 根据你提供的PDF文档，检查是否为 HyperOS 3，并相应地发送岛通知或普通通知。 */
object NotificationHandler {

    private const val CHANNEL_ID = "default"
    private const val CHANNEL_NAME = "默认通知渠道"
    private val notificationIdCounter = AtomicInteger(0)

    // History list
    private val _history = mutableListOf<RecognizerResult>()
    val history: List<RecognizerResult>
        get() = _history

    // Callback for history updates
    var onHistoryUpdated: (() -> Unit)? = null

    fun sendNotification(
        context: Context,
        result: RecognizerResult,
        notificationId: Int? = null
    ): Int {
        // 只有新通知才添加到历史（notificationId == null 表示新通知）
        // 重新发送的通知（notificationId != null）不应该重复记录
        // 包括错误通知也会添加到历史记录
        if (notificationId != null) {
            _history.add(0, result) // Add to top
            // 限制历史记录数量
            if (_history.size > MAX_HISTORY_SIZE) {
                val removedItem = _history.removeAt(_history.size - 1)
                // 删除被移除项的截图
                deleteScreenshot(context, removedItem)
            }
            // 使用应用协程作用域替代 GlobalScope
            BaseApplication.getInstance().applicationScope.launch(Dispatchers.IO) {
                saveHistory(context)
            }
            onHistoryUpdated?.invoke()
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 确保通知渠道已创建
        createNotificationChannel(notificationManager)

        val notification: Notification =
            if (isHyperOS(context)) {
                // 是 HyperOS 3，构建岛通知
                buildHyperOsIslandNotification(context, result)
            } else {
                // 其他系统，构建标准通知
                buildStandardNotification(context, result)
            }

        if (notificationId != null) {
            notificationManager.notify(notificationId, notification)
            logDebug("已发送通知: $result")
            return notificationId
        } else {
            val notificationId = notificationIdCounter.incrementAndGet()
            notificationManager.notify(notificationId, notification)
            logDebug("已发送通知: $result")
            return notificationId
        }
    }

    fun sendStandardNotification(context: Context, title: String, content: String) {
        logDebug("直接发送普通通知")
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 确保通知渠道已创建
        createNotificationChannel(notificationManager)

        val builder =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.wand_stars)
                .setContentTitle(title)
                .setOngoing(true)
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
        notificationManager.notify(notificationIdCounter.incrementAndGet(), builder.build())
        logDebug("已发送通知")
    }

    fun cancelNotification(context: Context, notificationId: Int) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
    }

    /**
     * 返回值含义: 1: OS1 版本, 支持 OS1 版本焦点通知模板 2: OS2 版本, 支持 OS2 版本焦点通知模板 3: OS3 版本, 支持 OS3 版本小米超级岛通知模板
     */
    private fun isHyperOS(context: Context): Boolean {
        try {
            val focusProtocolVersion =
                Settings.System.getInt(
                    context.contentResolver,
                    "notification_focus_protocol",
                    0
                )
            return focusProtocolVersion != 0
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun createNotificationChannel(manager: NotificationManager) {
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                )
                    .apply {}
            manager.createNotificationChannel(channel)
        }
    }

    /** 构建通知文本 */
    private fun buildNotificationText(result: RecognizerResult): String = buildString {
        if (result.content.isNotEmpty()) {
            append(result.content)
        }
        if (result.infoTitle.isNotEmpty() && result.infoContent.isNotEmpty()) {
            if (isNotEmpty()) append("\n")
            append("${result.infoContent}：${result.infoTitle}")
        }
        if (result.subInfoTitle.isNotEmpty() && result.subInfoContent.isNotEmpty()) {
            if (isNotEmpty()) append("\n")
            append("${result.subInfoContent}：${result.subInfoTitle}")
        }
    }

    /** 构建标准通知 */
    private fun buildStandardNotification(
        context: Context,
        result: RecognizerResult
    ): Notification {
        logDebug("创建普通通知")
        val builder =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.wand_stars)
                .setContentTitle(result.title)
                .setOngoing(true)
        val notificationText = buildNotificationText(result)
        if (notificationText.isNotEmpty()) {
            builder.setContentText(notificationText)
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
        }
        return builder.build()
    }

    /** 构建 HyperOS 3 超级岛通知 */
    private fun buildHyperOsIslandNotification(
        context: Context,
        result: RecognizerResult
    ): Notification {
        // 使用 HyperNotification 库构建焦点通知
        val extras =
            FocusNotification.buildV3 {
                val iconLight =
                    createPicture(
                        "pic_icon_light",
                        Icon.createWithResource(context, R.drawable.wand_stars)
                            .setTint("#FFFFFF".toColorInt())
                    )
                val iconDark =
                    createPicture(
                        "pic_icon_dark",
                        Icon.createWithResource(context, R.drawable.wand_stars)
                            .setTint("#424242".toColorInt())
                    )

                enableFloat = true
                ticker =
                    if (result.content.isNotEmpty()) {
                        "${result.content} ${result.title}"
                    } else {
                        result.title
                    }
                tickerPic = iconLight

                // 基础信息
                if (result.content.isNotEmpty()) {
                    baseInfo {
                        type = 1
                        title = result.title
                        content = result.content
                        colorTitle = "#57A2DB"
                    }
                }

                // 提示信息
                if ((result.infoTitle.isNotEmpty() && result.infoContent.isNotEmpty()) ||
                    (result.subInfoTitle.isNotEmpty() &&
                            result.subInfoContent.isNotEmpty())
                ) {
                    hintInfo {
                        type = 2
                        if (result.infoTitle.isNotEmpty() && result.infoContent.isNotEmpty()) {
                            title = result.infoTitle
                            content = result.infoContent
                        }
                        if (result.subInfoTitle.isNotEmpty() &&
                            result.subInfoContent.isNotEmpty()
                        ) {
                            subTitle = result.subInfoTitle
                            subContent = result.subInfoContent
                        }
                    }
                }

                // 超级岛配置
                island {
                    islandProperty = 1
                    bigIslandArea {
                        imageTextInfoLeft {
                            type = 1
                            if (result.content.isNotEmpty()) {
                                textInfo { title = result.content }
                            }
                            picInfo {
                                type = 1
                                pic = iconLight
                            }
                        }
                        textInfo =
                            com.xzakota.hyper.notification.island.model.TextInfo().apply {
                                title = result.title
                            }
                    }
                    shareData {
                        title = result.title
                        if (result.content.isNotEmpty()) {
                            content = result.content
                            shareContent = "${result.content}：${result.title}"
                        } else {
                            shareContent = result.title
                        }
                    }
                }
            }

        // 构建通知
        val notificationText = buildNotificationText(result)
        val builder =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.wand_stars)
                .setContentTitle(result.title)
                .setOngoing(true)

        if (notificationText.isNotEmpty()) {
            builder.setContentText(notificationText)
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
        }

        builder.extras.putAll(extras)

        logDebug("创建 HyperOS Island 通知成功")
        return builder.build()
    }

    suspend fun saveHistory(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val prefs =
                    context.getSharedPreferences(
                        PREF_NOTIFICATION_HISTORY,
                        Context.MODE_PRIVATE
                    )
                // Limit to MAX_HISTORY_SIZE items
                val historyToSave = _history.take(MAX_HISTORY_SIZE)
                val jsonString = Json.encodeToString(historyToSave)
                prefs.edit { putString(PREF_HISTORY_JSON, jsonString) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun loadHistory(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val prefs =
                    context.getSharedPreferences(
                        PREF_NOTIFICATION_HISTORY,
                        Context.MODE_PRIVATE
                    )
                val jsonString = prefs.getString(PREF_HISTORY_JSON, "[]") ?: "[]"

                val loadedHistory = Json.decodeFromString<List<RecognizerResult>>(jsonString)

                withContext(Dispatchers.Main) {
                    _history.clear()
                    _history.addAll(loadedHistory)
                    onHistoryUpdated?.invoke()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** 删除历史记录中的指定项 */
    fun deleteHistoryItem(context: Context, item: RecognizerResult) {
        _history.remove(item)
        deleteScreenshot(context, item)
        BaseApplication.getInstance().applicationScope.launch(Dispatchers.IO) {
            saveHistory(context)
        }
        onHistoryUpdated?.invoke()
    }

    /** 删除截图文件 */
    private fun deleteScreenshot(context: Context, item: RecognizerResult) {
        item.screenshotPath?.let { path ->
            try {
                val file = java.io.File(context.filesDir, path)
                if (file.exists()) {
                    file.delete()
                    logDebug("已删除截图: ${file.absolutePath}")
                }
            } catch (e: Exception) {
                logError("删除截图失败", e)
            }
        }
    }
}
