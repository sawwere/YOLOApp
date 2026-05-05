package com.sawwere.yoloapp.core.system

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.MasterKeys
import java.security.KeyStore
import javax.crypto.SecretKey

class KeyStoreManager(private val context: Context) {
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    private val databaseKey = MasterKeys.getOrCreate(
        KeyGenParameterSpec.Builder(
            DATABASE_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).apply {
            setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            setKeySize(256)
            setUserAuthenticationRequired(false)
            setInvalidatedByBiometricEnrollment(true)
            setUnlockedDeviceRequired(false)
            setIsStrongBoxBacked(false)
        }.build()
    )

    companion object {
        private const val DATABASE_KEY_ALIAS = "signature_db_encryption_key_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }

    fun getDatabaseKey(): ByteArray {
        return try {
            val secretKey = getSecretKey()
            secretKey.encoded
        } catch (e: Exception) {
            databaseKey.toByteArray()
        }
    }


    private fun getSecretKey(): SecretKey {
        if (!keyStore.containsAlias(DATABASE_KEY_ALIAS)) {
            throw IllegalStateException("Database encryption key not found in KeyStore")
        }
        return keyStore.getKey(DATABASE_KEY_ALIAS, null) as SecretKey
    }
}