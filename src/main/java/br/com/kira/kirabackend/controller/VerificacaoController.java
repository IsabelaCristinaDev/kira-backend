package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.dto.response.VerificacaoResponse;
import br.com.kira.kirabackend.service.VerificacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Verificação de identidade (Kira Verify). As duas rotas exigem login e só tratam da verificação de quem está logado. */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/verificacao")
public class VerificacaoController {

    private final VerificacaoService verificacaoService;

    // As partes são opcionais aqui para a validação do serviço responder com mensagem clara em vez de erro genérico
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VerificacaoResponse> enviar(
            @AuthenticationPrincipal Usuario usuario,
            @RequestParam(value = "document", required = false) MultipartFile documento,
            @RequestParam(value = "document_back", required = false) MultipartFile versoDocumento,
            @RequestParam(value = "selfie", required = false) MultipartFile selfie,
            @RequestParam(value = "document_type", required = false) String tipoDocumento,
            @RequestParam(value = "consent", required = false) Boolean consentimento) {
        return ResponseEntity.ok(verificacaoService.enviar(usuario, documento, versoDocumento, selfie, tipoDocumento, consentimento));
    }

    @GetMapping("/me")
    public ResponseEntity<VerificacaoResponse> consultar(@AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(verificacaoService.consultar(usuario.getId()));
    }
}
