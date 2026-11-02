package com.example.myfocusblocker

import com.example.myfocusblocker.util.GuardianCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardianCodeTest {

    @Test fun `genera codigos con formato legible y sin caracteres ambiguos`() {
        repeat(50) {
            val code = GuardianCode.generate()
            assertEquals(9, code.length)            // XXXX-XXXX
            assertEquals('-', code[4])
            val letters = GuardianCode.normalize(code)
            assertEquals(8, letters.length)
            // Nada de 0/O/1/I/L, que se confunden al dictarlos por teléfono.
            assertTrue(letters.none { it in "01ILO" })
        }
    }

    @Test fun `genera codigos distintos`() {
        val codes = (1..50).map { GuardianCode.generate() }.toSet()
        assertTrue("Debería haber muchos códigos distintos", codes.size > 45)
    }

    @Test fun `acepta el codigo con cualquier formato`() {
        val code = "ABCD-2345"
        val hash = GuardianCode.hash(code)
        assertTrue(GuardianCode.matches("ABCD-2345", hash))
        assertTrue(GuardianCode.matches("abcd2345", hash))
        assertTrue(GuardianCode.matches(" AbCd - 2345 ", hash))
    }

    @Test fun `rechaza un codigo incorrecto`() {
        val hash = GuardianCode.hash("ABCD-2345")
        assertFalse(GuardianCode.matches("ABCD-2346", hash))
        assertFalse(GuardianCode.matches("", hash))
    }

    @Test fun `el hash no revela el codigo`() {
        val code = "ABCD-2345"
        val hash = GuardianCode.hash(code)
        assertEquals(64, hash.length) // SHA-256 en hexadecimal
        assertNotEquals(code, hash)
        assertFalse(hash.contains("ABCD", ignoreCase = true))
    }
}
