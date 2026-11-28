package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.dto.response.VerificacaoEmAnaliseResponse;
import br.com.kira.kirabackend.service.RevisaoVerificacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Revisão manual das verificações de identidade em análise. Em /api/admin, só ADMIN acessa (SecurityConfig). */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/admin/verificacoes")
public class AdminVerificacaoController {

    private final RevisaoVerificacaoService revisaoService;

    @GetMapping("/em-analise")
    public ResponseEntity<List<VerificacaoEmAnaliseResponse>> listarEmAnalise() {
        return ResponseEntity.ok(revisaoService.listarEmAnalise());
    }

    @PostMapping("/{id}/aprovar")
    public ResponseEntity<Void> aprovar(@PathVariable UUID id, @AuthenticationPrincipal Usuario admin) {
        revisaoService.aprovar(id, admin);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reprovar")
    public ResponseEntity<Void> reprovar(@PathVariable UUID id, @AuthenticationPrincipal Usuario admin) {
        revisaoService.reprovar(id, admin);
        return ResponseEntity.noContent().build();
    }
}
