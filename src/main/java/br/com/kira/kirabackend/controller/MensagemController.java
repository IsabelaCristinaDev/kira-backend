package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.dto.request.MensagemRequest;
import br.com.kira.kirabackend.dto.response.MensagemResponse;
import br.com.kira.kirabackend.service.MensagemService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/mensagens")
public class MensagemController {

    @Autowired
    private MensagemService mensagemService;

    @PostMapping("/remetente/{remetenteId}")
    public ResponseEntity<MensagemResponse> enviar(
            @PathVariable UUID remetenteId,
            @RequestBody @Valid MensagemRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(mensagemService.enviar(remetenteId, request));
    }

    @GetMapping("/agendamento/{agendamentoId}")
    public ResponseEntity<List<MensagemResponse>> listarConversa(
            @PathVariable UUID agendamentoId) {
        return ResponseEntity.ok(mensagemService.listarConversa(agendamentoId));
    }

    @PatchMapping("/{id}/lida")
    public ResponseEntity<Void> marcarComoLida(
            @PathVariable UUID id) {
        mensagemService.marcarComoLida(id);
        return ResponseEntity.noContent().build();
    }
}