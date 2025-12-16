package com.mukapp.customland.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.dylanc.longan.dp
import com.google.android.material.tabs.TabLayout
import com.mukapp.customland.logic.NotificationHandler
import com.mukapp.customland.R
import com.mukapp.customland.logic.RecognizerResult
import com.mukapp.customland.databinding.ActivityDetailBinding
import org.json.JSONObject
import java.io.File

class DetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDetailBinding
    private var result: RecognizerResult? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 从Intent获取结果ID
        val resultId = intent.getStringExtra(EXTRA_RESULT_ID)
        if (resultId == null) {
            finish()
            return
        }

        // 从历史记录中查找结果
        result = NotificationHandler.history.find { it.id == resultId }
        if (result == null) {
            finish()
            return
        }

        setupEdgeToEdge()
        setupToolbar()
        setupTabs()
        loadScreenshotPage()
        loadDebugInfoPage()
    }

    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            binding.blurViewToolbar.updatePadding(top = systemBars.top)

            // 为两个页面设置padding
            binding.pageScreenshot.root.updatePadding(
                top = systemBars.top + 56.dp.toInt() + 48.dp.toInt(),
                bottom = systemBars.bottom
            )
            binding.pageDebugInfo.root.updatePadding(
                top = systemBars.top + 56.dp.toInt() + 48.dp.toInt(),
                bottom = systemBars.bottom
            )

            insets
        }

        val decorView = window.decorView
        binding.blurViewToolbar
            .setupWith(binding.blurTarget)
            .setBlurRadius(16f)
            .setFrameClearDrawable(decorView.background)
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener { finish() }
    }

    private fun setupTabs() {
        // 添加Tab
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.tab_screenshot))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.tab_debug_info))

        // Tab选择监听
        binding.tabLayout.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    when (tab.position) {
                        0 -> switchPage(binding.pageScreenshot.root, binding.pageDebugInfo.root)
                        1 -> switchPage(binding.pageDebugInfo.root, binding.pageScreenshot.root)
                    }
                }

                override fun onTabUnselected(tab: TabLayout.Tab) {}
                override fun onTabReselected(tab: TabLayout.Tab) {}
            }
        )
    }

    private fun switchPage(show: View, hide: View) {
        show.animate().cancel()
        hide.animate().cancel()

        show.visibility = View.VISIBLE
        show.alpha = 0f
        show.animate().alpha(1f).setDuration(200).start()

        hide.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction { hide.visibility = View.GONE }
            .start()
    }

    private fun loadScreenshotPage() {
        val ivScreenshot = binding.pageScreenshot.ivScreenshot
        val tvNoScreenshot = binding.pageScreenshot.tvNoScreenshot

        val screenshotPath = result?.screenshotPath
        if (screenshotPath != null) {
            val file = File(filesDir, screenshotPath)
            if (file.exists()) {
                ivScreenshot.setImageURI(Uri.fromFile(file))
                ivScreenshot.visibility = View.VISIBLE
                tvNoScreenshot.visibility = View.GONE
            } else {
                ivScreenshot.visibility = View.GONE
                tvNoScreenshot.visibility = View.VISIBLE
            }
        } else {
            ivScreenshot.visibility = View.GONE
            tvNoScreenshot.visibility = View.VISIBLE
        }
    }

    private fun loadDebugInfoPage() {
        val cardError = binding.pageDebugInfo.cardError
        val tvErrorMessage = binding.pageDebugInfo.tvErrorMessage
        val tvDuration = binding.pageDebugInfo.tvDuration
        val tvRequestJson = binding.pageDebugInfo.tvRequestJson
        val tvResponseJson = binding.pageDebugInfo.tvResponseJson
        val tvNoDebugInfo = binding.pageDebugInfo.tvNoDebugInfo

        val errorMessage = result?.errorMessage
        val debugInfo = result?.debugInfo

        // 显示错误信息（如果有）
        if (errorMessage != null) {
            cardError.visibility = View.VISIBLE
            // 限制错误信息长度，防止超长文本
            val displayMessage =
                if (errorMessage.length > 3000) {
                    errorMessage.take(3000) + "\n\n... (错误信息过长已截断)"
                } else {
                    errorMessage
                }
            tvErrorMessage.text = displayMessage
        } else {
            cardError.visibility = View.GONE
        }

        // 显示调试信息（如果有）
        if (debugInfo != null) {
            tvDuration.text = getString(R.string.duration_format, debugInfo.durationMs)
            tvRequestJson.text = formatJson(debugInfo.requestJson)
            tvResponseJson.text = formatJson(debugInfo.responseJson)
            tvNoDebugInfo.visibility = View.GONE
        } else {
            // 如果没有调试信息但有错误信息，也不显示"暂无调试信息"
            if (errorMessage != null) {
                tvDuration.visibility = View.GONE
                tvRequestJson.visibility = View.GONE
                tvResponseJson.visibility = View.GONE
                tvNoDebugInfo.visibility = View.GONE
            } else {
                // 既没有错误信息也没有调试信息
                tvDuration.visibility = View.GONE
                tvRequestJson.visibility = View.GONE
                tvResponseJson.visibility = View.GONE
                tvNoDebugInfo.visibility = View.VISIBLE
            }
        }
    }

    /** 格式化JSON字符串 */
    private fun formatJson(json: String): String {
        // 限制长度，防止超长文本导致渲染卡死
        val maxLength = 3000
        val truncated =
            if (json.length > maxLength) {
                json.take(maxLength) + "\n\n... (内容过长已截断，总长度: ${json.length} 字符)"
            } else {
                json
            }

        return try {
            val jsonObject = JSONObject(truncated)
            jsonObject.toString(2) // 缩进2个空格
        } catch (_: Exception) {
            truncated // 如果解析失败，返回原始字符串
        }
    }

    companion object {
        private const val EXTRA_RESULT_ID = "result_id"

        fun start(context: Context, resultId: String) {
            val intent =
                Intent(context, DetailActivity::class.java).apply {
                    putExtra(EXTRA_RESULT_ID, resultId)
                }
            context.startActivity(intent)
        }
    }
}