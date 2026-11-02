package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.dto.request.DispositivoWebRequest;
import br.com.kira.kirabackend.dto.request.RemoverDispositivoRequest;
import br.com.kira.kirabackend.dto.response.ChavePublicaResponse;
import br.com.kira.kirabackend.service.NotificacaoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/notificacoes")
public class NotificacaoController {

    private final NotificacaoService notificacaoService;

    @GetMapping("/chave-publica")
    public ResponseEntity<ChavePublicaResponse> chavePublica() {
        return ResponseEntity.ok(new ChavePublicaResponse(notificacaoService.chavePublica()));
    }

    @PostMapping("/dispositivos/web")
    public ResponseEntity<Void> registrar(
            @AuthenticationPrincipal Usuario usuario,
            @RequestBody @Valid DispositivoWebRequest request) {
        notificacaoService.registrar(usuario.getId(), request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/dispositivos/web")
    public ResponseEntity<Void> remover(
            @AuthenticationPrincipal Usuario usuario,
            @RequestBody @Valid RemoverDispositivoRequest request) {
        notificacaoService.remover(usuario.getId(), request.endpoint());
        return ResponseEntity.noContent().build();
    }
}
