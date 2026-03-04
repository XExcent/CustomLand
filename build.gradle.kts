// 顶层构建文件，可在此添加所有子项目/模块的公共配置选项。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}