package com.mukapp.customland

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class BaseApplication : Application() {

    // 应用级协程作用域，替代 GlobalScope
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        // 初始化通知处理器（注册广播接收器）
        NotificationHandler.init(this)
        // val logConfig = LogConfig()
        // logConfig.logLevel = LogLevel.DEBUG
        // logConfig.tag="CustomLand_Log"
        // DevLogger.initialize(logConfig)
    }

    companion object {
        @Volatile
        private var instance: BaseApplication? = null

        fun getInstance(): BaseApplication {
            return instance ?: throw IllegalStateException("Application not initialized")
        }
    }

    init {
        instance = this
    }
}
