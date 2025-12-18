package com.mukapp.customland

import com.mukapp.customland.utils.FixedPrefixPrinter
import android.app.Application
import com.dylanc.longan.initLogger
import com.mukapp.customland.common.MMKVHelper
import com.mukapp.customland.logic.NotificationHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class BaseApplication : Application() {

    // 应用级协程作用域，替代 GlobalScope
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        // 初始化 MMKV（必须在其他组件之前）
        MMKVHelper.init(this)

        // 初始化时注入我们写好的 Printer，并指定你想要的前缀
        initLogger(printer = FixedPrefixPrinter("[APP]"))

        // 初始化通知处理器（注册广播接收器）
        NotificationHandler.init(this)
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
