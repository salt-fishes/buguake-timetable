package com.buguake.timetable.campus

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 本机静态加密（零新依赖）：用 Android Keystore 里的 AES-256/GCM 密钥加密字符串。
 *
 * 密钥只存在于系统 Keystore（应用卸载即失效），不落盘、不可导出；
 * 加密结果格式：`enc:<base64(iv(12B) + ciphertext)>`。
 *
 * Keystore 不可用（极少数定制 ROM）时降级为 `raw:<明文>`，功能不中断，
 * 仅失去静态加密保护——不因此让用户无法开门。
 */
class LocalCrypto {

    fun encrypt(plain: String): String = try {
        val key = secretKey() ?: return RAW + plain
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        ENC + Base64.encodeToString(cipher.iv + body, Base64.NO_WRAP)
    } catch (t: Throwable) {
        RAW + plain
    }

    /** 解密失败（密钥失效/数据损坏）返回 null，调用方按"无缓存"处理。 */
    fun decrypt(value: String): String? = when {
        value.startsWith(RAW) -> value.removePrefix(RAW)
        value.startsWith(ENC) -> runCatching {
            val bytes = Base64.decode(value.removePrefix(ENC), Base64.NO_WRAP)
            require(bytes.size > IV_LENGTH)
            val iv = bytes.copyOfRange(0, IV_LENGTH)
            val body = bytes.copyOfRange(IV_LENGTH, bytes.size)
            val key = secretKey() ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            String(cipher.doFinal(body), Charsets.UTF_8)
        }.getOrNull()
        // 旧版明文（未加密）数据：直接当明文读，下次写入时自动升级为密文
        else -> value
    }

    private fun secretKey(): SecretKey? = runCatching {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey ?: generateKey()
    }.getOrNull()

    private fun generateKey(): SecretKey =
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    // 不做生物识别绑定：解密由应用自行控制（可选门闸在 BiometricGate）
                    .setUserAuthenticationRequired(false)
                    .build()
            )
        }.generateKey()

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "buguake_campus_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val ENC = "enc:"
        const val RAW = "raw:"
    }
}
