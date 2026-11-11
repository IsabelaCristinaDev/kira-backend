package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Agendamento;
import br.com.kira.kirabackend.domain.entity.BloqueioHorario;
import br.com.kira.kirabackend.domain.entity.Funcionaria;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuadroDeHorariosTest {

    private static final LocalDateTime DEZ = LocalDateTime.of(2026, 10, 19, 10, 0);
    private static final LocalDateTime ONZE = DEZ.plusHours(1);

    private final Funcionaria ana = funcionaria("Ana");
    private final Funcionaria bia = funcionaria("Bia");

    private static Funcionaria funcionaria(String nome) {
        Funcionaria f = new Funcionaria();
        f.setId(UUID.randomUUID());
        f.setNome(nome);
        return f;
    }

    private static Agendamento agendamento(Funcionaria f, LocalDateTime inicio, LocalDateTime fim) {
        Agendamento a = new Agendamento();
        a.setId(UUID.randomUUID());
        a.setFuncionaria(f);
        a.setDataHoraInicio(inicio);
        a.setDataHoraFim(fim);
        return a;
    }

    @Test
    @DisplayName("Estúdio sem funcionárias atende um cliente por vez")
    void semFuncionariasUmPorVez() {
        QuadroDeHorarios quadro = new QuadroDeHorarios(List.of(), List.of(agendamento(null, DEZ, ONZE)), List.of());

        assertFalse(quadro.livre(null, DEZ.plusMinutes(30), ONZE.plusMinutes(30)));
        assertTrue(quadro.livre(null, ONZE, ONZE.plusHours(1)));
    }

    @Test
    @DisplayName("Sem pedido, fica com a funcionária livre que tem menos atendimentos")
    void semPedidoMenosAtendimentos() {
        LocalDateTime oito = DEZ.minusHours(2);
        QuadroDeHorarios quadro = new QuadroDeHorarios(List.of(ana, bia),
                List.of(agendamento(ana, oito, oito.plusHours(1)), agendamento(ana, DEZ, ONZE)), List.of());

        assertEquals(Optional.of(bia), quadro.funcionariaLivre(null, ONZE, ONZE.plusHours(1)));
    }

    @Test
    @DisplayName("Sem pedido e com empate, fica com a primeira livre por ordem de nome")
    void semPedidoEmpatePorNome() {
        QuadroDeHorarios semAtendimentos = new QuadroDeHorarios(List.of(ana, bia), List.of(), List.of());
        QuadroDeHorarios umCada = new QuadroDeHorarios(List.of(ana, bia),
                List.of(agendamento(ana, DEZ, ONZE), agendamento(bia, DEZ, ONZE)), List.of());

        assertEquals(Optional.of(ana), semAtendimentos.funcionariaLivre(null, DEZ, ONZE));
        assertEquals(Optional.of(ana), umCada.funcionariaLivre(null, ONZE, ONZE.plusHours(1)));
    }

    @Test
    @DisplayName("A funcionária pedida precisa estar livre")
    void pedidaPrecisaEstarLivre() {
        QuadroDeHorarios quadro = new QuadroDeHorarios(List.of(ana, bia),
                List.of(agendamento(ana, DEZ, ONZE)), List.of());

        assertEquals(Optional.empty(), quadro.funcionariaLivre(ana.getId(), DEZ, ONZE));
        assertEquals(Optional.of(bia), quadro.funcionariaLivre(bia.getId(), DEZ, ONZE));
    }

    @Test
    @DisplayName("Bloqueio deixa a funcionária indisponível")
    void bloqueioOcupa() {
        BloqueioHorario almoco = new BloqueioHorario();
        almoco.setFuncionaria(bia);
        almoco.setDataHoraInicio(DEZ);
        almoco.setDataHoraFim(ONZE);
        QuadroDeHorarios quadro = new QuadroDeHorarios(List.of(ana, bia),
                List.of(agendamento(ana, DEZ, ONZE)), List.of(almoco));

        assertFalse(quadro.livre(null, DEZ, ONZE));
        assertTrue(quadro.livre(bia.getId(), ONZE, ONZE.plusHours(1)));
    }

    @Test
    @DisplayName("Agendamento sem funcionária ativa ocupa a vaga de uma funcionária qualquer")
    void semFuncionariaOcupaUmaVaga() {
        Funcionaria desativada = funcionaria("Carla");
        QuadroDeHorarios umaVagaTomada = new QuadroDeHorarios(List.of(ana, bia),
                List.of(agendamento(null, DEZ, ONZE)), List.of());
        QuadroDeHorarios duasVagasTomadas = new QuadroDeHorarios(List.of(ana, bia),
                List.of(agendamento(null, DEZ, ONZE), agendamento(desativada, DEZ, ONZE)), List.of());

        assertTrue(umaVagaTomada.livre(null, DEZ, ONZE));
        assertTrue(umaVagaTomada.livre(bia.getId(), DEZ, ONZE));
        assertFalse(duasVagasTomadas.livre(null, DEZ, ONZE));
    }

    @Test
    @DisplayName("Reconhece só as funcionárias ativas do estúdio")
    void temFuncionaria() {
        QuadroDeHorarios quadro = new QuadroDeHorarios(List.of(ana), List.of(), List.of());

        assertTrue(quadro.temFuncionaria(ana.getId()));
        assertFalse(quadro.temFuncionaria(bia.getId()));
    }
}
