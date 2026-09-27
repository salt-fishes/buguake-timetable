package com.buguake.timetable.ui

import android.content.Context
import android.view.ContextThemeWrapper
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * 全应用「提示」的统一出口：轻提示走**系统 Toast**，需要确认 / 输入 / 选择的走**框架 AlertDialog**。
 *
 * 为什么不用 Compose 的 Snackbar / AlertDialog：
 *  - Toast 浮在窗口最上层，二级覆盖页、WebView 之上都能看到，不会被页面内容或滚动区遮挡；
 *  - 框架对话框是**独立窗口**，样式由系统（ROM）决定——教务页面里的提示没法伪装成应用自己的弹窗，
 *    用户一眼能分清"这是系统在说话"。
 *
 * 对话框主题跟随应用内的亮/暗选择（系统主题可能与之相反），所以用 [ContextThemeWrapper] 套一层。
 */
object SystemPrompt {

    /** 轻提示（系统 Toast）。空消息直接忽略。 */
    fun toast(context: Context, message: String, long: Boolean = false) {
        if (message.isBlank()) return
        runCatching {
            Toast.makeText(
                context,
                message,
                if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun dialogContext(context: Context, dark: Boolean): Context = ContextThemeWrapper(
        context,
        if (dark) {
            android.R.style.Theme_Material_Dialog_Alert
        } else {
            android.R.style.Theme_Material_Light_Dialog_Alert
        },
    )

    /** 确认类弹窗：确定 / 取消 + 返回键等同取消。 */
    fun alert(
        context: Context,
        dark: Boolean,
        title: String,
        message: String,
        confirmText: String,
        cancelText: String,
        onClose: () -> Unit,
        onResult: (Boolean) -> Unit,
    ): android.app.AlertDialog {
        val dialog = android.app.AlertDialog.Builder(dialogContext(context, dark))
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(confirmText) { _, _ ->
                onClose()
                onResult(true)
            }
            .setNegativeButton(cancelText) { _, _ ->
                onClose()
                onResult(false)
            }
            .create()
        dialog.setOnCancelListener {
            onClose()
            onResult(false)
        }
        return dialog
    }

    /**
     * 输入类弹窗：确定前先过调用方的校验（桥接侧会去跑适配器给的校验函数）。
     * 校验失败**不关窗**——所以确定按钮要在 show() 之后换成自定义监听，
     * 否则框架会先自动 dismiss。
     */
    fun input(
        context: Context,
        dark: Boolean,
        title: String,
        tip: String,
        defaultText: String,
        onSubmit: (input: String, onValidationError: (String) -> Unit, onSuccess: () -> Unit) -> Unit,
        onClose: () -> Unit,
        onCancel: () -> Unit,
    ): android.app.AlertDialog {
        val themed = dialogContext(context, dark)
        val density = context.resources.displayMetrics.density
        val pad = (20 * density).toInt()

        val field = EditText(themed).apply {
            setText(defaultText)
            setSelection(text.length)
            setSingleLine(true)
        }
        val container = LinearLayout(themed).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            if (tip.isNotBlank()) {
                addView(
                    TextView(themed).apply {
                        text = tip
                        setPadding(0, 0, 0, (6 * density).toInt())
                    },
                )
            }
            addView(field)
        }

        val dialog = android.app.AlertDialog.Builder(themed)
            .setTitle(title)
            .setView(container)
            // 先占位，show() 后替换监听（校验失败要留在原地）
            .setPositiveButton("确定", null)
            .setNegativeButton("取消") { _, _ ->
                onClose()
                onCancel()
            }
            .create()
        dialog.setOnCancelListener {
            onClose()
            onCancel()
        }
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                onSubmit(
                    field.text.toString(),
                    { error -> toast(context, error) },  // 校验失败：系统提示 + 弹窗保持打开
                    onClose,                              // 校验通过：交给调用方关窗
                )
            }
        }
        return dialog
    }

    /** 单选列表弹窗：确定返回下标，取消返回 null。 */
    fun singleChoice(
        context: Context,
        dark: Boolean,
        title: String,
        items: List<String>,
        defaultSelectedIndex: Int,
        onClose: () -> Unit,
        onResult: (Int?) -> Unit,
    ): android.app.AlertDialog {
        var selected = defaultSelectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        val dialog = android.app.AlertDialog.Builder(dialogContext(context, dark))
            .setTitle(title)
            .setSingleChoiceItems(items.toTypedArray(), selected) { _, which -> selected = which }
            .setPositiveButton("确定") { _, _ ->
                onClose()
                onResult(selected)
            }
            .setNegativeButton("取消") { _, _ ->
                onClose()
                onResult(null)
            }
            .create()
        dialog.setOnCancelListener {
            onClose()
            onResult(null)
        }
        return dialog
    }
}
