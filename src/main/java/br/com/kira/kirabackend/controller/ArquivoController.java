package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.service.ArmazenamentoArquivos;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/arquivos")
@RequiredArgsConstructor
public class ArquivoController {

    private static final Map<String, MediaType> TIPOS = Map.of(
            "jpg", MediaType.IMAGE_JPEG,
            "png", MediaType.IMAGE_PNG,
            "webp", MediaType.parseMediaType("image/webp"));

    private final ArmazenamentoArquivos armazenamento;

    @GetMapping("/{nome}")
    public ResponseEntity<Resource> abrir(@PathVariable String nome) {
        MediaType tipo = TIPOS.get(nome.substring(nome.lastIndexOf('.') + 1));
        Resource arquivo = tipo == null ? null : armazenamento.abrir(nome).orElse(null);
        if (arquivo == null) {
            throw new RecursoNaoEncontradoException("Arquivo não encontrado");
        }
        // O nome é único e o arquivo nunca muda: o navegador pode guardar a cópia
        return ResponseEntity.ok()
                .contentType(tipo)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(arquivo);
    }
}
