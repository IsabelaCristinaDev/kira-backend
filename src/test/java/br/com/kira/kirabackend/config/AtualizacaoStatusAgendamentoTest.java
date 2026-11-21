package br.com.kira.kirabackend.config;

import br.com.kira.kirabackend.domain.enums.StatusAgendamento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AtualizacaoStatusAgendamentoTest {

    private static final String REGRA_ANTIGA = "CHECK (((status)::text = ANY (ARRAY['PENDENTE'::character varying,"
            + " 'CONFIRMADO'::character varying, 'CANCELADO'::character varying, 'CONCLUIDO'::character varying,"
            + " 'REAGENDADO'::character varying])))";

    @Mock
    private JdbcTemplate jdbc;

    @InjectMocks
    private AtualizacaoStatusAgendamento atualizacao;

    private void regraAtual(List<String> definicao) {
        when(jdbc.queryForList(anyString(), eq(String.class), eq(AtualizacaoStatusAgendamento.REGRA)))
                .thenReturn(definicao);
    }

    @Test
    @DisplayName("Regra antiga, sem CHEGADA_CONFIRMADA: é refeita com todos os status")
    void refazRegraAntiga() {
        regraAtual(List.of(REGRA_ANTIGA));

        atualizacao.run(new DefaultApplicationArguments());

        ArgumentCaptor<String> comandos = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(2)).execute(comandos.capture());
        assertEquals("alter table agendamento drop constraint if exists agendamento_status_check",
                comandos.getAllValues().get(0));
        String nova = comandos.getAllValues().get(1);
        assertTrue(nova.contains("'CHEGADA_CONFIRMADA'"), nova);
        assertTrue(nova.contains("'PENDENTE'") && nova.contains("'REAGENDADO'"), nova);
    }

    @Test
    @DisplayName("A lista fixa da regra tem exatamente os status do enum (status novo precisa entrar nela)")
    void listaFixaIgualAoEnum() {
        List<String> daLista = Arrays.stream(AtualizacaoStatusAgendamento.STATUS_ACEITOS.split(","))
                .map(s -> s.trim().replace("'", ""))
                .sorted()
                .toList();
        List<String> doEnum = Arrays.stream(StatusAgendamento.values()).map(Enum::name).sorted().toList();

        assertEquals(doEnum, daLista);
    }

    @Test
    @DisplayName("Regra que já aceita todos os status: não mexe na tabela")
    void regraEmDia() {
        regraAtual(List.of(REGRA_ANTIGA.replace("'CONFIRMADO'::character varying,",
                "'CONFIRMADO'::character varying, 'CHEGADA_CONFIRMADA'::character varying,")));

        atualizacao.run(new DefaultApplicationArguments());

        verify(jdbc, never()).execute(anyString());
    }

    @Test
    @DisplayName("Sem a regra: cria uma com todos os status")
    void semRegra() {
        regraAtual(List.of());

        atualizacao.run(new DefaultApplicationArguments());

        verify(jdbc, times(2)).execute(anyString());
    }
}
