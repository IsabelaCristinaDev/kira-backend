package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.dto.response.AgendamentoResponse;
import br.com.kira.kirabackend.service.AgendaFuncionariaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/funcionarias/{funcionariaId}/agenda")
public class AgendaFuncionariaController {

    @Autowired
    private AgendaFuncionariaService agendaFuncionariaService;

    @GetMapping("/dia")
    public ResponseEntity<List<AgendamentoResponse>> agendaDiaria(
            @PathVariable UUID funcionariaId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return ResponseEntity.ok(
                agendaFuncionariaService.listarPorDia(funcionariaId, data));
    }

    @GetMapping("/semana")
    public ResponseEntity<List<AgendamentoResponse>> agendaSemanal(
            @PathVariable UUID funcionariaId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio) {
        return ResponseEntity.ok(
                agendaFuncionariaService.listarPorSemana(funcionariaId, dataInicio));
    }
}