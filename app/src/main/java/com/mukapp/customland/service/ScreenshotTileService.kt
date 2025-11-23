package com.mukapp.customland.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.dylanc.longan.logDebug
import com.mukapp.customland.Constants.EXTRA_TARGET_PAGE
import com.mukapp.customland.Constants.TARGET_PAGE_SETTING
import com.mukapp.customland.MainActivity
import com.mukapp.customland.utils.isAccessibilityServiceEnabled

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
            // 服务未开启，跳转到 App 并提示
            runCatching {
                // 1. 统一创建 Intent
                val intent =
                    Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra(EXTRA_TARGET_PAGE, TARGET_PAGE_SETTING)
                    }

                // 2. 根据版本分发逻辑
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val pendingIntent =
                        PendingIntent.getActivity(
                            this,
                            0,
                            intent,
                            PendingIntent.FLAG_IMMUTABLE or
                                    PendingIntent.FLAG_UPDATE_CURRENT
                        )
                    startActivityAndCollapse(pendingIntent)
                } else {
                    @Suppress("DEPRECATION")
                    @SuppressLint("StartActivityAndCollapseDeprecated")
                    startActivityAndCollapse(intent)
                }
            }
            return
        }

        // 发送广播以触发无障碍服务中的截图
        val intent = Intent(ACTION_TAKE_SCREENSHOT).apply { setPackage(packageName) }
        sendBroadcast(intent)
    }

    /** 检查无障碍服务是否已启用 */
    private fun isAccessibilityServiceEnabled(): Boolean {
        return isAccessibilityServiceEnabled(ScreenshotAccessibilityService::class.java)
    }
}
