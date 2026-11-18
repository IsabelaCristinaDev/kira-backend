package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.dto.request.RecuperacaoSenhaRequest;
import br.com.kira.kirabackend.dto.request.RedefinicaoSenhaRequest;
import br.com.kira.kirabackend.dto.response.RecuperacaoSenhaResponse;
import br.com.kira.kirabackend.security.IpDoCliente;
import br.com.kira.kirabackend.service.RecuperacaoSenhaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/auth")
public class RecuperacaoSenhaController {

    // Sempre a mesma resposta: não revela se o e-mail tem conta
    private static final RecuperacaoSenhaResponse PEDIDO_RECEBIDO =
            new RecuperacaoSenhaResponse("Se o e-mail estiver cadastrado, enviaremos as instruções.");

    private final RecuperacaoSenhaService recuperacaoSenhaService;
    private final IpDoCliente ipDoCliente;

    @PostMapping("/recuperar-senha")
    public ResponseEntity<RecuperacaoSenhaResponse> solicitarRecuperacao(
            @RequestBody @Valid RecuperacaoSenhaRequest request, HttpServletRequest http) {
        recuperacaoSenhaService.solicitarRecuperacao(request, ipDoCliente.de(http));
        return ResponseEntity.ok(PEDIDO_RECEBIDO);
    }

    @PostMapping("/redefinir-senha")
    public ResponseEntity<Void> redefinirSenha(
            @RequestBody @Valid RedefinicaoSenhaRequest request, HttpServletRequest http) {
        recuperacaoSenhaService.redefinirSenha(request, ipDoCliente.de(http));
        return ResponseEntity.ok().build();
    }
}
