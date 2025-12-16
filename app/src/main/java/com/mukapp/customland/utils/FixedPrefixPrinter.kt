package com.mukapp.customland.utils

import android.util.Log
import com.dylanc.longan.LogLevel
import com.dylanc.longan.LoggerPrinter

// 自定义 Printer，接收一个前缀参数
class FixedPrefixPrinter(private val prefix: String = "[CustomLand]") : LoggerPrinter {
    override fun log(level: LogLevel, tag: String, message: String, thr: Throwable?) {
        // 核心修改：在这里拼接你的前缀
        val newTag = "$prefix$tag"

        // 根据库定义的 LogLevel 映射到 Android 原生 Log 等级
        val priority = when (level) {
            LogLevel.VERBOSE -> Log.VERBOSE
            LogLevel.DEBUG -> Log.DEBUG
            LogLevel.INFO -> Log.INFO
            LogLevel.WARN -> Log.WARN
            LogLevel.ERROR -> Log.ERROR
            else -> Log.DEBUG
        }

        // 执行真正的打印
        if (thr != null) {
            // 如果有异常对象，打印堆栈
            Log.println(priority, newTag, message + '\n' + Log.getStackTraceString(thr))
        } else {
            Log.println(priority, newTag, message)
        }
    }

    override fun logWtf(tag: String, message: String, thr: Throwable?) {
        val newTag = "$prefix$tag"
        if (thr != null) {
            Log.wtf(newTag, message, thr)
        } else {
            Log.wtf(newTag, message)
        }
    }
}