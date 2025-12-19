package com.mukapp.customland.logic

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import com.dylanc.longan.logDebug
import com.dylanc.longan.logError
import com.mukapp.customland.BaseApplication
import com.mukapp.customland.R
import com.mukapp.customland.common.Constants
import com.mukapp.customland.common.MMKVHelper
import com.mukapp.customland.ui.DetailActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** 通知处理器 根据你提供的PDF文档，检查是否为 HyperOS 3，并相应地发送岛通知或普通通知。 */
object NotificationHandler {
    private const val CHANNEL_ID = "default"
    private const val CHANNEL_NAME = "默认通知渠道"
    private const val ACTION_DISMISS_NOTIFICATION =
        "com.mukapp.customland.ACTION_DISMISS_NOTIFICATION"
    private const val EXTRA_NOTIFICATION_ID = "notification_id"
    private val notificationIdCounter = AtomicInteger(0)

    // 广播接收器，用于处理按钮点击关闭通知
    private var dismissReceiver: BroadcastReceiver? = null

    // History list
    private val _history = mutableListOf<RecognizerResult>()
    val history: List<RecognizerResult>
        get() = _history

    // 标记历史记录是否已加载，防止未加载时覆盖旧数据
    private var historyLoaded = false

    // Callback for history updates
    var onHistoryUpdated: (() -> Unit)? = null

