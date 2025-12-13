package com.mukapp.customland

import android.graphics.Bitmap
import android.util.Base64
import com.dylanc.longan.logDebug
import com.dylanc.longan.logError
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.json.JSONObject

object AiRecognizer {
    var api: String = "https://open.bigmodel.cn/api/paas/v4/chat/completions"
    var apikey: String = ""
    var model: String = "glm-4v-flash"

    /**
     * @param bitmap 待识别截图
     * @param screenshotPath 截图保存路径（可选）
     * @return 识别出的文字结果
     */
    suspend fun analyze(bitmap: Bitmap, screenshotPath: String? = null): RecognizerResult {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            var requestJson = ""
            var responseJson = ""

            try {
                logDebug("开始请求")

                val byteArrayOutputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
                val base64Image =
                    Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.NO_WRAP)

                val promptText =
                    """
                    # Role
                    你是一个专为“灵动岛”UI设计的各种截图信息提取专家。你的任务是从图片中提取关键信息，并将其转化为符合严格UI限制的结构化JSON数据。
                    
                    # Critical Constraints (最高优先级)
                    1. **输出格式**：仅输出纯 JSON 字符串。严禁使用 ```json 代码块、Markdown 标记或任何解释性文字。
                    2. **容错处理**：识别不到的非必填字段返回空字符串""
                    
                    # Extraction Rules (字段定义)
                    请根据以下逻辑提取并映射字段：
                    
                    ## 1. title (String, 必填)
                    * **定义**：用户完成线下动作所需的最核心凭证（如取餐码、取件码、座位号、登机口）。
                    * **特征**：通常是数字、字母组合，视觉上最醒目。
                    * **处理**：去除“取餐码”等前缀，只保留核心字符（如 "取餐码 A888" -> "A888"）。
                    
                    ## 2. content (String, 必填)
                    * **定义**：title 的简短描述标签。
                    * **限制**：严格控制在 2-4 个汉字（如：取餐码、快递柜、登机口、检票口）。
                    
                    ## 3. info (String, 选填)
                    * **定义**：辅助详情。
                    * **优先级**：核心商品/服务名 > 店铺/地点 > 备注/时间。
                    * **格式**：使用 `\n` 换行。最多 3 行，尽量 2 行。
                    * **长度控制**：每行不超过 12 个全角字符，太长可缩略（例如“肯德基（北京大学第三分店）” -> “肯德基(北大店)”）。
                    
                    ## 4. iconType (String, 枚举, 必填)
                    根据画面主体内容，精确匹配以下枚举值之一。若不确定，优先使用泛类（如 TAKEOUT_BAG）。
                    * **饮品类**：MILK_TEA (奶茶/果茶), COFFEE (咖啡)
                    * **主食类**：BURGER (汉堡/西餐), FRIED_CHICKEN (炸鸡/小食), RICE_BOWL (米饭/简餐), NOODLES (面条/粉/螺蛳粉), PIZZA (披萨)
                    * **甜品类**：DESSERT (甜品/冰淇淋), CAKE (蛋糕/面包), FRUIT (水果)
                    * **通用/物流**：TAKEOUT_BAG (无法区分具体食物/混合外卖), PACKAGE (快递包裹/物流), SHOPPING_BAG (商超购物)
                    * **默认**：RECEIPT (小票/排号单/其他)
                    
                    ## 5. buttonText (String, 必填)
                    * **逻辑**：
                        * 取餐/取件/核销 -> "已取"
                        * 排队/等位 -> "不等了"
                        * 通知/票务 -> "知道了"
                    * **限制**：2-3 个汉字。
                    
                    # Output Schema
                    {
                      "title": "String",
                      "content": "String",
                      "info": "String",
                      "iconType": "Enum String",
                      "buttonText": "String"
                    }
                    
                    # Few-Shot Examples
                    User Input: [肯德基截图: K555, 香辣鸡腿堡套餐, 北京大学北门店]
                    Assistant Output: {"title":"K555","content":"取餐码","info":"香辣鸡腿堡套餐\n肯德基(北大北门店)","iconType":"BURGER","buttonText":"已取"}
                    
                    User Input: [丰巢截图: 取件码 882299, 顺丰快递]
                    Assistant Output: {"title":"882299","content":"快递柜","info":"顺丰速运\n丰巢快递柜","iconType":"PACKAGE","buttonText":"已取"}
                """.trimIndent()

                // 2. 使用 DSL 构建 JSON
                val jsonObject = buildJsonObject {
                    put("model", model)
                    putJsonArray("messages") {
                        addJsonObject {
                            put("role", "user")
                            putJsonArray("content") {
                                // 文本部分
                                addJsonObject {
                                    put("type", "text")
                                    put("text", promptText)
                                }
                                // 图片部分
                                addJsonObject {
                                    put("type", "image_url")
                                    putJsonObject("image_url") {
                                        put("url", "data:image/jpeg;base64,$base64Image")
                                    }
                                }
                            }
                        }
                    }
                }

                val jsonInputString = jsonObject.toString()
                // 立即省略base64图片数据并保存到requestJson，确保即使后续出错也能记录
                requestJson = omitBase64FromJson(jsonInputString)

                // 建立网络连接（放在JSON构建之后，确保即使连接失败也有requestJson）
                val url = URL(api)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $apikey")
                connection.doOutput = true

                connection.outputStream.use { os ->
                    val input = jsonInputString.toByteArray(Charsets.UTF_8)
                    os.write(input, 0, input.size)
                }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response =
                        connection.inputStream.bufferedReader(Charsets.UTF_8).use {
                            it.readText()
                        }
                    responseJson = response

                    logDebug("请求成功，响应：$response")
                    val jsonResponse = JSONObject(response)
                    val jsonContent =
                        jsonResponse
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")

                    // 内容本身是一个JSON字符串，所以我们需要再次解析它。
                    // 它可能被包裹在 ```json ... ``` 中，所以让我们清理它。
                    val cleanedContent =
                        jsonContent.replace("```json", "").replace("```", "").trim()

                    val resultJson = JSONObject(cleanedContent)
                    val title = resultJson.optString("title")
                    val content = resultJson.optString("content")
                    val info = resultJson.optString("info")
                    val buttonText = resultJson.optString("buttonText", "已取")
                    val iconTypeStr = resultJson.optString("iconType", "RECEIPT")
                    val iconType =
                        try {
                            IconType.valueOf(iconTypeStr)
                        } catch (_: IllegalArgumentException) {
                            IconType.RECEIPT // 无法识别的默认使用 RECEIPT
                        }

                    val duration = System.currentTimeMillis() - startTime
                    val debugInfo =
                        DebugInfo(
                            requestJson = requestJson,
                            responseJson = responseJson,
                            durationMs = duration
                        )

                    if (title.isNotEmpty()) {
                        RecognizerResult(
                            title = title,
                            content = content,
                            info = info,
                            iconType = iconType,
                            buttonText = buttonText,
                            screenshotPath = screenshotPath,
                            debugInfo = debugInfo
                        )
                    } else {
                        RecognizerResult(
                            title = cleanedContent,
                            content = "识别失败",
                            error = true,
                            errorMessage = "AI返回的title为空",
                            screenshotPath = screenshotPath,
                            debugInfo = debugInfo
                        )
                    }
                } else {
                    val error =
                        connection.errorStream.bufferedReader(Charsets.UTF_8).use {
                            it.readText()
                        }
                    responseJson = error

                    logError("请求失败，响应码：$responseCode，错误：$error")

                    val duration = System.currentTimeMillis() - startTime
                    RecognizerResult(
                        title = "响应码: $responseCode",
                        content = "网络请求失败",
                        error = true,
                        errorMessage = error,
                        screenshotPath = screenshotPath,
                        debugInfo = DebugInfo(requestJson, responseJson, duration)
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                logError("请求出错", e)

                val duration = System.currentTimeMillis() - startTime
                RecognizerResult(
                    title = e::class.simpleName ?: "Exception",
                    content = "识别异常",
                    error = true,
                    errorMessage = e.message ?: e.toString(),
                    screenshotPath = screenshotPath,
                    debugInfo =
                        if (requestJson.isNotEmpty()) {
                            DebugInfo(requestJson, responseJson.ifEmpty { "无响应" }, duration)
                        } else null
                )
            }
        }
    }

    /** 省略JSON中的base64图片数据，避免显示时卡顿 */
    private fun omitBase64FromJson(json: String): String {
        // 1. (data:image.*?;base64,) -> 捕获组1：匹配头部，不管中间是 / 还是 \/，直到遇到 ;base64,
        // 2. ([^"]*) -> 捕获组2：匹配数据，贪婪地吃掉所有直到遇到下一个双引号字符
        val regex = Regex("""(data:image.*?;base64,)([^"]*)""")

        return regex.replace(json) { matchResult ->
            // 保留头部(组1)，替换数据部分(组2)
            "${matchResult.groupValues[1]}......(已省略Base64数据)......"
        }
    }
}

