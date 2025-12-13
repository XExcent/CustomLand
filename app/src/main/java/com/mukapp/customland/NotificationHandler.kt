package com.mukapp.customland

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Bundle
import android.provider.Settings
import android.widget.RemoteViews
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
        // 自定义布局需要使用 miui.focus.param.custom 而不是 miui.focus.param
        val customParamsJson = createCustomParamsJson(result)
        logDebug("customParamsJson: $customParamsJson")
        builder.extras.putString("miui.focus.param.custom", customParamsJson)
        // 同时添加 ticker 用于状态栏显示
        builder.extras.putString("miui.focus.ticker", result.title)

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

        // 添加自定义布局
        builder.extras.putParcelable(
            "miui.focus.rv",
            createFocusRemoteViews(context, result, isDarkMode = false)
        )
        builder.extras.putParcelable(
            "miui.focus.rvNight",
            createFocusRemoteViews(context, result, isDarkMode = true)
        )
        // 超级岛展开状态布局（使用暗色模式）
        builder.extras.putParcelable(
            "miui.focus.rv.island.expand",
            createFocusRemoteViews(context, result, isDarkMode = true)
        )

        logDebug("创建 HyperOS Island 自定义通知成功")

        return builder.build()
    }

    /**
     * 创建自定义焦点通知参数 JSON（用于 miui.focus.param.custom） 注意：自定义 RemoteViews 需要使用此格式，直接是 param_v2
     * 的内容，无需外层包装
     */
    private fun createCustomParamsJson(result: RecognizerResult): String {
        val tickerText =
            if (result.content.isNotEmpty()) "${result.content} ${result.title}"
            else result.title

        val jsonObject = buildJsonObject {
            put("protocol", 1)
            put("enableFloat", true)
            put("updatable", true)
            put("isShowNotification", true)
            put("ticker", tickerText)
            put("tickerPic", "miui.focus.pic_icon_light")
            put("tickerPicDark", "miui.focus.pic_icon_dark")
            put("timeout", 999999)
            put("reopen", "reopen")

            // 息屏显示配置
            put("aodTitle", result.title)
            put("aodPic", "miui.focus.pic_icon_light")

            // 小米超级岛配置
            put(
                "param_island",
                buildJsonObject {
                    put("islandProperty", 1)
                    put("islandPriority", 2)
                    put("islandTimeout", 280)
                    put("dismissIsland", false)
                    put("needCloseAnimation", true)

                    // 未展开态（小岛/胶囊态）
                    put(
                        "smallIslandArea",
                        buildJsonObject {
                            put(
                                "picInfo",
                                buildJsonObject {
                                    put("type", 1)
                                    put("pic", "miui.focus.pic_icon_light")
                                }
                            )
                        }
                    )

                    // 展开态（大岛）
                    put(
                        "bigIslandArea",
                        buildJsonObject {
                            put(
                                "imageTextInfoLeft",
                                buildJsonObject {
                                    put("type", 1)
                                    if (result.content.isNotEmpty()) {
                                        put(
                                            "textInfo",
                                            buildJsonObject {
                                                put("title", result.content)
                                            }
                                        )
                                    }
                                    put(
                                        "picInfo",
                                        buildJsonObject {
                                            put("type", 1)
                                            put("pic", "miui.focus.pic_icon_light")
                                        }
                                    )
                                }
                            )
                            put(
                                "imageTextInfoRight",
                                buildJsonObject {
                                    put("type", 2)
                                    put(
                                        "textInfo",
                                        buildJsonObject {
                                            put("title", result.title)
                                        }
                                    )
                                }
                            )
                        }
                    )

                    // 分享数据
                    put(
                        "shareData",
                        buildJsonObject {
                            put("title", result.title)
                            put("pic", "miui.focus.pic_icon_light")
                            if (result.content.isNotEmpty()) {
                                put("content", result.content)
                                put("shareContent", "${result.content}：${result.title}")
                            } else {
                                put("shareContent", result.title)
                            }
                        }
                    )
                }
            )
        }

        return jsonObject.toString()
    }

    /** 创建普通焦点通知参数 JSON（用于 miui.focus.param，不使用自定义布局时） 保留此函数以备不使用自定义 RemoteViews 时使用 */
    fun createIslandParamsJson(result: RecognizerResult): String {
        val jsonObject = buildJsonObject {
            put(
                "param_v2",
                buildJsonObject {
                    put("protocol", 1)
                    put("enableFloat", true)
                    put("updatable", true)
                    put("reopen", "reopen")

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
                                        "imageTextInfoRight",
                                        buildJsonObject {
                                            put("type", 2)
                                            put(
                                                "textInfo",
                                                buildJsonObject {
                                                    put("title", result.title)
                                                }
                                            )
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

    /** 创建焦点通知 RemoteViews 自定义布局 */
    private fun createFocusRemoteViews(
        context: Context,
        result: RecognizerResult,
        isDarkMode: Boolean
    ): RemoteViews {
        val layoutId =
            if (isDarkMode) {
                R.layout.layout_focus_custom_night
            } else {
                R.layout.layout_focus_custom
            }

        return RemoteViews(context.packageName, layoutId).apply {
            // 设置图标
            setImageViewResource(R.id.ivIcon, R.drawable.wand_stars)

            // 上半部分：标签(content) + 主要内容(title)
            setTextViewText(R.id.tvLabel, result.content.ifEmpty { "识别结果" })
            setTextViewText(R.id.tvMainContent, result.title)

            // 下半部分：描述文字
            val description = buildString {
                if (result.infoTitle.isNotEmpty()) {
                    append(result.infoTitle)
                    if (result.infoContent.isNotEmpty()) {
                        append(" · ")
                        append(result.infoContent)
                    }
                }
                if (result.subInfoTitle.isNotEmpty()) {
                    if (isNotEmpty()) append("\n")
                    append(result.subInfoTitle)
                    if (result.subInfoContent.isNotEmpty()) {
                        append(" · ")
                        append(result.subInfoContent)
                    }
                }
            }
            setTextViewText(R.id.tvDescription, description.ifEmpty { "CustomLand" })

            // 按钮文字
            setTextViewText(R.id.btnAction, "查看")
        }
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
