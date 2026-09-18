package com.buguake.timetable.campus.laundry

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri


/**
 * 设备"去开洗"跳转：拼 scheme 直达官方小程序该设备下单页，不碰支付与设备控制。
 *
 * 小程序路径固定 pages/device/togo，靠 action_code（机器二维码里的 code）定位具体设备，
 * 洗衣机/烘干机/洗鞋机/吹风机共用同一跳转。scheme 复刻官方落地页
 * （https://batch.shunshuikj.cn/scan?code=X 的 302 H5 行为），无需小程序 AppSecret。
 */
object LaundryJump {
    private const val WECHAT_APPID = "wx50e9776c81087219"
    private const val ALIPAY_APPID = "2021004146616939"
    private const val MINI_PATH = "pages/device/togo"

    /**
     * scheme query 段的百分号编码：与官方落地页的 encodeURIComponent 一致
     * （仅 RFC 3986 非保留字符不转义）。自实现而非 Uri.encode，保证纯 JVM 可测。
     */
    internal fun queryEncode(s: String): String = buildString {
        val hex = "0123456789ABCDEF"
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xFF
            if (c in 'A'.code..'Z'.code || c in 'a'.code..'z'.code ||
                c in '0'.code..'9'.code || c == '-'.code || c == '_'.code ||
                c == '.'.code || c == '*'.code
            ) append(c.toChar()) else append('%').append(hex[c shr 4]).append(hex[c and 0xF])
        }
    }

    fun wechatScheme(actionCode: String): String =
        "weixin://dl/business/?appid=$WECHAT_APPID&path=$MINI_PATH" +
            "&query=${queryEncode("code=$actionCode")}&env_version=release"

    fun alipayScheme(actionCode: String): String =
        "alipays://platformapi/startapp?appId=$ALIPAY_APPID&page=$MINI_PATH" +
            "&query=${queryEncode("code=$actionCode")}"

    /** 支付宝 Universal Link 兜底：浏览器打开后再拉起支付宝。 */
    fun alipayH5(actionCode: String): String =
        "https://ulink.alipay.com/?scheme=${queryEncode(alipayScheme(actionCode))}"

    /** 微信 H5 跳板：302 → 微信 scheme（官方落地页，非微信环境同样执行跳转）。 */
    fun wechatH5(actionCode: String): String =
        "https://batch.shunshuikj.cn/scan?code=$actionCode"

    /**
     * 依次尝试：微信 scheme → 微信 H5 跳板 → 支付宝 scheme → 支付宝 H5 → 复制链接。
     * @return 是否已拉起任一渠道（false 时调用方提示"链接已复制，可在微信内打开"）
     */
    fun openInMiniProgram(context: Context, actionCode: String): Boolean {
        if (actionCode.isBlank()) return false
        val channels = listOf(
            wechatScheme(actionCode),
            wechatH5(actionCode),
            alipayScheme(actionCode),
            alipayH5(actionCode),
        )
        for (uri in channels) {
            if (tryStart(context, uri)) return true
        }
        copyToClipboard(context, wechatH5(actionCode))
        return false
    }

    private fun tryStart(context: Context, uri: String): Boolean = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess

    private fun copyToClipboard(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText("laundry", text))
    }
}
