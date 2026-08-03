package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.dto.request.RecuperacaoSenhaRequest;
import br.com.kira.kirabackend.dto.request.RedefinicaoSenhaRequest;
import br.com.kira.kirabackend.service.RecuperacaoSenhaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/auth")
public class RecuperacaoSenhaController {


    private final RecuperacaoSenhaService recuperacaoSenhaService;

    @PostMapping("/recuperar-senha")
    public ResponseEntity<Void> solicitarRecuperacao(
            @RequestBody @Valid RecuperacaoSenhaRequest request) {
        recuperacaoSenhaService.solicitarRecuperacao(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/redefinir-senha")
    public ResponseEntity<Void> redefinirSenha(
            @RequestBody @Valid RedefinicaoSenhaRequest request) {
        recuperacaoSenhaService.redefinirSenha(request);
        return ResponseEntity.ok().build();
    }
}