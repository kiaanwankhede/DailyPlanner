package app.roz.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Remembers the backup passphrase and recovery code so the nightly backup can run unattended.
 * Both are encrypted with a key that lives in this phone's hardware keystore and never leaves it.
 */
class SecretStore(context: Context) {
    private val prefs = context.getSharedPreferences("roz_secure", Context.MODE_PRIVATE)

    fun isSetUp(): Boolean = passphrase() != null && recoveryCode() != null

    fun save(passphrase: CharArray, recoveryCode: String) {
        put("passphrase", String(passphrase))
        put("recovery", recoveryCode)
    }

    fun passphrase(): CharArray? = get("passphrase")?.toCharArray()

    fun recoveryCode(): String? = get("recovery")

    /** True when [attempt] is the remembered passphrase (the monthly check). */
    fun matches(attempt: CharArray): Boolean = passphrase()?.contentEquals(attempt) == true

    private fun put(name: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit(commit = true) {
            putString("${name}_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            putString(name, Base64.encodeToString(sealed, Base64.NO_WRAP))
        }
    }

    private fun get(name: String): String? {
        val iv = prefs.getString("${name}_iv", null) ?: return null
        val sealed = prefs.getString(name, null) ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(sealed, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "roz_backup_secrets"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
