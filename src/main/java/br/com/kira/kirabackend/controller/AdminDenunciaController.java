package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.domain.enums.StatusDenuncia;
import br.com.kira.kirabackend.dto.request.AnaliseDenunciaRequest;
import br.com.kira.kirabackend.dto.response.DenunciaResponse;
import br.com.kira.kirabackend.service.DenunciaService;
import br.com.kira.kirabackend.service.SuspensaoPerfilService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/admin")
public class AdminDenunciaController {

    private final DenunciaService denunciaService;
    private final SuspensaoPerfilService suspensaoPerfilService;

    @GetMapping("/denuncias")
    public ResponseEntity<List<DenunciaResponse>> listar(
            @RequestParam(defaultValue = "PENDENTE") StatusDenuncia status) {
        return ResponseEntity.ok(denunciaService.listar(status));
    }

    @GetMapping("/denuncias/{id}")
    public ResponseEntity<DenunciaResponse> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(denunciaService.buscar(id));
    }

    @PatchMapping("/denuncias/{id}/analise")
    public ResponseEntity<DenunciaResponse> analisar(
            @PathVariable UUID id,
            @AuthenticationPrincipal Usuario admin,
            @RequestBody @Valid AnaliseDenunciaRequest request) {
        return ResponseEntity.ok(denunciaService.analisar(id, admin.getId(), request));
    }

    @PostMapping("/suspensoes/{id}/revogar")
    public ResponseEntity<Void> revogarSuspensao(
            @PathVariable UUID id,
            @AuthenticationPrincipal Usuario admin) {
        suspensaoPerfilService.revogar(id, admin.getId());
        return ResponseEntity.noContent().build();
    }
}
