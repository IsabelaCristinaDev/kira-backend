package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.dto.request.DenunciaRequest;
import br.com.kira.kirabackend.dto.response.DenunciaRegistradaResponse;
import br.com.kira.kirabackend.service.DenunciaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/denuncias")
public class DenunciaController {

    private final DenunciaService denunciaService;

    @PostMapping
    public ResponseEntity<DenunciaRegistradaResponse> registrar(
            @AuthenticationPrincipal Usuario usuario,
            @RequestBody @Valid DenunciaRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(denunciaService.registrar(usuario.getId(), request));
    }
}
