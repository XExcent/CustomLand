package com.mukapp.customland.common

import android.content.Context
import android.content.SharedPreferences
import com.dylanc.longan.logDebug
import com.tencent.mmkv.MMKV

/**
 * MMKV 辅助类
 * 提供统一的键值存储接口，支持多进程访问
 */
object MMKVHelper {

    private const val MIGRATION_DONE_KEY = "mmkv_migration_done"

    // 默认 MMKV 实例（多进程模式）
    private val defaultMMKV: MMKV by lazy {
        MMKV.defaultMMKV(MMKV.MULTI_PROCESS_MODE, null)
    }

    // 历史记录专用 MMKV 实例
    private val historyMMKV: MMKV by lazy {
        MMKV.mmkvWithID(Constants.PREF_NOTIFICATION_HISTORY, MMKV.MULTI_PROCESS_MODE)
    }

    /**
     * 初始化 MMKV 并迁移旧数据
     * 在 Application.onCreate 中调用
     */
    fun init(context: Context) {
        val rootDir = MMKV.initialize(context)
        logDebug("MMKV 初始化完成，根目录: $rootDir")

        // 执行数据迁移
        migrateFromSharedPreferences(context)
    }

    /**
     * 从 SharedPreferences 迁移数据到 MMKV
     */
    private fun migrateFromSharedPreferences(context: Context) {
        // 检查是否已经迁移过
        if (defaultMMKV.decodeBool(MIGRATION_DONE_KEY, false)) {
            logDebug("MMKV 数据已迁移，跳过")
            return
        }

        logDebug("开始从 SharedPreferences 迁移数据到 MMKV")

        // 迁移 app_prefs
        val appPrefs = context.getSharedPreferences(
            Constants.PREF_APP_SETTINGS,
            Context.MODE_PRIVATE
        )
        migratePrefs(appPrefs, defaultMMKV)

        // 迁移 notification_history
        val historyPrefs = context.getSharedPreferences(
            Constants.PREF_NOTIFICATION_HISTORY,
            Context.MODE_PRIVATE
        )
        migratePrefs(historyPrefs, historyMMKV)

        // 删除旧的 SharedPreferences 文件
        deleteOldPrefs(context, Constants.PREF_APP_SETTINGS)
        deleteOldPrefs(context, Constants.PREF_NOTIFICATION_HISTORY)

        // 标记迁移完成
        defaultMMKV.encode(MIGRATION_DONE_KEY, true)
        logDebug("MMKV 数据迁移完成")
    }

    /**
     * 将 SharedPreferences 中的所有数据迁移到 MMKV
     */
    private fun migratePrefs(prefs: SharedPreferences, mmkv: MMKV) {
        val allEntries = prefs.all
        if (allEntries.isEmpty()) return

        for ((key, value) in allEntries) {
            when (value) {
                is String -> mmkv.encode(key, value)
                is Int -> mmkv.encode(key, value)
                is Boolean -> mmkv.encode(key, value)
                is Float -> mmkv.encode(key, value)
                is Long -> mmkv.encode(key, value)
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    mmkv.encode(key, value as Set<String>)
                }
            }
        }
        logDebug("已迁移 ${allEntries.size} 条数据")
    }

    /**
     * 删除旧的 SharedPreferences 文件
     */
    private fun deleteOldPrefs(context: Context, name: String) {
        try {
            val prefsFile = java.io.File(context.filesDir.parent, "shared_prefs/$name.xml")
            if (prefsFile.exists()) {
                prefsFile.delete()
                logDebug("已删除旧 SharedPreferences 文件: $name")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ========== 读取方法 ==========

    fun getString(key: String, defaultValue: String = ""): String {
        return defaultMMKV.decodeString(key, defaultValue) ?: defaultValue
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return defaultMMKV.decodeBool(key, defaultValue)
    }

    fun getInt(key: String, defaultValue: Int = 0): Int {
        return defaultMMKV.decodeInt(key, defaultValue)
    }

    // 历史记录专用
    fun getHistoryJson(key: String, defaultValue: String = "[]"): String {
        return historyMMKV.decodeString(key, defaultValue) ?: defaultValue
    }

    // ========== 写入方法 ==========

    fun putString(key: String, value: String) {
        defaultMMKV.encode(key, value)
    }

    fun putBoolean(key: String, value: Boolean) {
        defaultMMKV.encode(key, value)
    }

    fun putInt(key: String, value: Int) {
        defaultMMKV.encode(key, value)
    }

    // 历史记录专用
    fun putHistoryJson(key: String, value: String) {
        historyMMKV.encode(key, value)
    }

    fun remove(key: String) {
        defaultMMKV.removeValueForKey(key)
    }
}
