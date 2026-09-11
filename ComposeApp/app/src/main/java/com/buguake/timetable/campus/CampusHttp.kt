package com.buguake.timetable.campus

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 校园模块共用的 HTTP 通道（okhttp 表单 POST），供各功能复用。 */
private val httpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build()

/**
 * 表单 POST，异常按语义分型：
 * - [YunmeiAuthException]：HTTP 401/403，登录态确实失效（允许降级清凭据）；
 * - [YunmeiNetworkException]：连不上/超时，**不允许**清凭据，只标记离线等待重试；
 * - [YunmeiException]：其余服务端错误。
 */
suspend fun httpPost(
    url: String,
    form: Map<String, String>,
    headers: Map<String, String>,
): String = withContext(Dispatchers.IO) {
    val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
    val rb = Request.Builder().url(url).post(body)
    headers.forEach { (k, v) -> rb.header(k, v) }
    try {
        httpClient.newCall(rb.build()).execute().use { resp ->
            when {
                resp.isSuccessful -> resp.body?.string() ?: ""
                resp.code == 401 || resp.code == 403 ->
                    throw YunmeiAuthException("登录已失效（HTTP ${resp.code}）")
                else -> throw YunmeiException("服务器返回 HTTP ${resp.code}")
            }
        }
    } catch (e: YunmeiException) {
        throw e
    } catch (e: IOException) {
        throw YunmeiNetworkException("网络请求失败：${e.message ?: "连接异常"}")
    }
}
