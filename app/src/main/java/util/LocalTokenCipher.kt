package util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Local-only AES-256-GCM encryption helper backed by Android Keystore (API 26+).
 *
 * Used to protect optional short-lived OAuth access tokens stored in DataStore so
 * plaintext Bearer credentials are never written to disk. Falls back to an in-memory
 * ephemeral AES-256-GCM key only in non-Android JVM unit-test environments where
 * `AndroidKeyStore` is unavailable.
 */
object LocalTokenCipher {

    private const val TAG = "LocalTokenCipher"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "sonora_local_oauth_token_aes_v1"
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val ENCRYPTED_PREFIX = "enc:v1:"

    private val lock = Any()
    private val secureRandom = SecureRandom()

    @Volatile
    private var jvmFallbackKey: SecretKey? = null

    fun isEncryptedPayload(value: String?): Boolean {
        return !value.isNullOrBlank() && value.startsWith(ENCRYPTED_PREFIX)
    }

    /**
     * Encrypts [plaintext] using AES-256-GCM with a freshly generated 96-bit IV.
     * Returns null if [plaintext] is blank or encryption fails (never falls back to plaintext).
     */
    fun encrypt(plaintext: String?): String? {
        val clean = plaintext?.trim().orEmpty()
        if (clean.isEmpty()) return null
        return try {
            val key = getOrCreateSecretKey() ?: return null
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            val iv = ByteArray(GCM_IV_LENGTH_BYTES)
            val usesKeystore = isAndroidKeystoreKey(key)
            if (usesKeystore) {
                // AndroidKeyStore requires keystore-generated IVs when randomizedEncryptionRequired=true
                cipher.init(Cipher.ENCRYPT_MODE, key)
            } else {
                secureRandom.nextBytes(iv)
                cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            }
            val actualIv = cipher.iv
            if (actualIv == null || actualIv.size != GCM_IV_LENGTH_BYTES) {
                return null
            }
            val ciphertext = cipher.doFinal(clean.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(actualIv.size + ciphertext.size)
            System.arraycopy(actualIv, 0, combined, 0, actualIv.size)
            System.arraycopy(ciphertext, 0, combined, actualIv.size, ciphertext.size)
            ENCRYPTED_PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed encrypting local token payload: ${t.javaClass.simpleName}")
            null
        }
    }

    /**
     * Decrypts an `enc:v1:` payload produced by [encrypt].
     * Returns null if the payload is corrupted, tampered, or cannot be decrypted.
     */
    fun decrypt(storedValue: String?): String? {
        val raw = storedValue?.trim().orEmpty()
        if (raw.isEmpty()) return null
        if (!raw.startsWith(ENCRYPTED_PREFIX)) {
            return null
        }
        return try {
            val key = getOrCreateSecretKey() ?: return null
            val payload = Base64.decode(raw.removePrefix(ENCRYPTED_PREFIX), Base64.NO_WRAP)
            if (payload.size <= GCM_IV_LENGTH_BYTES + 16) return null
            val iv = payload.copyOfRange(0, GCM_IV_LENGTH_BYTES)
            val ciphertext = payload.copyOfRange(GCM_IV_LENGTH_BYTES, payload.size)
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            val plainBytes = cipher.doFinal(ciphertext)
            String(plainBytes, Charsets.UTF_8).takeIf { it.isNotBlank() }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed decrypting local token payload: ${t.javaClass.simpleName}")
            null
        }
    }

    private fun isAndroidKeystoreKey(key: SecretKey): Boolean {
        return key.encoded == null
    }

    private fun getOrCreateSecretKey(): SecretKey? = synchronized(lock) {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) return@synchronized existing

            val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
            keyGen.init(spec)
            keyGen.generateKey()
        } catch (_: Throwable) {
            // Non-Android JVM test fallback (e.g., Robolectric without KeyStore daemon)
            jvmFallbackKey ?: runCatching {
                val keyGen = KeyGenerator.getInstance("AES")
                keyGen.init(256, secureRandom)
                keyGen.generateKey().also { jvmFallbackKey = it }
            }.getOrNull()
        }
    }
}
