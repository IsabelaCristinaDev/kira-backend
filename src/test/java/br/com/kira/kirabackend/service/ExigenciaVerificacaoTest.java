package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.enums.StatusVerificacao;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.exception.VerificacaoObrigatoriaException;
import br.com.kira.kirabackend.repository.VerificacaoIdentidadeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class ExigenciaVerificacaoTest {

    private static final UUID VERIFICADA = UUID.randomUUID();
    private static final UUID SEM_VERIFICACAO = UUID.randomUUID();

    @Mock
    private VerificacaoIdentidadeRepository repository;

    @Nested
    @DisplayName("Com VERIFICACAO_OBRIGATORIA=false")
    class Desligada {

        private ExigenciaVerificacao exigencia() {
            return new ExigenciaVerificacao(repository, false);
        }

        @Test
        @DisplayName("Avisa no log, ao subir, que a verificação está desligada")
        void avisaNoLog(CapturedOutput saida) {
            exigencia().avisarSeDesligada();

            assertTrue(saida.getOut().contains("WARN"));
            assertTrue(saida.getOut().contains("VERIFICACAO_OBRIGATORIA=false"));
        }

        @Test
        @DisplayName("Cliente e estúdio sem verificação passam, sem consultar o banco")
        void tudoLiberado() {
            ExigenciaVerificacao exigencia = exigencia();

            assertDoesNotThrow(() -> exigencia.exigirClienteVerificada(SEM_VERIFICACAO));
            assertDoesNotThrow(() -> exigencia.exigirEstudioVerificado(SEM_VERIFICACAO));
            assertTrue(exigencia.naVitrine().test(SEM_VERIFICACAO));
            verifyNoInteractions(repository);
        }
    }

    @Nested
    @DisplayName("Com VERIFICACAO_OBRIGATORIA=true")
    class Ligada {

        private ExigenciaVerificacao exigencia() {
            return new ExigenciaVerificacao(repository, true);
        }

        @Test
        @DisplayName("Não avisa nada no log")
        void semAviso(CapturedOutput saida) {
            exigencia().avisarSeDesligada();

            assertFalse(saida.getOut().contains("VERIFICACAO_OBRIGATORIA"));
        }

        @Test
        @DisplayName("Cliente sem verificação é recusada com a exceção própria (403)")
        void clienteSemVerificacao() {
            when(repository.existsByUsuarioIdAndStatus(SEM_VERIFICACAO, StatusVerificacao.VERIFIED)).thenReturn(false);
            ExigenciaVerificacao exigencia = exigencia();

            assertThrows(VerificacaoObrigatoriaException.class, () -> exigencia.exigirClienteVerificada(SEM_VERIFICACAO));
        }

        @Test
        @DisplayName("Cliente verificada passa")
        void clienteVerificada() {
            when(repository.existsByUsuarioIdAndStatus(VERIFICADA, StatusVerificacao.VERIFIED)).thenReturn(true);

            assertDoesNotThrow(() -> exigencia().exigirClienteVerificada(VERIFICADA));
        }

        @Test
        @DisplayName("Estúdio sem verificação não recebe agendamento: a cliente não tem o que verificar, então é regra de negócio")
        void estudioSemVerificacao() {
            when(repository.existsByUsuarioIdAndStatus(SEM_VERIFICACAO, StatusVerificacao.VERIFIED)).thenReturn(false);
            ExigenciaVerificacao exigencia = exigencia();

            assertThrows(RegraDeNegocioException.class, () -> exigencia.exigirEstudioVerificado(SEM_VERIFICACAO));
        }

        @Test
        @DisplayName("A vitrine mostra só os verificados")
        void vitrine() {
            when(repository.idsDeUsuariosComStatus(StatusVerificacao.VERIFIED)).thenReturn(Set.of(VERIFICADA));

            Predicate<UUID> naVitrine = exigencia().naVitrine();

            assertTrue(naVitrine.test(VERIFICADA));
            assertFalse(naVitrine.test(SEM_VERIFICACAO));
        }
    }
}
