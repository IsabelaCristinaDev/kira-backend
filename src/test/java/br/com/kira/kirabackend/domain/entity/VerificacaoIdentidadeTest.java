package br.com.kira.kirabackend.domain.entity;

import br.com.kira.kirabackend.domain.enums.StatusVerificacao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerificacaoIdentidadeTest {

    @Test
    @DisplayName("Ao salvar, preenche as datas e começa como PENDING com a lista de motivos vazia")
    void aoPersistirPreencheValoresIniciais() {
        VerificacaoIdentidade verificacao = new VerificacaoIdentidade();
        verificacao.setStatus(null);
        verificacao.setMotivos(null);

        verificacao.aoPersistir();

        assertEquals(StatusVerificacao.PENDING, verificacao.getStatus());
        assertTrue(verificacao.getMotivos().isEmpty());
        assertNotNull(verificacao.getDataCriacao());
        assertEquals(verificacao.getDataCriacao(), verificacao.getDataAtualizacao());
    }

    @Test
    @DisplayName("Ao salvar, mantém o status e os motivos já definidos")
    void aoPersistirMantemValoresDefinidos() {
        VerificacaoIdentidade verificacao = new VerificacaoIdentidade();
        verificacao.setStatus(StatusVerificacao.REJECTED);
        verificacao.setMotivos(List.of("FACE_MISMATCH"));

        verificacao.aoPersistir();

        assertEquals(StatusVerificacao.REJECTED, verificacao.getStatus());
        assertEquals(List.of("FACE_MISMATCH"), verificacao.getMotivos());
    }

    @Test
    @DisplayName("Ao atualizar, muda só a data de atualização")
    void aoAtualizarMudaSoDataDeAtualizacao() {
        VerificacaoIdentidade verificacao = new VerificacaoIdentidade();
        LocalDateTime criacao = LocalDateTime.of(2026, 1, 1, 10, 0);
        verificacao.setDataCriacao(criacao);
        verificacao.setDataAtualizacao(criacao);

        verificacao.aoAtualizar();

        assertEquals(criacao, verificacao.getDataCriacao());
        assertTrue(verificacao.getDataAtualizacao().isAfter(criacao));
    }

    @Test
    @DisplayName("A tabela não tem campo para imagem nem para dados lidos pelo OCR (LGPD)")
    void naoGuardaImagemNemDadosPessoais() {
        List<String> proibidos = List.of("imagem", "foto", "selfie", "documentoimagem", "cpf", "nome", "nascimento");

        for (Field campo : VerificacaoIdentidade.class.getDeclaredFields()) {
            String nome = campo.getName().toLowerCase(Locale.ROOT);
            assertTrue(proibidos.stream().noneMatch(nome::contains), "Campo proibido: " + campo.getName());
            assertTrue(campo.getType() != byte[].class, "Campo binário proibido: " + campo.getName());
        }
    }
}
