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
            if (requestJson != null && responseJson != null) {
                debugInfo = DebugInfo(requestJson, responseJson, duration)
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
            // 显示耗时
            binding.tvDuration.text = getString(R.string.duration_format, debugInfo!!.durationMs)

            // 显示请求JSON（格式化）
            binding.tvRequestJson.text = formatJson(debugInfo!!.requestJson)

            // 显示响应JSON（格式化）
            binding.tvResponseJson.text = formatJson(debugInfo!!.responseJson)

            binding.tvNoDebugInfo.visibility = View.GONE
        } else {
            binding.tvDuration.visibility = View.GONE
            binding.tvRequestJson.visibility = View.GONE
            binding.tvResponseJson.visibility = View.GONE
            binding.tvNoDebugInfo.visibility = View.VISIBLE
        }
    }

    /** 格式化JSON字符串 */
    private fun formatJson(json: String): String {
        return try {
            val jsonObject = JSONObject(json)
            jsonObject.toString(2) // 缩进2个空格
        } catch (e: Exception) {
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

        fun newInstance(debugInfo: DebugInfo?) =
            DebugInfoFragment().apply {
                arguments =
                    Bundle().apply {
                        if (debugInfo != null) {
                            putString(ARG_REQUEST_JSON, debugInfo.requestJson)
                            putString(ARG_RESPONSE_JSON, debugInfo.responseJson)
                            putLong(ARG_DURATION, debugInfo.durationMs)
                        }
                    }
            }
    }
}