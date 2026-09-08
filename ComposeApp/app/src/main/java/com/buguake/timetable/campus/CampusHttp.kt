package com.buguake.timetable.campus

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** 校园模块共用的 HTTP 通道（okhttp 表单 POST），供各功能复用。 */
private val httpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build()

suspend fun httpPost(
    url: String,
    form: Map<String, String>,
    headers: Map<String, String>,
): String = withContext(Dispatchers.IO) {
    val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
    val rb = Request.Builder().url(url).post(body)
    headers.forEach { (k, v) -> rb.header(k, v) }
    httpClient.newCall(rb.build()).execute().use { resp ->
        if (!resp.isSuccessful) throw YunmeiException("服务器返回 HTTP ${resp.code}")
        resp.body?.string() ?: ""
    }
}
