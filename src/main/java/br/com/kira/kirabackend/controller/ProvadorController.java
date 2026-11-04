package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.dto.request.ConsentimentoProvadorRequest;
import br.com.kira.kirabackend.dto.response.ConsentimentoProvadorResponse;
import br.com.kira.kirabackend.dto.response.GeracaoProvadorResponse;
import br.com.kira.kirabackend.service.ProvadorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/provador")
public class ProvadorController {

    private final ProvadorService provadorService;

    @GetMapping("/consentimento")
    public ResponseEntity<ConsentimentoProvadorResponse> consultarConsentimento(
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(provadorService.consultarConsentimento(usuario.getId()));
    }

    @PostMapping("/consentimento")
    public ResponseEntity<ConsentimentoProvadorResponse> aceitarConsentimento(
            @AuthenticationPrincipal Usuario usuario,
            @RequestBody @Valid ConsentimentoProvadorRequest request) {
        return ResponseEntity.ok(provadorService.aceitarConsentimento(usuario.getId(), request.versao()));
    }

    @DeleteMapping("/consentimento")
    public ResponseEntity<ConsentimentoProvadorResponse> revogarConsentimento(
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(provadorService.revogarConsentimento(usuario.getId()));
    }

    // As partes são opcionais aqui para a validação do serviço responder com mensagem clara em vez de erro genérico
    @PostMapping(value = "/geracoes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<GeracaoProvadorResponse> gerar(
            @AuthenticationPrincipal Usuario usuario,
            @RequestParam(value = "cliente", required = false) MultipartFile cliente,
            @RequestParam(value = "referencia", required = false) MultipartFile referencia) {
        return ResponseEntity.ok(provadorService.gerar(usuario.getId(), cliente, referencia));
    }

    // Nenhuma foto fica guardada (veja ProvadorService), então não há o que apagar. Existe para manter o contrato do app.
    @DeleteMapping("/fotos")
    public ResponseEntity<Void> apagarFotos() {
        return ResponseEntity.noContent().build();
    }
}
