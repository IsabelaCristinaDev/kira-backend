package br.com.kira.kirabackend.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class HashDeTokenTest {

    @Test
    @DisplayName("Gera o SHA-256 em hexadecimal minúsculo")
    void geraSha256EmHexadecimal() {
        // Valor de referência do SHA-256 para "abc" (FIPS 180-2)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", HashDeToken.sha256("abc"));
    }

    @Test
    @DisplayName("O mesmo token gera sempre o mesmo hash, e tokens diferentes geram hashes diferentes")
    void mesmoTokenMesmoHash() {
        assertEquals(HashDeToken.sha256("token-a"), HashDeToken.sha256("token-a"));
        assertNotEquals(HashDeToken.sha256("token-a"), HashDeToken.sha256("token-b"));
    }
}
