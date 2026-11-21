package br.com.kira.kirabackend;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

/** Falha de propósito: confere que o CI impede o merge na main. Este PR é fechado sem juntar. */
class CiDeveBarrarTest {

    @Test
    void falhaDePropósito() {
        fail("Teste falhando de propósito para conferir que o CI barra o PR");
    }
}
