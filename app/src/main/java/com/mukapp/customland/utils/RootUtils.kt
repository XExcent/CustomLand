package com.mukapp.customland.utils

import android.content.ComponentName
import android.content.Context
import com.dylanc.longan.logDebug
import com.dylanc.longan.logError
import java.io.BufferedReader
import java.io.InputStreamReader

/** Root 权限工具类 */
object RootUtils {

    /**
     * 检查设备是否拥有 Root 权限
     * 使用 "su -c id" 比 "id" 更准确，因为它验证了 su 二进制文件是否确实能提升权限
     */
    fun isRootAvailable(): Boolean {
        val result = executeCommand("su -c id")
        val hasRoot = result.output.contains("uid=0")
        logDebug("Root 检查结果: $hasRoot (output: ${result.output})")
        return hasRoot
    }

    /**
     * 请求 Root 权限
     */
    fun requestRootPermission(): Boolean {
        // 执行一个简单的 id 命令并退出，仅为了触发授权弹窗
        val result = executeCommand("su -c id")
        val hasRoot = result.exitCode == 0
        logDebug("Root 权限请求结果: $hasRoot")
        return hasRoot
    }

    /**
     * 通过 Root 权限开启无障碍服务
     */
    fun enableAccessibilityServiceByRoot(context: Context, serviceClass: Class<*>): Boolean {
        try {
            val componentName = ComponentName(context, serviceClass)
            val targetService = componentName.flattenToString()

            // 1. 获取当前启用的服务 (使用 su -c 一次性执行，避免交互式 Shell 的坑)
            val getResult =
                executeCommand("su -c settings get secure enabled_accessibility_services")

            // 处理不同的返回值情况：null, 空字符串, 或者具体的服务列表
            var currentServices = getResult.output.trim()
            if (currentServices == "null") currentServices = ""

            // 2. 检查是否已经包含
            if (currentServices.contains(targetService)) {
                logDebug("无障碍服务已在列表中，无需添加")
                // 即使在列表中，为了保险，有时也需要重新 enable 一下开关
                executeCommand("su -c settings put secure accessibility_enabled 1")
                return true
            }

            // 3. 构建新列表
            val newServices = if (currentServices.isEmpty()) {
                targetService
            } else {
                "$currentServices:$targetService"
            }

            // 4. 写入新配置并开启总开关
            // 为了保证原子性和效率，将多条命令合并为一条执行
            val cmd =
                "settings put secure enabled_accessibility_services '$newServices' && settings put secure accessibility_enabled 1"
            val setResult = executeCommand("su -c \"$cmd\"")

            val success = setResult.exitCode == 0
            logDebug("通过 Root 开启无障碍服务: $success (services: $newServices)")
            return success

        } catch (e: Exception) {
            logError("通过 Root 开启无障碍服务失败", e)
            return false
        }
    }

    /**
     * 执行 Root 命令 (对外暴露的通用方法)
     */
    // fun executeRootCommand(command: String): String? {
    //     val result = executeCommand(command)
    //     return if (result.exitCode == 0 || result.output.isNotEmpty()) {
    //         result.output
    //     } else {
    //         null
    //     }
    // }

    // --- 内部核心执行方法 ---

    data class CommandResult(val exitCode: Int, val output: String)

    /**
     * 核心执行方法：处理流合并，防止死锁
     */
    private fun executeCommand(command: String): CommandResult {
        var process: Process? = null
        return try {
            // 使用 su -c 来执行命令，这样可以正确处理包含空格、引号、管道符等的复杂命令
            // ProcessBuilder 需要将命令作为数组传入
            val pb = ProcessBuilder("su", "-c", command)
            pb.redirectErrorStream(true)
            process = pb.start()

            val output = StringBuilder()
            // 使用 use 自动关闭流
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }
            }

            val exitCode = process.waitFor()
            CommandResult(exitCode, output.toString().trim())
        } catch (e: Exception) {
            logError("命令执行异常: $command", e)
            CommandResult(-1, "")
        } finally {
            process?.destroy()
        }
    }
}