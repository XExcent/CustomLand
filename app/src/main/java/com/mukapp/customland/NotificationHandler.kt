package com.mukapp.customland

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Bundle
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import com.dylanc.longan.logDebug
import com.dylanc.longan.logError
import com.mukapp.customland.Constants.MAX_HISTORY_SIZE
import com.mukapp.customland.Constants.PREF_HISTORY_JSON
import com.mukapp.customland.Constants.PREF_NOTIFICATION_HISTORY
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
        val notificationText = buildString {
            // 1. 添加 content，如果它不为空
            if (result.content.isNotEmpty()) {
                append(result.content)
            }
            // 2. 添加 infotitle 和 infocontent，仅当两者都不为空时
            if (result.infoTitle.isNotEmpty() && result.infoContent.isNotEmpty()) {
                if (isNotEmpty()) {
                    append("\n") // 如果前面有内容，则换行
                }
                append("${result.infoContent}：${result.infoTitle}")
            }
            // 3. 添加 subinfotitle 和 subinfocontent，仅当两者都不为空时
            if (result.subInfoTitle.isNotEmpty() && result.subInfoContent.isNotEmpty()) {
                if (isNotEmpty()) {
                    append("\n") // 如果前面有内容，则换行
                }
                append("${result.subInfoContent}：${result.subInfoTitle}")
            }
        }
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
        val builder =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.wand_stars)
                .setContentTitle(result.title)
                .setOngoing(true)
        val notificationText = buildString {
            // 1. 添加 content，如果它不为空
            if (result.content.isNotEmpty()) {
                append(result.content)
            }
            // 2. 添加 infotitle 和 infocontent，仅当两者都不为空时
            if (result.infoTitle.isNotEmpty() && result.infoContent.isNotEmpty()) {
                if (isNotEmpty()) {
                    append("\n") // 如果前面有内容，则换行
                }
                append("${result.infoContent}：${result.infoTitle}")
            }
            // 3. 添加 subinfotitle 和 subinfocontent，仅当两者都不为空时
            if (result.subInfoTitle.isNotEmpty() && result.subInfoContent.isNotEmpty()) {
                if (isNotEmpty()) {
                    append("\n") // 如果前面有内容，则换行
                }
                append("${result.subInfoContent}：${result.subInfoTitle}")
            }
        }
        if (notificationText.isNotEmpty()) {
            builder.setContentText(notificationText)
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
        }
        builder.extras.putString("miui.focus.param", createIslandParamsJson(result))

        builder.extras.putBundle(
            "miui.focus.pics",
            Bundle().apply {
                putParcelable(
                    "miui.focus.pic_icon_light",
                    Icon.createWithResource(context, R.drawable.wand_stars)
                        .setTint("#FFFFFF".toColorInt())
                )
                putParcelable(
                    "miui.focus.pic_icon_light_secondary",
                    Icon.createWithResource(context, R.drawable.wand_stars)
                        .setTint("#9E9E9E".toColorInt())
                )
                putParcelable(
                    "miui.focus.pic_icon_dark",
                    Icon.createWithResource(context, R.drawable.wand_stars)
                        .setTint("#424242".toColorInt())
                )
            }
        )

        logDebug("创建 HyperOS Island 通知成功")

        return builder.build()
    }

    fun createIslandParamsJson(result: RecognizerResult): String {
        // 使用 buildJsonObject 动态构建
        val jsonObject = buildJsonObject {
            put(
                "param_v2",
                buildJsonObject {
                    put("protocol", 1)
                    // put("business", "taxi")
                    put("enableFloat", true)
                    put("updatable", true)
                    put("reopen", "reopen")

                    // Ticker: 只有 text 不为空时才拼接
                    val tickerText =
                        if (result.content.isNotEmpty()) "${result.content} ${result.title}"
                        else result.title
                    put("ticker", tickerText)
                    put("aodTitle", result.title)
                    put("aodPic", "miui.focus.pic_icon_light")

                    put(
                        "param_island",
                        buildJsonObject {
                            put("islandProperty", 1)
                            put(
                                "bigIslandArea",
                                buildJsonObject {
                                    // 只有 text 不为空时才添加 imageTextInfoLeft
                                    put(
                                        "imageTextInfoLeft",
                                        buildJsonObject {
                                            put("type", 1)
                                            if (result.content.isNotEmpty()) {
                                                put(
                                                    "textInfo",
                                                    buildJsonObject {
                                                        put(
                                                            "title",
                                                            result.content
                                                        )
                                                    }
                                                )
                                            }
                                            put(
                                                "picInfo",
                                                buildJsonObject {
                                                    put("type", 1)
                                                    put(
                                                        "pic",
                                                        "miui.focus.pic_icon_light"
                                                    )
                                                }
                                            )
                                        }
                                    )
                                    put(
                                        "textInfo",
                                        buildJsonObject {
                                            put("title", result.title)
                                        }
                                    )
                                }
                            )
                            put(
                                "shareData",
                                buildJsonObject {
                                    put("title", result.title)
                                    if (result.content.isNotEmpty()) {
                                        put("content", result.content)
                                        put(
                                            "shareContent",
                                            "${result.content}：${result.title}"
                                        )
                                    } else {
                                        put("shareContent", result.title)
                                    }
                                }
                            )
                        }
                    )

                    if (result.content.isNotEmpty()) {
                        put(
                            "baseInfo",
                            buildJsonObject {
                                put("type", 1)
                                put("title", result.title)
                                put("content", result.content)
                                put("colorTitle", "#57A2DB")
                            }
                        )
                    }
                    if ((result.infoTitle.isNotEmpty() && result.infoContent.isNotEmpty()) ||
                        (result.subInfoTitle.isNotEmpty() &&
                                result.subInfoContent.isNotEmpty())
                    ) {
                        put(
                            "hintInfo",
                            buildJsonObject {
                                put("type", 2)
                                if (result.infoTitle.isNotEmpty() &&
                                    result.infoContent.isNotEmpty()
                                ) {
                                    put("title", result.infoTitle)
                                    put("content", result.infoContent)
                                }
                                if (result.subInfoTitle.isNotEmpty() &&
                                    result.subInfoContent.isNotEmpty()
                                ) {
                                    put("subTitle", result.subInfoTitle)
                                    put("subContent", result.subInfoContent)
                                }
                            }
                        )
                    }
                    put(
                        "picInfo",
                        buildJsonObject {
                            put("type", 1)
                            put("pic", "miui.focus.pic_icon_dark")
                            put("picDark", "miui.focus.pic_icon_light_secondary")
                        }
                    )
                }
            )
        }

        return jsonObject.toString()
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
