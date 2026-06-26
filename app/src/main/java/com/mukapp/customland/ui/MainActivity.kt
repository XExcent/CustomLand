package com.mukapp.customland.ui

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dylanc.longan.appVersionName
import com.dylanc.longan.dp
import com.dylanc.longan.toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mukapp.customland.R
import com.mukapp.customland.common.Constants
import com.mukapp.customland.common.MMKVHelper
import com.mukapp.customland.databinding.ActivityMainBinding
import com.mukapp.customland.logic.AiRecognizer
import com.mukapp.customland.logic.NotificationHandler
import com.mukapp.customland.logic.RecognizerResult
import com.mukapp.customland.service.ScreenshotAccessibilityService
import com.mukapp.customland.ui.adapter.NotificationAdapter
import com.mukapp.customland.utils.RootUtils
import com.mukapp.customland.utils.ShizukuScreenshotHelper
import com.mukapp.customland.utils.applyAppTheme
import com.mukapp.customland.utils.isAccessibilityServiceEnabled
import com.mukapp.customland.utils.setupPreferenceWatcher
import dev.rikka.shizuku.Shizuku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val notificationAdapter =
        NotificationAdapter(
            onItemClick = { result ->
                // 点击列表项，跳转到详情页
                DetailActivity.start(this, result.id)
            },
            onItemLongClick = { result ->
                // 长按列表项，显示操作选择对话框
                showActionDialog(result)
            }
        )

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            updateNotificationStatus()
            if (!isGranted) {
                showNotificationPermissionDialog()
            }
        }

    private val shizukuPermissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode != SHIZUKU_PERMISSION_REQUEST_CODE) return@OnRequestPermissionResultListener
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            runOnUiThread {
                if (granted) {
                    toast(getString(R.string.toast_shizuku_permission_granted))
                } else {
                    toast(getString(R.string.toast_shizuku_permission_denied))
                }
                updateShizukuStatus()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            binding.blurViewToolbar.updatePadding(top = systemBars.top)

            binding.pageHome.updatePadding(
                top = systemBars.top + 56.dp.toInt(),
                bottom = systemBars.bottom + 64.dp.toInt()
            )
            binding.pageSetting.updatePadding(
                top = systemBars.top + 56.dp.toInt(),
                bottom =
                    if (imeHeight == 0) {
                        systemBars.bottom + 64.dp.toInt()
                    } else {
                        imeHeight
                    }
            )

            if (imeHeight > 0) {
                binding.pageSetting.post {
                    binding.pageSetting.scrollTo(0, binding.pageSetting.bottom)
                }
            }

            val baseThumb =
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 2.dp // 当所有圆角相同时，可以这样简写
                    setColor("#80808080".toColorInt()) // 滚动条颜色，建议半透明
                    setSize(4.dp.toInt(), 0) // 设置宽度
                }

            val noInsetThumb = InsetDrawable(baseThumb, 0)

            binding.pageHome.verticalScrollbarThumbDrawable = noInsetThumb
            binding.pageSetting.verticalScrollbarThumbDrawable = noInsetThumb
            insets
        }

        // 拦截 BottomNavigationView 的 Insets 处理
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNav) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = systemBars.bottom)
            insets
        }

        binding.toolbarTitle.text = this.title

        val decorView = window.decorView

        binding.blurViewToolbar
            .setupWith(binding.blurTarget)
            .setBlurRadius(16f)
            .setFrameClearDrawable(decorView.background)
        binding.blurViewBottomNav
            .setupWith(binding.blurTarget)
            .setBlurRadius(16f)
            .setFrameClearDrawable(decorView.background)

        // Load history
        lifecycleScope.launch { NotificationHandler.loadHistory(this@MainActivity) }

        // Setup RecyclerView
        binding.pageHome.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = notificationAdapter
        }
        binding.pageHome.addItemDecoration(
            object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(
                    outRect: Rect,
                    view: View,
                    parent: RecyclerView,
                    state: RecyclerView.State
                ) {
                    val position = parent.getChildAdapterPosition(view)
                    val itemCount = state.itemCount
                    if (position == 0) {
                        outRect.top = 8.dp.toInt()
                    }
                    if (position == itemCount - 1) {
                        outRect.bottom = 8.dp.toInt()
                    }
                }
            }
        )

        // Observe History
        NotificationHandler.onHistoryUpdated = {
            runOnUiThread { notificationAdapter.submitList(NotificationHandler.history.toList()) }
        }
        // Initial load
        notificationAdapter.submitList(NotificationHandler.history.toList())

        binding.version.text = getString(R.string.app_version, appVersionName)

        // Settings Page Logic
        binding.coolapkButton.setOnClickListener {
            val uri = "http://www.coolapk.com/u/1105973".toUri()
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        }

        binding.cardAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        binding.cardNotification.setOnClickListener { askNotificationPermission() }

        // Shizuku 截图设置
        val useShizuku = MMKVHelper.getBoolean(Constants.PREF_USE_SHIZUKU_SCREENSHOT, true)
        binding.switchShizuku.isChecked = useShizuku
        updateShizukuStatus()

        binding.cardShizuku.setOnClickListener {
            binding.switchShizuku.isChecked = !binding.switchShizuku.isChecked
        }

        binding.switchShizuku.setOnCheckedChangeListener { _, isChecked ->
            MMKVHelper.putBoolean(Constants.PREF_USE_SHIZUKU_SCREENSHOT, isChecked)
            if (isChecked) {
                requestShizukuPermissionIfNeeded()
            }
            updateShizukuStatus()
        }

        Shizuku.addRequestPermissionResultListener(shizukuPermissionResultListener)

        // Root 权限设置
        val isRootEnabled = MMKVHelper.getBoolean(Constants.PREF_ROOT_ENABLED, false)
        binding.switchRoot.isChecked = isRootEnabled
        updateRootStatus(isRootEnabled)

        // 点击卡片时切换开关
        binding.cardRoot.setOnClickListener {
            binding.switchRoot.isChecked = !binding.switchRoot.isChecked
        }

        binding.switchRoot.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // 请求 Root 权限
                lifecycleScope.launch {
                    val hasRoot = withContext(Dispatchers.IO) { RootUtils.requestRootPermission() }
                    if (hasRoot) {
                        MMKVHelper.putBoolean(Constants.PREF_ROOT_ENABLED, true)
                        updateRootStatus(true)
                        toast(getString(R.string.toast_root_granted))
                    } else {
                        binding.switchRoot.isChecked = false
                        MMKVHelper.putBoolean(Constants.PREF_ROOT_ENABLED, false)
                        updateRootStatus(false)
                        toast(getString(R.string.toast_root_denied))
                    }
                }
            } else {
                MMKVHelper.putBoolean(Constants.PREF_ROOT_ENABLED, false)
                updateRootStatus(false)
            }
        }

        // 多任务隐藏设置
        val isHideFromRecentsEnabled =
            MMKVHelper.getBoolean(Constants.PREF_HIDE_FROM_RECENTS, false)
        binding.switchHideRecents.isChecked = isHideFromRecentsEnabled

        // 点击卡片时切换开关
        binding.cardHideRecents.setOnClickListener {
            binding.switchHideRecents.isChecked = !binding.switchHideRecents.isChecked
        }

        binding.switchHideRecents.setOnCheckedChangeListener { _, isChecked ->
            MMKVHelper.putBoolean(Constants.PREF_HIDE_FROM_RECENTS, isChecked)
        }

        // AI Settings Logic

        // 加载已保存的值显示到 UI（AiRecognizer 会在使用时自动从 MMKV 加载）
        binding.etApiAddress.setText(
            MMKVHelper.getString(
                Constants.PREF_API_ADDRESS,
                AiRecognizer.api
            )
        )
        binding.etApiKey.setText(MMKVHelper.getString(Constants.PREF_API_KEY, AiRecognizer.apikey))
        binding.etModelName.setText(
            MMKVHelper.getString(
                Constants.PREF_MODEL_NAME,
                AiRecognizer.model
            )
        )

        // 输入变化时自动保存到 MMKV
        binding.etApiAddress.setupPreferenceWatcher(Constants.PREF_API_ADDRESS)
        binding.etApiKey.setupPreferenceWatcher(Constants.PREF_API_KEY)
        binding.etModelName.setupPreferenceWatcher(Constants.PREF_MODEL_NAME)

        // 主模型图像输入开关设置
        val supportsVision = MMKVHelper.getBoolean(Constants.PREF_MODEL_SUPPORTS_VISION, true)
        binding.switchVisionSupport.isChecked = supportsVision
        updateOcrSettingsVisibility(supportsVision)

        // 点击卡片时切换开关
        binding.cardVisionSupport.setOnClickListener {
            binding.switchVisionSupport.isChecked = !binding.switchVisionSupport.isChecked
        }

        binding.switchVisionSupport.setOnCheckedChangeListener { _, isChecked ->
            MMKVHelper.putBoolean(Constants.PREF_MODEL_SUPPORTS_VISION, isChecked)
            updateOcrSettingsVisibility(isChecked)
        }

        // OCR 模型设置
        binding.etOcrApiAddress.setText(
            MMKVHelper.getString(
                Constants.PREF_OCR_API_ADDRESS,
                AiRecognizer.ocrApi
            )
        )
        binding.etOcrApiKey.setText(
            MMKVHelper.getString(
                Constants.PREF_OCR_API_KEY,
                AiRecognizer.ocrApikey
            )
        )
        binding.etOcrModelName.setText(
            MMKVHelper.getString(
                Constants.PREF_OCR_MODEL_NAME,
                AiRecognizer.ocrModel
            )
        )

        binding.etOcrApiAddress.setupPreferenceWatcher(Constants.PREF_OCR_API_ADDRESS)
        binding.etOcrApiKey.setupPreferenceWatcher(Constants.PREF_OCR_API_KEY)
        binding.etOcrModelName.setupPreferenceWatcher(Constants.PREF_OCR_MODEL_NAME)

        // 教程卡片点击事件
        binding.cardTutorial.setOnClickListener {
            showTutorialDialog()
        }

        // 提示词设置点击事件
        binding.cardPromptVision.setOnClickListener {
            showPromptEditDialog(
                getString(R.string.prompt_vision_title),
                Constants.PREF_PROMPT_VISION,
                AiRecognizer.DEFAULT_PROMPT_VISION
            )
        }
        binding.cardPromptText.setOnClickListener {
            showPromptEditDialog(
                getString(R.string.prompt_text_extract_title),
                Constants.PREF_PROMPT_TEXT_EXTRACT,
                AiRecognizer.DEFAULT_PROMPT_TEXT_EXTRACT
            )
        }
        binding.cardPromptOcr.setOnClickListener {
            showPromptEditDialog(
                getString(R.string.prompt_ocr_title),
                Constants.PREF_PROMPT_OCR,
                AiRecognizer.DEFAULT_PROMPT_OCR
            )
        }

        // 设置 BottomNavigationView 的监听器
        binding.bottomNav.setOnItemSelectedListener { item ->
            if (binding.bottomNav.selectedItemId == item.itemId) {
                return@setOnItemSelectedListener true
            }

            when (item.itemId) {
                R.id.navigation_home -> {
                    switchPage(binding.pageHome, binding.pageSetting)
                    true
                }

                R.id.navigation_settings -> {
                    switchPage(binding.pageSetting, binding.pageHome)
                    true
                }

                else -> false
            }
        }

        askNotificationPermission()

        // 处理 Intent，确定是否需要跳转到特定页面
        val targetPage = intent.getStringExtra(Constants.EXTRA_TARGET_PAGE)
        if (targetPage == Constants.TARGET_PAGE_SETTING) {
            // 如果 Intent 要求显示设置页，则切换到设置页
            binding.bottomNav.selectedItemId = R.id.navigation_settings
            // 手动同步页面状态
            binding.pageHome.visibility = View.GONE
            binding.pageHome.alpha = 0f
            binding.pageSetting.visibility = View.VISIBLE
            binding.pageSetting.alpha = 1f

            toast(getString(R.string.toast_enable_accessibility))
            intent.removeExtra(Constants.EXTRA_TARGET_PAGE)
        } else if (savedInstanceState == null) {
            // 只有在首次启动（非重建）且没有特定目标时，才默认显示主页
            // 如果 savedInstanceState != null，说明是重建（如权限变更），
            // 此时应等待 onRestoreInstanceState 恢复状态，不要强制覆盖
            binding.bottomNav.selectedItemId = R.id.navigation_home
            // 手动同步页面状态
            binding.pageHome.visibility = View.VISIBLE
            binding.pageHome.alpha = 1f
            binding.pageSetting.visibility = View.GONE
            binding.pageSetting.alpha = 0f
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val selectedId = binding.bottomNav.selectedItemId

                    if (selectedId != R.id.navigation_home) {
                        // 非主页时，先返回主页
                        binding.bottomNav.selectedItemId = R.id.navigation_home
                    } else {
                        // 仅仅是把任务移到后台（相当于按了 Home 键），而不销毁 Activity
                        moveTaskToBack(true)

                        // 检查是否启用了多任务隐藏功能
                        val hideFromRecents =
                            MMKVHelper.getBoolean(Constants.PREF_HIDE_FROM_RECENTS, false)
                        if (hideFromRecents) {
                            // 获取 AppTask 并设置从最近任务中排除
                            val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
                            val tasks = am.appTasks
                            if (tasks.isNotEmpty()) {
                                // 设置为 true：从多任务中隐藏
                                tasks[0].setExcludeFromRecents(true)
                            }
                        }
                    }
                }
            }
        )
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        // Activity 重建（如权限变更）导致状态恢复后，手动同步页面可见性
        // 此时 BottomNavigationView 已经恢复了选中的 Item
        when (binding.bottomNav.selectedItemId) {
            R.id.navigation_settings -> {
                binding.pageHome.visibility = View.GONE
                binding.pageHome.alpha = 0f
                binding.pageSetting.visibility = View.VISIBLE
                binding.pageSetting.alpha = 1f
            }

            else -> {
                binding.pageHome.visibility = View.VISIBLE
                binding.pageHome.alpha = 1f
                binding.pageSetting.visibility = View.GONE
                binding.pageSetting.alpha = 0f
            }
        }
    }

    private fun switchPage(show: View, hide: View) {
        show.animate().cancel()
        hide.animate().cancel()

        show.animate().setListener(null)
        show.visibility = View.VISIBLE
        show.animate().alpha(1f).setDuration(200).start()

        hide.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                // 只有动画真正执行结束时才隐藏
                hide.visibility = View.GONE
            }
            .start()
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                updateNotificationStatus()
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            updateNotificationStatus()
        }
    }

    override fun onResume() {
        super.onResume()
        // 每次返回App时，都检查服务的状态
        updateServiceStatus()
        updateNotificationStatus()
        updateShizukuStatus()

        // 如果启用了多任务隐藏功能，恢复时需要将其重新显示
        val hideFromRecents = MMKVHelper.getBoolean(Constants.PREF_HIDE_FROM_RECENTS, false)
        if (hideFromRecents) {
            val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            val tasks = am.appTasks
            if (tasks.isNotEmpty()) {
                // 恢复为 false：显示在多任务中
                // 这样在软件正常使用期间，切换出去是能看到卡片的
                tasks[0].setExcludeFromRecents(false)
            }
        }
    }

    // 处理 Activity 已存在于后台并被拉起的情况
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // 更新当前的 intent
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val target = intent.getStringExtra(Constants.EXTRA_TARGET_PAGE)
        if (target == Constants.TARGET_PAGE_SETTING) {
            binding.bottomNav.selectedItemId = R.id.navigation_settings
            toast(getString(R.string.toast_enable_accessibility))
            // 建议：处理完后清除 Extra，避免旋转屏幕等操作重复触发
            intent.removeExtra(Constants.EXTRA_TARGET_PAGE)
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionResultListener)
        super.onDestroy()
    }

    private fun updateServiceStatus() {
        if (isAccessibilityServiceEnabled()) {
            binding.tvAccessibilityStatus.text = getString(R.string.status_enabled)
            binding.tvAccessibilityStatus.setTextColor(
                ContextCompat.getColor(this, R.color.textSecondary)
            )
        } else {
            binding.tvAccessibilityStatus.text = getString(R.string.status_disabled)
            binding.tvAccessibilityStatus.setTextColor(Color.RED)
        }
    }

    private fun updateNotificationStatus() {
        val isGranted =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true // Pre-Tiramisu assumes granted or handled differently
            }

        if (isGranted) {
            binding.tvNotificationStatus.text = getString(R.string.status_enabled)
            binding.tvNotificationStatus.setTextColor(
                ContextCompat.getColor(this, R.color.textSecondary)
            )
        } else {
            binding.tvNotificationStatus.text = getString(R.string.status_disabled)
            binding.tvNotificationStatus.setTextColor(Color.RED)
        }
    }

    /** 检查无障碍服务是否已启用 */
    private fun isAccessibilityServiceEnabled(): Boolean {
        return isAccessibilityServiceEnabled(ScreenshotAccessibilityService::class.java)
    }

    /** 更新 Root 权限状态显示 */
    private fun updateRootStatus(isEnabled: Boolean) {
        if (isEnabled) {
            binding.tvRootStatus.text = getString(R.string.status_root_granted)
            binding.tvRootStatus.setTextColor(ContextCompat.getColor(this, R.color.textSecondary))
        } else {
            binding.tvRootStatus.text = getString(R.string.root_permission_desc)
            binding.tvRootStatus.setTextColor(ContextCompat.getColor(this, R.color.textSecondary))
        }
    }

    private fun requestShizukuPermissionIfNeeded() {
        if (!ShizukuScreenshotHelper.isShizukuAvailable()) {
            toast(getString(R.string.toast_shizuku_unavailable))
            return
        }
        if (!ShizukuScreenshotHelper.hasShizukuPermission()) {
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
        }
    }

    private fun updateShizukuStatus() {
        if (!binding.switchShizuku.isChecked) {
            binding.tvShizukuStatus.text = getString(R.string.shizuku_screenshot_desc)
            binding.tvShizukuStatus.setTextColor(
                ContextCompat.getColor(this, R.color.textSecondary)
            )
            return
        }

        if (!ShizukuScreenshotHelper.isShizukuAvailable()) {
            binding.tvShizukuStatus.text = getString(R.string.shizuku_status_unavailable)
            binding.tvShizukuStatus.setTextColor(Color.RED)
            return
        }

        if (ShizukuScreenshotHelper.hasShizukuPermission()) {
            binding.tvShizukuStatus.text = getString(R.string.shizuku_status_granted)
            binding.tvShizukuStatus.setTextColor(
                ContextCompat.getColor(this, R.color.textSecondary)
            )
        } else {
            binding.tvShizukuStatus.text = getString(R.string.shizuku_status_permission_required)
            binding.tvShizukuStatus.setTextColor(Color.RED)
        }
    }

    private fun showNotificationPermissionDialog() {
        MaterialAlertDialogBuilder(this, R.style.MyDialogTheme)
            .applyAppTheme()
            .setTitle(getString(R.string.dialog_notification_permission_title))
            .setMessage(getString(R.string.dialog_notification_permission_message))
            .setPositiveButton(getString(R.string.action_settings)) { _, _ ->
                val intent =
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    }
                startActivity(intent)
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .setIcon(R.drawable.notification_settings)
            .show()
    }

    /** 显示删除确认对话框 */
    private fun showDeleteConfirmDialog(result: RecognizerResult) {
        MaterialAlertDialogBuilder(this, R.style.MyDialogTheme)
            .applyAppTheme()
            .setTitle(getString(R.string.dialog_delete_title))
            .setMessage(getString(R.string.dialog_delete_message))
            .setPositiveButton(getString(R.string.action_delete)) { _, _ ->
                NotificationHandler.deleteHistoryItem(this, result)
                toast(getString(R.string.toast_deleted))
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .setIcon(R.drawable.delete_forever)
            .show()
    }

    /** 显示操作选择对话框 */
    private fun showActionDialog(result: RecognizerResult) {
        val items = arrayOf(getString(R.string.action_resend), getString(R.string.action_delete))
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
        MaterialAlertDialogBuilder(this, R.style.MyDialogTheme)
            .applyAppTheme()
            .setTitle(getString(R.string.dialog_action_title))
            .setAdapter(adapter) { _, which ->
                when (which) {
                    0 -> {
                        // 再次发送
                        NotificationHandler.sendNotification(this, result)
                        toast(getString(R.string.toast_resent))
                    }

                    1 -> {
                        // 删除
                        showDeleteConfirmDialog(result)
                    }
                }
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .setIcon(R.drawable.action_key)
            .show()
    }

    /** 更新 OCR 设置区域的可见性 */
    private fun updateOcrSettingsVisibility(supportsVision: Boolean) {
        binding.layoutOcrSettings.visibility = if (supportsVision) View.GONE else View.VISIBLE

        // 提示词设置可见性
        if (supportsVision) {
            // 主模型支持 Vision：只显示 Vision 提示词
            binding.cardPromptVision.visibility = View.VISIBLE
            binding.cardPromptText.visibility = View.GONE
            binding.cardPromptOcr.visibility = View.GONE
        } else {
            // 主模型不支持 Vision（OCR 模式）：隐藏 Vision 提示词，显示 OCR 和 文本提取提示词
            binding.cardPromptVision.visibility = View.GONE
            binding.cardPromptText.visibility = View.VISIBLE
            binding.cardPromptOcr.visibility = View.VISIBLE
        }
    }

    /** 显示教程对话框 */
    private fun showTutorialDialog() {
        val zhipuRegisterUrl =
            "https://www.bigmodel.cn/invite?icode=DDcuAn9IU7nM6rPrg1%2FHDGczbXFgPRGIalpycrEwJ28%3D"
        val zhipuApikeyUrl = "https://open.bigmodel.cn/usercenter/proj-mgmt/apikeys"
        val siliconflowRegisterUrl = "https://cloud.siliconflow.cn/i/ElzWYSiJ"
        val siliconflowApikeyUrl = "https://cloud.siliconflow.cn/me/account/ak"

        val message = android.text.SpannableStringBuilder().apply {
            append(getString(R.string.dialog_tutorial_token_note))
            append("\n\n")

            // 智谱 AI 部分
            append("【${getString(R.string.dialog_tutorial_zhipu_title)}】\n")

            val zhipuRegisterText = "• ${getString(R.string.dialog_tutorial_zhipu_register)}"
            val zhipuRegisterStart = length
            append(zhipuRegisterText)
            setSpan(
                object : android.text.style.ClickableSpan() {
                    override fun onClick(widget: View) {
                        startActivity(Intent(Intent.ACTION_VIEW, zhipuRegisterUrl.toUri()))
                    }
                },
                zhipuRegisterStart,
                length,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            append("\n")

            val zhipuApikeyText = "• ${getString(R.string.dialog_tutorial_zhipu_apikey)}"
            val zhipuApikeyStart = length
            append(zhipuApikeyText)
            setSpan(
                object : android.text.style.ClickableSpan() {
                    override fun onClick(widget: View) {
                        startActivity(Intent(Intent.ACTION_VIEW, zhipuApikeyUrl.toUri()))
                    }
                },
                zhipuApikeyStart,
                length,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            append("\n\n")

            // 硅基流动部分
            append("【${getString(R.string.dialog_tutorial_siliconflow_title)}】\n")

            val siliconflowRegisterText =
                "• ${getString(R.string.dialog_tutorial_siliconflow_register)}"
            val siliconflowRegisterStart = length
            append(siliconflowRegisterText)
            setSpan(
                object : android.text.style.ClickableSpan() {
                    override fun onClick(widget: View) {
                        startActivity(Intent(Intent.ACTION_VIEW, siliconflowRegisterUrl.toUri()))
                    }
                },
                siliconflowRegisterStart,
                length,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            append("\n")

            val siliconflowApikeyText =
                "• ${getString(R.string.dialog_tutorial_siliconflow_apikey)}"
            val siliconflowApikeyStart = length
            append(siliconflowApikeyText)
            setSpan(
                object : android.text.style.ClickableSpan() {
                    override fun onClick(widget: View) {
                        startActivity(Intent(Intent.ACTION_VIEW, siliconflowApikeyUrl.toUri()))
                    }
                },
                siliconflowApikeyStart,
                length,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            append("\n\n")

            append(getString(R.string.dialog_tutorial_siliconflow_note))
        }

        val dialog = MaterialAlertDialogBuilder(this, R.style.MyDialogTheme)
            .applyAppTheme()
            .setTitle(getString(R.string.dialog_tutorial_title))
            .setMessage(message)
            .setNegativeButton(getString(R.string.action_cancel), null)
            .setIcon(R.drawable.key)
            .show()

        // 让 TextView 中的链接可点击
        dialog.findViewById<android.widget.TextView>(android.R.id.message)?.movementMethod =
            android.text.method.LinkMovementMethod.getInstance()
    }

    /** 显示提示词编辑对话框 */
    private fun showPromptEditDialog(title: String, prefKey: String, defaultValue: String) {
        val currentPrompt = MMKVHelper.getString(prefKey, defaultValue)
        val editText = com.google.android.material.textfield.TextInputEditText(this)
        editText.setText(currentPrompt)
        editText.gravity = android.view.Gravity.TOP or android.view.Gravity.START
        editText.textSize = 14f
        editText.background = null
        editText.setPadding(0, 0, 0, 0)

        // 使用 NestedScrollView 包裹 EditText 以处理滚动裁剪问题
        val scrollView = androidx.core.widget.NestedScrollView(this)

        // 自定义背景和圆角
        val backgroundDrawable = GradientDrawable().apply {
            setColor(ContextCompat.getColor(this@MainActivity, R.color.container))
            cornerRadius = 12.dp
        }
        scrollView.background = backgroundDrawable

        // 设置 Padding 并允许子视图在 Padding 区域绘制
        val padding = 16.dp.toInt()
        scrollView.setPadding(padding, padding, padding, padding)
        scrollView.clipToPadding = false

        scrollView.addView(editText)

        // 使用 FrameLayout 作为容器来添加 Margin
        val container = android.widget.FrameLayout(this)
        // 使用相对固定的高度，防止输入法弹出时被顶出屏幕
        // val scrollHeight = 250.dp.toInt()
        val params = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            // scrollHeight
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.leftMargin = 24.dp.toInt()
        params.rightMargin = 24.dp.toInt()
        params.topMargin = 16.dp.toInt()
        scrollView.layoutParams = params
        container.addView(scrollView)

        MaterialAlertDialogBuilder(this, R.style.MyDialogTheme)
            .applyAppTheme()
            .setTitle(title)
            .setView(container)
            .setPositiveButton(getString(android.R.string.ok)) { _, _ ->
                val newPrompt = editText.text.toString()
                MMKVHelper.putString(prefKey, newPrompt)
                toast(getString(R.string.toast_prompt_saved))
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .setNeutralButton(getString(R.string.action_reset_default)) { _, _ ->
                MMKVHelper.remove(prefKey) // 移除即恢复默认
                toast(getString(R.string.toast_prompt_reset))
            }
            .setIcon(R.drawable.edit)
            .show()
    }

    companion object {
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001
    }
}