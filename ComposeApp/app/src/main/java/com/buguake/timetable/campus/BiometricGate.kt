package com.buguake.timetable.campus

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 开门前的可选门闸（默认关闭，用户在宿舍开门页自行开启）。
 *
 * 零新依赖，两条原生路径：
 * - 有指纹/面容（API 28+）→ 平台 [BiometricPrompt] 对话框；
 * - 只有锁屏密码（含 API 26/27）→ 系统「确认锁屏密码」界面（[KeyguardManager]）。
 *
 * 这样既不需要把 MainActivity 改成 FragmentActivity，也不需要 androidx.biometric；
 * 平台 BiometricPrompt 只申请生物识别（不叠加 DEVICE_CREDENTIAL），
 * 从而避开"允许设备凭据时 CancellationSignal 必须为 null"的平台约束。
 */
object BiometricGate {

    enum class Availability {
        /** 有已录入的指纹/面容，用生物识别对话框。 */
        BIOMETRIC,

        /** 无生物识别但设了锁屏密码，用系统密码界面。 */
        DEVICE_CREDENTIAL,

        /** 两者都没有：不启用门闸（也不该把用户挡在门外）。 */
        NONE,
    }

    fun availability(context: Context): Availability {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && biometricEnrolled(context)) {
            return Availability.BIOMETRIC
        }
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return if (keyguard?.isDeviceSecure == true) Availability.DEVICE_CREDENTIAL else Availability.NONE
    }

    /** 本机是否具备可用的验证方式（开启门闸的前置条件）。 */
    fun canAuthenticate(context: Context): Boolean = availability(context) != Availability.NONE

    /** 需要走系统锁屏密码界面（而非生物识别对话框）。 */
    fun needsLegacyPrompt(context: Context): Boolean =
        availability(context) == Availability.DEVICE_CREDENTIAL

    /** 系统「确认锁屏密码」Intent；无锁屏密码时返回 null。 */
    fun legacyPromptIntent(context: Context): Intent? {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            ?: return null
        if (keyguard.isDeviceSecure != true) return null
        return keyguard.createConfirmDeviceCredentialIntent("开门验证", "验证后开启宿舍门锁")
    }

    /**
     * 生物识别对话框：通过返回 true，取消/失败返回 false。
     * 仅当 [availability] 为 [Availability.BIOMETRIC] 时调用。
     */
    suspend fun authenticate(activity: Activity, title: String, subtitle: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return suspendCancellableCoroutine { cont ->
            val executor = ContextCompat.getMainExecutor(activity)
            val cancellation = CancellationSignal()
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    if (cont.isActive) cont.resume(false)
                }
            }
            runCatching {
                val prompt = BiometricPrompt.Builder(activity)
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setNegativeButton("取消", executor) { _, _ ->
                        if (cont.isActive) cont.resume(false)
                    }
                    .build()
                prompt.authenticate(cancellation, executor, callback)
                cont.invokeOnCancellation { cancellation.cancel() }
            }.onFailure {
                if (cont.isActive) cont.resume(false)
            }
        }
    }

    private fun biometricEnrolled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bm = context.getSystemService(android.hardware.biometrics.BiometricManager::class.java)
            return bm?.canAuthenticate(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
                android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val bm = context.getSystemService(android.hardware.biometrics.BiometricManager::class.java)
            return bm?.canAuthenticate() == android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS
        }
        val fm = context.getSystemService(android.hardware.fingerprint.FingerprintManager::class.java)
        @Suppress("DEPRECATION")
        return fm?.isHardwareDetected == true && fm.hasEnrolledFingerprints()
    }
}
