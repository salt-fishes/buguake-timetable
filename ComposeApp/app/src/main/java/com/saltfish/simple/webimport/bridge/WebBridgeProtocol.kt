package com.saltfish.simple.webimport.bridge

import org.json.JSONObject

/**
 * JS 桥协议：与拾光课程表适配脚本生态（shiguang_warehouse）完全兼容的通信契约。
 *
 * 桥初始化脚本与回调构造逐字复制自拾光课程表
 * （XingHeYuZhuan/shiguangschedule，Apache License 2.0，Copyright 2025 XingHeYuZhuan），
 * 本项目仅调整包名与注释；依据 Apache-2.0 的修改声明要求特此注明。
 * 参见仓库 THIRD_PARTY_NOTICES.md。
 */

/** JS → Native 消息外壳：{action, callbackId?, payload?}，payload 为 JSON 字符串。 */
data class JsBridgeMessage(
    val action: String,
    val callbackId: String? = null,
    val payload: String? = null,
) {
    companion object {
        fun parse(jsonString: String): JsBridgeMessage? = runCatching {
            val o = JSONObject(jsonString)
            JsBridgeMessage(
                action = o.optString("action", ""),
                callbackId = o.optString("callbackId", "").takeIf { it.isNotBlank() },
                payload = o.optString("payload", "").takeIf { it.isNotBlank() },
            )
        }.getOrNull()
    }
}

/** 构造响应 JS 端 Promise 的执行脚本（[resultRawJs] 为 JS 字面量）。 */
fun buildJsCallbackScript(callbackId: String, isSuccess: Boolean, resultRawJs: String): String =
    "window._shiguangNativeCallback('$callbackId', $isSuccess, $resultRawJs);"

// ---- 各动作的 payload（JSON 字符串 → 字段） ----

data class ShowToastPayload(val message: String)

data class ShowAlertPayload(
    val titleText: String,
    val contentText: String,
    val confirmText: String?,
)

data class ShowPromptPayload(
    val titleText: String,
    val tipText: String,
    val defaultText: String,
    val validatorJsFunction: String?,
)

data class ShowSingleSelectionPayload(
    val titleText: String,
    val itemsJsonString: String,
    val defaultSelectedIndex: Int,
)

data class SaveCoursesPayload(val coursesJsonString: String)
data class SaveConfigPayload(val configJsonString: String)
data class SaveTimeSlotsPayload(val timeSlotsJsonString: String)

private inline fun <reified T> parse(jsonString: String?, map: (JSONObject) -> T): T? =
    jsonString?.let { s -> runCatching { map(JSONObject(s)) }.getOrNull() }

fun parseShowToast(payload: String?): ShowToastPayload? =
    parse(payload) { ShowToastPayload(it.optString("message", "")) }

fun parseShowAlert(payload: String?): ShowAlertPayload? =
    parse(payload) {
        ShowAlertPayload(
            titleText = it.optString("titleText", ""),
            contentText = it.optString("contentText", ""),
            confirmText = it.optString("confirmText", "").takeIf { t -> t.isNotBlank() },
        )
    }

fun parseShowPrompt(payload: String?): ShowPromptPayload? =
    parse(payload) {
        ShowPromptPayload(
            titleText = it.optString("titleText", ""),
            tipText = it.optString("tipText", ""),
            defaultText = it.optString("defaultText", ""),
            validatorJsFunction = it.optString("validatorJsFunction", "").takeIf { t -> t.isNotBlank() },
        )
    }

fun parseShowSingleSelection(payload: String?): ShowSingleSelectionPayload? =
    parse(payload) {
        ShowSingleSelectionPayload(
            titleText = it.optString("titleText", ""),
            itemsJsonString = it.optString("itemsJsonString", "[]"),
            defaultSelectedIndex = it.optInt("defaultSelectedIndex", -1),
        )
    }

fun parseSaveCourses(payload: String?): SaveCoursesPayload? =
    parse(payload) { SaveCoursesPayload(it.optString("coursesJsonString", "")) }

fun parseSaveConfig(payload: String?): SaveConfigPayload? =
    parse(payload) { SaveConfigPayload(it.optString("configJsonString", "")) }

fun parseSaveTimeSlots(payload: String?): SaveTimeSlotsPayload? =
    parse(payload) { SaveTimeSlotsPayload(it.optString("timeSlotsJsonString", "")) }

/** 解析单选列表的 items JSON（字符串数组，宽松：非法项过滤）。 */
fun parseSelectionItems(itemsJsonString: String): List<String> = runCatching {
    val arr = org.json.JSONArray(itemsJsonString)
    (0 until arr.length()).mapNotNull { i ->
        when (val v = arr.get(i)) {
            is String -> v
            is Number, is Boolean -> v.toString()
            is org.json.JSONObject -> v.toString()
            else -> null
        }
    }
}.getOrDefault(emptyList())

/**
 * JS 端桥挂载脚本（逐字复制自拾光课程表 WebBridgeProtocol.kt 的 JS_BRIDGE_INIT，
 * Apache License 2.0）。幂等注入；Promise 语义 + callbackId 回程；
 * shiguangBridgePromise 为异步接口（弹窗/保存），shiguangBridge 为单向接口（toast/完成通知），
 * AndroidBridge* 为旧版别名。
 */
