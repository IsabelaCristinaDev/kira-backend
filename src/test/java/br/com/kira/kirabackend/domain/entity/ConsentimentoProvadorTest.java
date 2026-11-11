package br.com.kira.kirabackend.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O consentimento do Provador vale só com as duas condições juntas: não revogado e versão aceita igual
 * à do termo atual. Os testes passam pelas quatro combinações para que trocar o "e" por "ou" seja pego.
 */
class ConsentimentoProvadorTest {

    private static final String VERSAO_ATUAL = "v2";

    private ConsentimentoProvador consentimento(String versaoAceita, LocalDateTime revogadoEm) {
        ConsentimentoProvador consentimento = new ConsentimentoProvador();
        consentimento.setVersao(versaoAceita);
        consentimento.setAceitoEm(LocalDateTime.of(2026, 11, 1, 10, 0));
        consentimento.setRevogadoEm(revogadoEm);
        return consentimento;
    }

    @Test
    @DisplayName("Aceitou a versão atual e não revogou: o consentimento está ativo")
    void versaoAtualSemRevogacaoEstaAtivo() {
        // Arrange
        ConsentimentoProvador consentimento = consentimento(VERSAO_ATUAL, null);

        // Act
        boolean ativo = consentimento.estaAtivo(VERSAO_ATUAL);

        // Assert
        assertTrue(ativo);
    }

    @Test
    @DisplayName("Aceitou a versão atual mas revogou depois: não está ativo")
    void versaoAtualRevogadaNaoEstaAtivo() {
        ConsentimentoProvador consentimento = consentimento(VERSAO_ATUAL, LocalDateTime.of(2026, 11, 5, 9, 0));

        assertFalse(consentimento.estaAtivo(VERSAO_ATUAL));
    }

    @Test
    @DisplayName("Aceitou uma versão antiga do termo: precisa aceitar de novo, mesmo sem ter revogado")
    void versaoAntigaNaoEstaAtivo() {
        ConsentimentoProvador consentimento = consentimento("v1", null);

        assertFalse(consentimento.estaAtivo(VERSAO_ATUAL));
    }

    @Test
    @DisplayName("Versão antiga e revogado: não está ativo")
    void versaoAntigaERevogadoNaoEstaAtivo() {
        ConsentimentoProvador consentimento = consentimento("v1", LocalDateTime.of(2026, 11, 5, 9, 0));

        assertFalse(consentimento.estaAtivo(VERSAO_ATUAL));
    }

    @Test
    @DisplayName("A versão é comparada exatamente: \"V2\" maiúsculo não vale como \"v2\"")
    void versaoComparadaExatamente() {
        ConsentimentoProvador consentimento = consentimento("V2", null);

        assertFalse(consentimento.estaAtivo(VERSAO_ATUAL));
    }

    @Test
    @DisplayName("Registro sem versão aceita não está ativo e não lança exceção")
    void semVersaoAceitaNaoEstaAtivo() {
        ConsentimentoProvador consentimento = consentimento(null, null);

        assertFalse(consentimento.estaAtivo(VERSAO_ATUAL));
    }
}
