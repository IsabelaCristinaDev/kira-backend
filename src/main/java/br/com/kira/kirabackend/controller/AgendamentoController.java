package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.dto.request.AgendamentoRequest;
import br.com.kira.kirabackend.dto.response.AgendamentoResponse;
import br.com.kira.kirabackend.service.AgendamentoService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/agendamentos")
public class AgendamentoController {

    @Autowired
    private AgendamentoService agendamentoService;

    @PostMapping("/cliente/{clienteId}")
    public ResponseEntity<AgendamentoResponse> criar(
            @PathVariable UUID clienteId,
            @RequestBody @Valid AgendamentoRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(agendamentoService.criar(clienteId, request));
    }

    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<AgendamentoResponse>> listarPorCliente(
            @PathVariable UUID clienteId) {
        return ResponseEntity.ok(agendamentoService.listarPorCliente(clienteId));
    }

    @GetMapping("/empresa/{empresaId}")
    public ResponseEntity<List<AgendamentoResponse>> listarPorEmpresa(
            @PathVariable UUID empresaId) {
        return ResponseEntity.ok(agendamentoService.listarPorEmpresa(empresaId));
    }

    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<AgendamentoResponse> cancelar(
            @PathVariable UUID id) {
        return ResponseEntity.ok(agendamentoService.cancelarPeloCliente(id));
    }

    @PatchMapping("/{id}/concluir")
    public ResponseEntity<AgendamentoResponse> concluir(
            @PathVariable UUID id) {
        return ResponseEntity.ok(agendamentoService.concluir(id));
    }
}