@Serializable
data class DebugInfo(val requestJson: String, val responseJson: String, val durationMs: Long)

/** 图标类型枚举 */
@Serializable
enum class IconType {
    MILK_TEA, // 奶茶
    COFFEE, // 咖啡
    PIZZA, // 披萨
    BURGER, // 汉堡套餐
    FRIED_CHICKEN, // 炸鸡套餐
    NOODLES, // 面
    RICE_BOWL, // 盖饭
    DESSERT, // 甜点
    CAKE, // 蛋糕
    FRUIT, // 水果
    PACKAGE, // 快递箱
    TAKEOUT_BAG, // 外卖袋（食物默认）
    SHOPPING_BAG, // 购物袋
    RECEIPT; // 小票（通用默认）

    /** 获取对应的图标资源 ID */
    fun getIconRes(): Int =
        when (this) {
            MILK_TEA -> R.drawable.ic_milk_tea
            COFFEE -> R.drawable.ic_coffee
            PIZZA -> R.drawable.ic_pizza
            BURGER -> R.drawable.ic_burger
            FRIED_CHICKEN -> R.drawable.ic_fried_chicken
            NOODLES -> R.drawable.ic_noodles
            RICE_BOWL -> R.drawable.ic_rice_bowl
            DESSERT -> R.drawable.ic_dessert
            CAKE -> R.drawable.ic_cake
            FRUIT -> R.drawable.ic_fruit
            PACKAGE -> R.drawable.ic_package
            TAKEOUT_BAG -> R.drawable.ic_takeout_bag
            SHOPPING_BAG -> R.drawable.ic_shopping_bag
            RECEIPT -> R.drawable.ic_receipt
        }
}

