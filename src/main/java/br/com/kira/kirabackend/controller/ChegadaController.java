package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.dto.request.ValidarChegadaRequest;
import br.com.kira.kirabackend.dto.response.AgendamentoResponse;
import br.com.kira.kirabackend.dto.response.CodigoChegadaResponse;
import br.com.kira.kirabackend.service.ChegadaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Confirmação presencial: a cliente gera e vê o código de chegada, e o estúdio o valida. */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/agendamentos/{id}")
public class ChegadaController {

    private final ChegadaService chegadaService;

    @PostMapping("/token-chegada")
    public ResponseEntity<CodigoChegadaResponse> gerar(
            @PathVariable UUID id,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(chegadaService.gerar(id, usuario.getId()));
    }

    @GetMapping("/token-chegada")
    public ResponseEntity<CodigoChegadaResponse> ativo(
            @PathVariable UUID id,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(chegadaService.ativo(id, usuario.getId()));
    }

    @PostMapping("/chegada/validar")
    public ResponseEntity<AgendamentoResponse> validar(
            @PathVariable UUID id,
            @AuthenticationPrincipal Usuario usuario,
            @RequestBody @Valid ValidarChegadaRequest request) {
        return ResponseEntity.ok(chegadaService.validar(id, usuario.getId(), request.codigo()));
    }
}