val JS_BRIDGE_INIT = """
(function() {
    if (window._shiguangBridgeInjected) return;
    window._shiguangBridgeInjected = true;

    var callbacks = {};
    var callbackCounter = 0;

    /**
     * 动态获取当前可用的 Native 发送管道
     * 不在初始化时死板锁定，避免空网站/初始化极早期 _shiguangNativeBridge 还没准备好导致失效
     */
    function postRawMessage(msg) {
        if (window._shiguangNativeBridge && typeof window._shiguangNativeBridge.postMessage === 'function') {
            window._shiguangNativeBridge.postMessage(msg);
            return;
        }
        if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.shiguangBridge) {
            window.webkit.messageHandlers.shiguangBridge.postMessage(msg);
            return;
        }
        if (typeof window.cefQuery === 'function') {
            window.cefQuery({ request: msg });
            return;
        }
        console.warn("[ShiguangBridge] Native bridge unavailable:", msg);
    }

    /**
     * 通用底层管道：将请求统一转为 JSON 发送给 Native
     */
    function postMessageToNative(action, payload, callbackId) {
        var msg = JSON.stringify({
            action: action,
            callbackId: callbackId || null,
            payload: payload ? JSON.stringify(payload) : null
        });

        postRawMessage(msg);
    }

    /**
     * Native 异步逻辑完成后的响应全局入口
     */
    window._shiguangNativeCallback = function(callbackId, isSuccess, result) {
        var cb = callbacks[callbackId];
        if (cb) {
            if (isSuccess) {
                cb.resolve(result);
            } else {
                cb.reject(result);
            }
            delete callbacks[callbackId];
        }
    };

    // 1. 异步 Promise 调用的 JS 接口
    var shiguangBridgePromise = {
        showAlert: function(titleText, contentText, confirmText) {
            return new Promise(function(resolve, reject) {
                var id = 'cb_' + (++callbackCounter) + '_' + Date.now();
                callbacks[id] = { resolve: resolve, reject: reject };
                postMessageToNative('showAlert', {
                    titleText: titleText || '',
                    contentText: contentText || '',
                    confirmText: confirmText || null
                }, id);
            });
        },
        showPrompt: function(titleText, tipText, defaultText, validatorJsFunction) {
            return new Promise(function(resolve, reject) {
                var id = 'cb_' + (++callbackCounter) + '_' + Date.now();
                callbacks[id] = { resolve: resolve, reject: reject };
                postMessageToNative('showPrompt', {
                    titleText: titleText || '',
                    tipText: tipText || '',
                    defaultText: defaultText || '',
                    validatorJsFunction: validatorJsFunction || ''
                }, id);
            });
        },
        showSingleSelection: function(titleText, items, defaultSelectedIndex) {
            return new Promise(function(resolve, reject) {
                var id = 'cb_' + (++callbackCounter) + '_' + Date.now();
                callbacks[id] = { resolve: resolve, reject: reject };
                var itemsJson = (typeof items === 'string') ? items : JSON.stringify(items || []);
                postMessageToNative('showSingleSelection', {
                    titleText: titleText || '',
                    itemsJsonString: itemsJson,
                    defaultSelectedIndex: defaultSelectedIndex !== undefined ? defaultSelectedIndex : -1
                }, id);
            });
        },
        saveImportedCourses: function(coursesJsonString) {
            return new Promise(function(resolve, reject) {
                var id = 'cb_' + (++callbackCounter) + '_' + Date.now();
                callbacks[id] = { resolve: resolve, reject: reject };
                postMessageToNative('saveImportedCourses', { coursesJsonString: coursesJsonString }, id);
            });
        },
        saveCourseConfig: function(configJsonString) {
            return new Promise(function(resolve, reject) {
                var id = 'cb_' + (++callbackCounter) + '_' + Date.now();
                callbacks[id] = { resolve: resolve, reject: reject };
                postMessageToNative('saveCourseConfig', { configJsonString: configJsonString }, id);
            });
        },
        savePresetTimeSlots: function(timeSlotsJsonString) {
            return new Promise(function(resolve, reject) {
                var id = 'cb_' + (++callbackCounter) + '_' + Date.now();
                callbacks[id] = { resolve: resolve, reject: reject };
                postMessageToNative('savePresetTimeSlots', { timeSlotsJsonString: timeSlotsJsonString }, id);
            });
        }
    };

    // 2. 单向/同步调用的 JS 接口
    var shiguangBridge = {
        showToast: function(message) {
            postMessageToNative('showToast', { message: message });
        },
        notifyTaskCompletion: function() {
            postMessageToNative('notifyTaskCompletion');
        }
    };

    // 3. 挂载全局对象
    window.shiguangBridgePromise = shiguangBridgePromise;
    window.shiguangBridge = shiguangBridge;

    // 旧版兼容接口
    window.AndroidBridgePromise = shiguangBridgePromise;
    window.AndroidBridge = shiguangBridge;
})();
""".trimIndent()
