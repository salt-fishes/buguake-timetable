package com.buguake.timetable.ui.mine

import androidx.compose.runtime.Composable

/**
 * 隐私政策页：正文见 `assets/html/privacy.html`。
 *
 * 改成 HTML 后，文案与排版在网页里维护，原生侧只剩标题栏与返回，
 * 页面代码从 300+ 行降到一行调用，内容也更精简。
 */
@Composable
fun PrivacyPage(
    onBack: () -> Unit,
) {
    HtmlDocScreen(
        title = "隐私政策",
        assetPath = "html/privacy.html",
        onBack = onBack,
    )
}
