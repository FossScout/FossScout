package com.fossscout.core.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoTest {
    private val crypto = BackupCrypto()
    private val plaintext = "{\"format\":\"fossscout-backup\"}"

    @Test
    fun `encrypted backup round trips with the correct passphrase`() {
        val encrypted = crypto.encrypt(plaintext, "correct horse".toCharArray())

        assertTrue(crypto.isEncrypted(encrypted))
        assertEquals(plaintext, crypto.decrypt(encrypted, "correct horse".toCharArray()))
    }

    @Test
    fun `wrong passphrase fails without returning plaintext`() {
        val encrypted = crypto.encrypt(plaintext, "correct horse".toCharArray())

        val error = assertThrows(BackupEncryptionException::class.java) {
            crypto.decrypt(encrypted, "wrong horse".toCharArray())
        }
        assertEquals(BackupEncryptionReason.WRONG_PASSPHRASE_OR_TAMPERED, error.reason)
    }

    @Test
    fun `tampered ciphertext fails authentication`() {
        val encrypted = crypto.encrypt(plaintext, "correct horse".toCharArray())
        val marker = "\"ciphertext\":\""
        val index = encrypted.indexOf(marker) + marker.length
        val original = encrypted[index]
        val replacement = if (original == 'A') 'B' else 'A'
        val tampered = encrypted.substring(0, index) + replacement +
            encrypted.substring(index + 1)

        val error = assertThrows(BackupEncryptionException::class.java) {
            crypto.decrypt(tampered, "correct horse".toCharArray())
        }
        assertEquals(BackupEncryptionReason.WRONG_PASSPHRASE_OR_TAMPERED, error.reason)
    }

    @Test
    fun `plain backup is not reported as encrypted`() {
        assertFalse(crypto.isEncrypted(plaintext))
        assertFalse(crypto.isEncrypted("not json"))
    }

    @Test
    fun `empty passphrase is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            crypto.encrypt(plaintext, CharArray(0))
        }
    }
}