@Serializable
data class RecognizerResult(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val title: String,
    val content: String = "",
    val info: String = "", // 新字段：补充信息（支持多行）
    val iconType: IconType = IconType.RECEIPT, // 新字段：图标类型
    val buttonText: String = "完成", // 新字段：按钮文字
    val error: Boolean? = false,
    val errorMessage: String? = null, // 错误信息
    val screenshotPath: String? = null, // 截图保存路径
    val debugInfo: DebugInfo? = null, // 调试信息

    // 旧字段保留用于兼容（反序列化旧数据时使用）
    @Deprecated("使用 info 字段替代") val infoTitle: String = "",
    @Deprecated("使用 info 字段替代") val infoContent: String = "",
    @Deprecated("使用 info 字段替代") val subInfoTitle: String = "",
    @Deprecated("使用 info 字段替代") val subInfoContent: String = ""
) {
    /** 获取兼容的补充信息（优先使用新字段，否则从旧字段组合） */
    @Suppress("DEPRECATION")
    val compatInfo: String
        get() = if (info.isNotEmpty()) info else buildLegacyInfo()

    /** 从旧字段构建补充信息 */
    @Suppress("DEPRECATION")
    private fun buildLegacyInfo(): String = buildString {
        if (infoTitle.isNotEmpty()) {
            // if (infoContent.isNotEmpty()) append("$infoContent：")
            append(infoTitle)
        }
        if (subInfoTitle.isNotEmpty()) {
            if (isNotEmpty()) append("\n")
            // if (subInfoContent.isNotEmpty()) append("$subInfoContent：")
            append(subInfoTitle)
        }
    }
}
