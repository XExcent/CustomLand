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
     * @param bitmap The screenshot to be recognized
     * @return The recognized text result
     */
    suspend fun analyze(bitmap: Bitmap): RecognizerResult {
        return withContext(Dispatchers.IO) {
            try {
                logDebug("开始请求")

                val byteArrayOutputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
                val base64Image =
                    Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.NO_WRAP)

                val url = URL(api)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $apikey")
                connection.doOutput = true

                val promptText =
                    """
                    # Role
                    你是一个UI信息提取助手，专门为“灵动岛”功能从图片中提取关键结构化数据。

                    # Output Format
                    必须仅返回纯 JSON 格式，不要包含 markdown 标记（如 ```json）。

                    # Field Definitions (Crucial)
                    请注意：为了适配UI显示，字段逻辑与常规不同，请严格遵守以下定义：
                    - title (必填): **核心数据值**（视觉上最需要突出的具体信息）。例如：具体的取餐码"A808"、航班号"CA1234"、车牌号等。
                    - content (必填): **数据值的标签/分类**（对title的简短描述）。例如："取餐码"、"航班"、"车牌"。
                    - infoTitle: **第一优先级补充数据**。例如：具体的商家名"麦当劳"、具体的登机口"15A"。
                    - infoContent: **第一优先级数据的标签**。例如："商家"、"登机口"。
                    - subInfoTitle: **第二优先级补充数据**。
                    - subInfoContent: **第二优先级数据的标签**。

                    # Constraints
                    1. 字段必须成对出现：如果有 `xxxTitle`，必须有对应的 `xxxContent`。
                    2. 无法识别或不存在的非必填字段，必须返回空字符串 `""`，不要返回 null。
                    3. **填写顺序严格优先**：必须先填充 `info` 组。严禁跳过 `infoTitle` 直接填充 `subInfoTitle`。只有当有一个补充信息时，必须填在 `infoTitle`；有两个补充信息时，次要的那个才填入 `subInfoTitle`。
                    4. **字数强制限制**：`infoTitle` 和 `subInfoTitle` 的**字数总和严禁超过 12 个字**（因为UI空间极小）。如果原始内容过长，你必须进行提取、缩写或仅保留最关键的词汇（例如将“肯德基宅急送”缩写为“肯德基”）。

                    # Example
                    Input Description: 一张显示肯德基北京大学校内店取餐码为K555的截图。
                    Output:
                    {
                      "title": "K555",
                      "content": "取餐码",
                      "infoTitle": "肯德基",
                      "infoContent": "商家",
                      "subInfoTitle": "香辣鸡腿堡",
                      "subInfoContent": "餐品"
                    }
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
                                        // 注意：这里假设 base64Image 变量里不包含 "data:image..." 前缀，
                                        // 如果已有前缀，请直接使用 $base64Image
                                        put("url", "data:image/jpeg;base64,$base64Image")
                                    }
                                }
                            }
                        }
                    }
                }

                val jsonInputString = jsonObject.toString()

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
                    val infoTitle = resultJson.optString("infoTitle")
                    val infoContent = resultJson.optString("infoContent")
                    val subInfoTitle = resultJson.optString("subInfoTitle")
                    val subInfoContent = resultJson.optString("subInfoContent")
                    if (title.isNotEmpty()) {
                        RecognizerResult(
                            title = title,
                            content = content,
                            infoTitle = infoTitle,
                            infoContent = infoContent,
                            subInfoTitle = subInfoTitle,
                            subInfoContent = subInfoContent
                        )
                    } else {
                        RecognizerResult(title = "识别失败", content = cleanedContent, error = true)
                    }
                } else {
                    val error =
                        connection.errorStream.bufferedReader(Charsets.UTF_8).use {
                            it.readText()
                        }
                    logError("请求失败，响应码：$responseCode，错误：$error")
                    RecognizerResult(title = error, error = true)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                logError("请求出错", e)
                RecognizerResult(title = e.toString(), error = true)
            }
        }
    }
}

@Serializable
data class RecognizerResult(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val title: String,
    val content: String = "",
    val infoTitle: String = "",
    val infoContent: String = "",
    val subInfoTitle: String = "",
    val subInfoContent: String = "",
    val error: Boolean? = false
)
