package com.mukapp.customland.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.mukapp.customland.R
import com.mukapp.customland.databinding.FragmentDebugInfoBinding
import com.mukapp.customland.logic.DebugInfo
import org.json.JSONObject

class DebugInfoFragment : Fragment() {
    private var _binding: FragmentDebugInfoBinding? = null
    private val binding
        get() = _binding!!

    private var debugInfo: DebugInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            val requestJson = it.getString(ARG_REQUEST_JSON)
            val responseJson = it.getString(ARG_RESPONSE_JSON)
            val duration = it.getLong(ARG_DURATION)
            val ocrRequestJson = it.getString(ARG_OCR_REQUEST_JSON)
            val ocrResponseJson = it.getString(ARG_OCR_RESPONSE_JSON)
            val ocrDuration =
                if (it.containsKey(ARG_OCR_DURATION)) it.getLong(ARG_OCR_DURATION) else null

            if (requestJson != null && responseJson != null) {
                debugInfo = DebugInfo(
                    requestJson = requestJson,
                    responseJson = responseJson,
                    durationMs = duration,
                    ocrRequestJson = ocrRequestJson,
                    ocrResponseJson = ocrResponseJson,
                    ocrDurationMs = ocrDuration
                )
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDebugInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (debugInfo != null) {
            val info = debugInfo!!

            // 检查是否有 OCR 调试信息（两阶段模式）
            val hasOcrInfo = info.ocrRequestJson != null && info.ocrResponseJson != null

            if (hasOcrInfo) {
                // 显示 OCR 分组
                binding.cardOcrSection.visibility = View.VISIBLE
                binding.tvMainModelSection.visibility = View.VISIBLE

                // OCR 耗时
                binding.tvOcrDuration.text =
                    getString(R.string.duration_format, info.ocrDurationMs ?: 0)

                // OCR 请求/响应 JSON
                binding.tvOcrRequestJson.text = formatJson(info.ocrRequestJson)
                binding.tvOcrResponseJson.text = formatJson(info.ocrResponseJson)
            } else {
                // 隐藏 OCR 分组
                binding.cardOcrSection.visibility = View.GONE
                binding.tvMainModelSection.visibility = View.GONE
            }

            // 显示主模型调试信息
            binding.tvDuration.text = getString(R.string.duration_format, info.durationMs)
            binding.tvRequestJson.text = formatJson(info.requestJson)
            binding.tvResponseJson.text = formatJson(info.responseJson)

            binding.tvNoDebugInfo.visibility = View.GONE
        } else {
            binding.tvDuration.visibility = View.GONE
            binding.tvRequestJson.visibility = View.GONE
            binding.tvResponseJson.visibility = View.GONE
            binding.cardOcrSection.visibility = View.GONE
            binding.tvMainModelSection.visibility = View.GONE
            binding.tvNoDebugInfo.visibility = View.VISIBLE
        }
    }

    /** 格式化JSON字符串 */
    private fun formatJson(json: String): String {
        return try {
            val jsonObject = JSONObject(json)
            jsonObject.toString(2) // 缩进2个空格
        } catch (_: Exception) {
            json // 如果解析失败，返回原始字符串
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_REQUEST_JSON = "request_json"
        private const val ARG_RESPONSE_JSON = "response_json"
        private const val ARG_DURATION = "duration"
        private const val ARG_OCR_REQUEST_JSON = "ocr_request_json"
        private const val ARG_OCR_RESPONSE_JSON = "ocr_response_json"
        private const val ARG_OCR_DURATION = "ocr_duration"

        fun newInstance(debugInfo: DebugInfo?) =
            DebugInfoFragment().apply {
                arguments =
                    Bundle().apply {
                        if (debugInfo != null) {
                            putString(ARG_REQUEST_JSON, debugInfo.requestJson)
                            putString(ARG_RESPONSE_JSON, debugInfo.responseJson)
                            putLong(ARG_DURATION, debugInfo.durationMs)
                            // OCR 调试信息（可选）
                            debugInfo.ocrRequestJson?.let { putString(ARG_OCR_REQUEST_JSON, it) }
                            debugInfo.ocrResponseJson?.let { putString(ARG_OCR_RESPONSE_JSON, it) }
                            debugInfo.ocrDurationMs?.let { putLong(ARG_OCR_DURATION, it) }
                        }
                    }
            }
    }
}
