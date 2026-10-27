package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.dto.request.MensagemRequest;
import br.com.kira.kirabackend.dto.response.MensagemResponse;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.service.MensagemService;
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
@RequestMapping("/api/mensagens")
public class MensagemController {


    private final MensagemService mensagemService;

    @PostMapping("/remetente/{remetenteId}")
    public ResponseEntity<MensagemResponse> enviar(
            @PathVariable UUID remetenteId,
            @AuthenticationPrincipal Usuario usuario,
            @RequestBody @Valid MensagemRequest request) {
        if (!usuario.getId().equals(remetenteId)) {
            throw new RegraDeNegocioException("Você só pode enviar mensagens em seu próprio nome");
        }
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(mensagemService.enviar(remetenteId, request));
    }

    @GetMapping("/agendamento/{agendamentoId}")
    public ResponseEntity<List<MensagemResponse>> listarConversa(
            @PathVariable UUID agendamentoId,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(mensagemService.listarConversa(agendamentoId, usuario.getId()));
    }

    @PatchMapping("/{id}/lida")
    public ResponseEntity<Void> marcarComoLida(
            @PathVariable UUID id,
            @AuthenticationPrincipal Usuario usuario) {
        mensagemService.marcarComoLida(id, usuario.getId());
        return ResponseEntity.noContent().build();
    }
}