package com.mukapp.customland.common

/** 应用常量 */
object Constants {
    // 延迟时间
    const val SCREENSHOT_DELAY_MS = 500L

    // 历史记录限制
    const val MAX_HISTORY_SIZE = 50

    // SharedPreferences 键名
    const val PREF_NOTIFICATION_HISTORY = "notification_history"
    const val PREF_HISTORY_JSON = "history_json"
    const val PREF_APP_SETTINGS = "app_prefs"
    const val PREF_API_ADDRESS = "api_address"
    const val PREF_API_KEY = "api_key"
    const val PREF_MODEL_NAME = "model_name"
    const val PREF_ROOT_ENABLED = "root_enabled"
    const val PREF_HIDE_FROM_RECENTS = "hide_from_recents"

    // Intent Extra
    const val EXTRA_TARGET_PAGE = "EXTRA_TARGET_PAGE"
    const val TARGET_PAGE_SETTING = "setting"
}