    /** 初始化广播接收器，用于处理按钮点击关闭通知 */
    fun init(context: Context) {
        if (dismissReceiver == null) {
            dismissReceiver =
                object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context, intent: Intent) {
                        if (intent.action == ACTION_DISMISS_NOTIFICATION) {
                            val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
                            if (notificationId != -1) {
                                cancelNotification(ctx, notificationId)
                                logDebug("按钮点击，关闭通知: $notificationId")
                            }
                        }
                    }
                }
            val filter = IntentFilter(ACTION_DISMISS_NOTIFICATION)
            ContextCompat.registerReceiver(
                context.applicationContext,
                dismissReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            logDebug("已注册通知关闭广播接收器")
        }
    }

    fun sendNotification(
        context: Context,
        result: RecognizerResult,
        notificationId: Int? = null
    ): Int {
        // 传入 notificationId 表示更新已有通知（最终识别结果），此时才添加到历史
        // 不传 notificationId 表示临时通知（如"识别中"），不添加历史
        if (notificationId != null) {
            // 确保在添加新记录前先加载已有历史，防止覆盖旧数据
            if (!historyLoaded) {
                kotlinx.coroutines.runBlocking { loadHistory(context) }
            }
            _history.add(0, result) // Add to top
            // 限制历史记录数量
            if (_history.size > Constants.MAX_HISTORY_SIZE) {
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

        // 先确定最终的 notificationId
        val finalNotificationId = notificationId ?: notificationIdCounter.incrementAndGet()

        val notification: Notification = buildNotification(context, result, finalNotificationId)
        // if (isHyperOS(context)) {
        // 是 HyperOS 3，构建岛通知
        // } else if (Build.VERSION.SDK_INT >= 36) {
        //     // Android 16 及以上，构建实时通知
        //     buildAndroid16LiveNotification(context, result)
        // } else {
        //     // 其他系统，构建标准通知
        //     buildStandardNotification(context, result)
        // }

        notificationManager.notify(finalNotificationId, notification)
        logDebug("已发送通知: $result")
        return finalNotificationId
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
    @Suppress("DEPRECATION")
    private fun buildNotificationText(result: RecognizerResult): String = buildString {
        if (result.content.isNotEmpty()) {
            append(result.content)
        }
        if (result.compatInfo.isNotEmpty()) {
            if (isNotEmpty()) append("\n")
            append(result.compatInfo)
        }
    }

    /** 构建标准通知 */
    private fun buildStandardNotification(
        context: Context,
        result: RecognizerResult
    ): Notification {
        logDebug("创建普通通知/实时通知")

        // 创建点击通知跳转到 DetailActivity 的 Intent
        val detailIntent =
            Intent(context, DetailActivity::class.java).apply {
                putExtra("result_id", result.id)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        val contentPendingIntent =
            PendingIntent.getActivity(
                context,
                result.id.hashCode(), // 使用 id 的 hashCode 作为 requestCode
                detailIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        val builder =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.wand_stars)
                .setLargeIcon(Icon.createWithResource(context, result.iconType.getIconRes()))
                .setContentTitle(result.title)
                .setSubText(result.content)
                .setShortCriticalText(result.title)
                .setOngoing(true)
                .setRequestPromotedOngoing(true)
                .setContentIntent(contentPendingIntent)

        if (result.info.isNotEmpty()) {
            builder.setContentText(result.info)
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(result.info))
        }

        return builder.build()
    }

    /** 构建通知 (HyperOS Island / Live Update / Standard) */
    private fun buildNotification(
        context: Context,
        result: RecognizerResult,
        notificationId: Int
    ): Notification {
        // 创建点击通知跳转到 DetailActivity 的 Intent
        val detailIntent =
            Intent(context, DetailActivity::class.java).apply {
                putExtra("result_id", result.id)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        val contentPendingIntent =
            PendingIntent.getActivity(
                context,
                result.id.hashCode(), // 使用 id 的 hashCode 作为 requestCode
                detailIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        val builder =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.wand_stars)
                .setLargeIcon(Icon.createWithResource(context, result.iconType.getIconRes()))
                .setContentTitle(result.title)
                .setSubText(result.content)
                .setShortCriticalText(result.title)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setRequestPromotedOngoing(true)
                .setContentIntent(contentPendingIntent)

        if (result.info.isNotEmpty()) {
            builder.setContentText(result.info)
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(result.info))
        }

        if (result.content.isNotEmpty()) {
            // 设置按钮点击事件，点击后关闭通知
            val dismissIntent =
                Intent(ACTION_DISMISS_NOTIFICATION).apply {
                    setPackage(context.packageName)
                    putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                }
            val actionPendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    notificationId, // 使用 notificationId 作为 requestCode 确保唯一性
                    dismissIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            builder.addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.check,
                    result.buttonText.ifEmpty { "已取" },
                    actionPendingIntent
                ).build()
            )
        }

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

        if (result.content.isNotEmpty()) {
            // 自定义布局需要使用 miui.focus.param.custom
            val customParamsJson = createCustomParamsJson(result)
            builder.extras.putString("miui.focus.param.custom", customParamsJson)
            // 同时添加 ticker 用于状态栏显示
            builder.extras.putString("miui.focus.ticker", result.title)

            // 添加自定义布局
            builder.extras.putParcelable(
                "miui.focus.rv",
                createFocusRemoteViews(context, result, isDarkMode = false, notificationId)
            )
            builder.extras.putParcelable(
                "miui.focus.rvNight",
                createFocusRemoteViews(context, result, isDarkMode = true, notificationId)
            )
            builder.extras.putParcelable(
                "miui.focus.rv.fullAod",
                createFocusRemoteViews(
                    context,
                    result,
                    isDarkMode = true,
                    notificationId,
                    showButton = false
                )
            )
            // 超级岛展开状态布局（使用暗色模式）
            builder.extras.putParcelable(
                "miui.focus.rv.island.expand",
                createFocusRemoteViews(context, result, isDarkMode = true, notificationId)
            )
        } else {
            builder.extras.putString("miui.focus.param", createIslandParamsJson(result))
        }

        logDebug("创建超级岛通知/实时通知/普通通知")

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
            // put("isShowNotification", true)
            put("ticker", tickerText)
            put("reopen", "reopen")

            // 息屏显示配置
            put("aodTitle", result.title)
            put("aodPic", "miui.focus.pic_icon_light")

            // 小米超级岛配置
            put(
                "param_island",
                buildJsonObject {
                    put("islandProperty", 1)
                    put("islandTimeout", 7200)
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

    /** 仅有岛的json */
    fun createIslandParamsJson(result: RecognizerResult): String {
        // 使用 buildJsonObject 动态构建
        val jsonObject = buildJsonObject {
            put(
                "param_v2",
                buildJsonObject {
                    put("protocol", 1)
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

    @Suppress("UNUSED_PARAMETER") // 保留 context 参数以保持 API 兼容
    suspend fun saveHistory(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                // Limit to MAX_HISTORY_SIZE items
                val historyToSave = _history.take(Constants.MAX_HISTORY_SIZE)
                val jsonString = Json.encodeToString(historyToSave)
                MMKVHelper.putHistoryJson(Constants.PREF_HISTORY_JSON, jsonString)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @Suppress("UNUSED_PARAMETER") // 保留 context 参数以保持 API 兼容
    suspend fun loadHistory(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val jsonString = MMKVHelper.getHistoryJson(Constants.PREF_HISTORY_JSON, "[]")

                val loadedHistory =
                    Json.decodeFromString<List<RecognizerResult>>(jsonString)

                withContext(Dispatchers.Main) {
                    _history.clear()
                    _history.addAll(loadedHistory)
                    historyLoaded = true // 标记已加载
                    onHistoryUpdated?.invoke()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                historyLoaded = true // 即使失败也标记为已加载，避免重复尝试
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
    @Suppress("DEPRECATION")
    private fun createFocusRemoteViews(
        context: Context,
        result: RecognizerResult,
        isDarkMode: Boolean,
        notificationId: Int,
        showButton: Boolean = true
    ): RemoteViews {
        val layoutId =
            if (isDarkMode) {
                R.layout.layout_focus_custom_night
            } else {
                R.layout.layout_focus_custom
            }

        return RemoteViews(context.packageName, layoutId).apply {
            // 设置图标（根据 iconType 动态选择）
            setImageViewResource(R.id.ivIcon, result.iconType.getIconRes())

            // 上半部分：标签(content) + 主要内容(title)
            setTextViewText(R.id.tvLabel, result.content.ifEmpty { "识别结果" })
            setTextViewText(R.id.tvMainContent, result.title)

            // 下半部分：描述文字（使用 compatInfo）
            setTextViewText(R.id.tvDescription, result.compatInfo.ifEmpty { "CustomLand" })

            if (showButton) {
                // 按钮文字（使用 AI 返回的 buttonText）
                setTextViewText(R.id.btnAction, result.buttonText.ifEmpty { "已取" })

                // 设置按钮点击事件，点击后关闭通知
                val dismissIntent =
                    Intent(ACTION_DISMISS_NOTIFICATION).apply {
                        setPackage(context.packageName)
                        putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                    }
                val pendingIntent =
                    PendingIntent.getBroadcast(
                        context,
                        notificationId, // 使用 notificationId 作为 requestCode 确保唯一性
                        dismissIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                setOnClickPendingIntent(R.id.btnAction, pendingIntent)
            } else {
                // 隐藏按钮
                setViewVisibility(R.id.btnAction, View.GONE)
            }
        }
    }

    /** 删除截图文件 */
    private fun deleteScreenshot(context: Context, item: RecognizerResult) {
        item.screenshotPath?.let { path ->
            try {
                val file = File(context.filesDir, path)
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