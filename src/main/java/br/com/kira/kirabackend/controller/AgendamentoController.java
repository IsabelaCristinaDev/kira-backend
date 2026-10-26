package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.dto.request.AgendamentoRequest;
import br.com.kira.kirabackend.dto.request.ReagendamentoRequest;
import br.com.kira.kirabackend.dto.response.AgendamentoResponse;
import br.com.kira.kirabackend.service.AgendamentoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/agendamentos")
public class AgendamentoController {

    private final AgendamentoService agendamentoService;

    @PostMapping("/cliente/{clienteId}")
    public ResponseEntity<AgendamentoResponse> criar(
            @PathVariable UUID clienteId,
            @AuthenticationPrincipal Usuario usuario,
            @RequestBody @Valid AgendamentoRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(agendamentoService.criar(clienteId, usuario.getId(), request));
    }

    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<AgendamentoResponse>> listarPorCliente(
            @PathVariable UUID clienteId,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(agendamentoService.listarPorCliente(clienteId, usuario.getId()));
    }

    @GetMapping("/empresa/{empresaId}")
    public ResponseEntity<List<AgendamentoResponse>> listarPorEmpresa(
            @PathVariable UUID empresaId,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(agendamentoService.listarPorEmpresa(empresaId, usuario.getId()));
    }

    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<AgendamentoResponse> cancelar(
            @PathVariable UUID id,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(agendamentoService.cancelarPeloCliente(id, usuario.getId()));
    }

    @PatchMapping("/{id}/concluir")
    public ResponseEntity<AgendamentoResponse> concluir(
            @PathVariable UUID id,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(agendamentoService.concluir(id, usuario.getId()));
    }

    @PatchMapping("/{id}/reagendar/cliente")
    public ResponseEntity<AgendamentoResponse> reagendarPeloCliente(
            @PathVariable UUID id,
            @AuthenticationPrincipal Usuario usuario,
            @RequestBody @Valid ReagendamentoRequest request) {
        return ResponseEntity.ok(agendamentoService.reagendarPeloCliente(id, usuario.getId(), request));
    }

    @PatchMapping("/{id}/reagendar/empresa")
    public ResponseEntity<AgendamentoResponse> reagendarPelaEmpresa(
            @PathVariable UUID id,
            @AuthenticationPrincipal Usuario usuario,
            @RequestBody @Valid ReagendamentoRequest request) {
        return ResponseEntity.ok(agendamentoService.reagendarPelaEmpresa(id, usuario.getId(), request));
    }

    @PatchMapping("/{id}/cancelar/empresa")
    public ResponseEntity<AgendamentoResponse> cancelarPelaEmpresa(
            @PathVariable UUID id,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(agendamentoService.cancelarPelaEmpresa(id, usuario.getId()));
    }
    @GetMapping("/cliente/{clienteId}/historico")
    public ResponseEntity<List<AgendamentoResponse>> historicoCliente(
            @PathVariable UUID clienteId,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(agendamentoService.historicoCliente(clienteId, usuario.getId()));
    }
}