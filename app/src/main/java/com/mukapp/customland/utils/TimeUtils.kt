package com.mukapp.customland.utils

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 时间工具类 */
object TimeUtils {

    /**
     * 将时间戳转换为人性化时间格式
     * @param timestamp 时间戳（毫秒）
     * @param context 上下文，用于获取字符串资源
     * @return 人性化的时间字符串
     */
    fun formatHumanReadableTime(timestamp: Long, context: Context): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < 0 -> "未来" // 异常情况
            diff < 60_000 -> "刚刚" // 1分钟内
            diff < 3600_000 -> "${diff / 60_000}分钟前" // 1小时内
            diff < 86400_000 -> "${diff / 3600_000}小时前" // 24小时内
            diff < 172800_000 -> "昨天 ${formatTime(timestamp)}" // 48小时内显示"昨天"
            diff < 604800_000 -> "${diff / 86400_000}天前" // 7天内
            else -> formatDateTime(timestamp) // 超过7天显示完整日期
        }
    }

    /** 格式化为 HH:mm */
    private fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    /** 格式化为 MM-dd HH:mm */
    private fun formatDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
