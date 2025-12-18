package com.mukapp.customland.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.dylanc.longan.logDebug
import com.dylanc.longan.logError
import com.mukapp.customland.common.Constants.EXTRA_TARGET_PAGE
import com.mukapp.customland.common.Constants.PREF_ROOT_ENABLED
import com.mukapp.customland.common.Constants.TARGET_PAGE_SETTING
import com.mukapp.customland.common.MMKVHelper
import com.mukapp.customland.ui.MainActivity
import com.mukapp.customland.utils.RootUtils
import com.mukapp.customland.utils.isAccessibilityServiceEnabled
import kotlin.concurrent.thread

/** 快捷设置磁贴服务 点击后，发送广播命令 AccessibilityService 进行截图。 */
class ScreenshotTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        // 当磁贴可见时更新状态
        qsTile.state = Tile.STATE_INACTIVE // 默认设为非激活状态
        qsTile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        logDebug("磁贴点击")

        if (!isAccessibilityServiceEnabled()) {
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
                                this,
                                com.mukapp.customland.R.string.toast_root_lost,
                                android.widget.Toast.LENGTH_LONG
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
                            if (isAccessibilityServiceEnabled()) {
                                sendScreenshotBroadcast()
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
                return
            } else {
                logDebug("用户未允许使用 Root，跳转到设置页")
                navigateToSettings()
                return
            }
        }

        sendScreenshotBroadcast()
    }

    /** 发送截图广播 */
    private fun sendScreenshotBroadcast() {
        val intent = Intent(ACTION_TAKE_SCREENSHOT).apply {
            setPackage(packageName)
            putExtra("FROM_TILE", true) // 标记这是从磁贴触发的，需要关闭通知栏
        }
        sendBroadcast(intent)
        logDebug("截图广播已发送")
    }

    /** 跳转到设置页 */
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun navigateToSettings() {
        runCatching {
            // 1. 统一创建 Intent
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_TARGET_PAGE, TARGET_PAGE_SETTING)
            }

            // 2. 根据版本分发逻辑
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
    }

    /** 检查无障碍服务是否已启用 */
    private fun isAccessibilityServiceEnabled(): Boolean {
        return isAccessibilityServiceEnabled(ScreenshotAccessibilityService::class.java)
    }
}
