package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.dto.response.EstudioResponse;
import br.com.kira.kirabackend.service.FavoritaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/favoritas")
public class FavoritaController {

    private final FavoritaService favoritaService;

    @GetMapping
    public ResponseEntity<List<EstudioResponse>> listar(@AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(favoritaService.listar(usuario));
    }

    @PutMapping("/{empresaId}")
    public ResponseEntity<Void> salvar(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable UUID empresaId) {
        favoritaService.salvar(usuario, empresaId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{empresaId}")
    public ResponseEntity<Void> remover(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable UUID empresaId) {
        favoritaService.remover(usuario, empresaId);
        return ResponseEntity.noContent().build();
    }
}
