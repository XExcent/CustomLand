package com.mukapp.customland.utils

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.res.ColorStateList
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import androidx.core.content.edit
import com.dylanc.longan.dp
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.mukapp.customland.R

/** 检查无障碍服务是否已启用 */
fun Context.isAccessibilityServiceEnabled(serviceClass: Class<out AccessibilityService>): Boolean {
    try {
        val prefString =
            Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
        val componentName = ComponentName(this, serviceClass)
        return prefString?.contains(componentName.flattenToString()) == true
    } catch (e: Exception) {
        e.printStackTrace()
        return false
    }
}

/** 为 EditText 设置 SharedPreferences 自动保存监听器 */
fun EditText.setupPreferenceWatcher(
    prefsName: String,
    prefKey: String,
    onChanged: (String) -> Unit
) {
    addTextChangedListener(
        object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val value = s.toString()
                onChanged(value)
                context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit {
                    putString(prefKey, value)
                }
            }
        }
    )
}

/** 应用 Material Design 主题到对话框 */
fun MaterialAlertDialogBuilder.applyAppTheme(): MaterialAlertDialogBuilder {
    return setBackground(
        MaterialShapeDrawable(ShapeAppearanceModel.builder().setAllCornerSizes(24.dp).build())
            .apply {
                fillColor = ColorStateList.valueOf(context.getColor(R.color.background))
            }
    )
}
