package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.dto.request.ContatoEmergenciaRequest;
import br.com.kira.kirabackend.dto.response.ContatoEmergenciaResponse;
import br.com.kira.kirabackend.service.ContatoEmergenciaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/contatos-emergencia")
public class ContatoEmergenciaController {

    private final ContatoEmergenciaService contatoService;

    @GetMapping
    public ResponseEntity<List<ContatoEmergenciaResponse>> listar(@AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(contatoService.listar(usuario));
    }

    @PostMapping
    public ResponseEntity<ContatoEmergenciaResponse> cadastrar(
            @AuthenticationPrincipal Usuario usuario,
            @RequestBody @Valid ContatoEmergenciaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contatoService.cadastrar(usuario, request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ContatoEmergenciaResponse> atualizar(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable UUID id,
            @RequestBody @Valid ContatoEmergenciaRequest request) {
        return ResponseEntity.ok(contatoService.atualizar(usuario, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable UUID id) {
        contatoService.remover(usuario, id);
        return ResponseEntity.noContent().build();
    }
}
