package br.com.kira.kirabackend.observer;

import br.com.kira.kirabackend.domain.enums.TipoAvisoAgendamento;
import br.com.kira.kirabackend.dto.response.AgendamentoResponse;

/** Leva o DTO, e não a entidade: o aviso à cliente é enviado em outra thread, depois do commit. */
public record AvisoAgendamentoEvent(AgendamentoResponse agendamento, TipoAvisoAgendamento tipo) {}
