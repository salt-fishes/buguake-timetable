# R8 规则（性能方案 P0-1）

# WebView JS 桥：网页 JS 按方法名反射调用桥方法，注解方法必须原样保留
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# OkHttp/Okio 平台探测引用的可选 TLS 提供方（缺省仅为警告）
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# 调试探针残留不入包
-assumenosideeffects class kotlinx.coroutines.DebugKt {
    public static *** debug(...);
}
