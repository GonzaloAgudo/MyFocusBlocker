package com.example.myfocusblocker.util

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Código de emergencia ("de guardián"): se genera, se le da a otra persona y sirve para
 * desactivar las protecciones si te quedas atrapado.
 *
 * Solo se guarda su hash SHA-256, nunca el código en claro: así no se puede recuperar
 * abriendo los datos de la app.
 */
object GuardianCode {

    /** Sin caracteres ambiguos (0/O, 1/I/L) para poder dictarlo por teléfono. */
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    private const val LENGTH = 8

    /** Genera un código nuevo con formato XXXX-XXXX. */
    fun generate(): String {
        val random = SecureRandom()
        val raw = buildString {
            repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }
        return raw.chunked(4).joinToString("-")
    }

    /** Ignora guiones, espacios y mayúsculas/minúsculas al comparar. */
    fun normalize(code: String): String = code.uppercase().filter { it.isLetterOrDigit() }

    fun hash(code: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(normalize(code).toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun matches(input: String, expectedHash: String): Boolean =
        hash(input).equals(expectedHash, ignoreCase = true)
}
