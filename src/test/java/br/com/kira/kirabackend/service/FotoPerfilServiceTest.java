package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.dto.response.FotoPerfilResponse;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FotoPerfilServiceTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1};
    private static final byte[] WEBP = "RIFF0000WEBPVP8 ".getBytes(StandardCharsets.US_ASCII);

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private ArmazenamentoArquivos armazenamento;
    private FotoPerfilService service;

    private final UUID usuarioId = UUID.randomUUID();
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        service = new FotoPerfilService(usuarioRepository, new ImagensEnviadas(armazenamento));
        usuario = new Cliente();
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(armazenamento.salvar(any(), any())).thenAnswer(i -> "novo." + i.getArgument(1));
    }

    private static MockMultipartFile arquivo(byte[] conteudo) {
        return new MockMultipartFile("arquivo", "foto.png", "image/png", conteudo);
    }

    @Test
    @DisplayName("JPG, PNG e WEBP são aceitos, com a extensão vinda do conteúdo")
    void aceitaImagens() {
        assertEquals("/api/arquivos/novo.jpg", service.definir(usuarioId, arquivo(JPEG)).fotoUrl());
        assertEquals("/api/arquivos/novo.png", service.definir(usuarioId, arquivo(PNG)).fotoUrl());
        assertEquals("/api/arquivos/novo.webp", service.definir(usuarioId, arquivo(WEBP)).fotoUrl());
    }

    @Test
    @DisplayName("Grava o endereço da foto na conta")
    void gravaNaConta() {
        FotoPerfilResponse response = service.definir(usuarioId, arquivo(PNG));

        assertEquals(response.fotoUrl(), usuario.getFotoUrl());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    @DisplayName("Arquivo que diz ser imagem, mas não é, é recusado e nada é guardado")
    void recusaConteudoFalso() {
        MockMultipartFile falso = arquivo("<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8));

        RegraDeNegocioException ex = assertThrows(RegraDeNegocioException.class,
                () -> service.definir(usuarioId, falso));

        assertEquals("A foto precisa ser JPG, PNG ou WEBP", ex.getMessage());
        verify(armazenamento, never()).salvar(any(), any());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Arquivo vazio é recusado")
    void recusaVazio() {
        MockMultipartFile vazio = arquivo(new byte[0]);

        assertThrows(RegraDeNegocioException.class, () -> service.definir(usuarioId, vazio));
        verify(armazenamento, never()).salvar(any(), any());
    }

    @Test
    @DisplayName("Trocar a foto apaga a anterior")
    void trocaApagaAnterior() {
        usuario.setFotoUrl("/api/arquivos/antiga.png");

        service.definir(usuarioId, arquivo(PNG));

        verify(armazenamento).apagar("antiga.png");
    }

    @Test
    @DisplayName("Foto antiga com endereço de fora não é apagada da pasta")
    void naoApagaEnderecoDeFora() {
        usuario.setFotoUrl("https://exemplo.com/foto.png");

        service.definir(usuarioId, arquivo(PNG));

        verify(armazenamento, never()).apagar(any());
    }

    @Test
    @DisplayName("Se o banco falhar, apaga o arquivo novo e mantém o anterior")
    void falhaNoBancoApagaNovo() {
        usuario.setFotoUrl("/api/arquivos/antiga.png");
        when(usuarioRepository.save(any())).thenThrow(new IllegalStateException("banco fora"));
        MockMultipartFile png = arquivo(PNG);

        assertThrows(IllegalStateException.class, () -> service.definir(usuarioId, png));

        verify(armazenamento).apagar("novo.png");
        verify(armazenamento, never()).apagar("antiga.png");
    }

    @Test
    @DisplayName("Falha ao apagar a foto anterior não desfaz a troca")
    void falhaAoApagarNaoDesfaz() {
        usuario.setFotoUrl("/api/arquivos/antiga.png");
        doThrow(new IllegalStateException("disco")).when(armazenamento).apagar("antiga.png");

        assertEquals("/api/arquivos/novo.png", service.definir(usuarioId, arquivo(PNG)).fotoUrl());
    }

    @Test
    @DisplayName("Remover limpa a conta e apaga o arquivo")
    void remove() {
        usuario.setFotoUrl("/api/arquivos/antiga.png");

        service.remover(usuarioId);

        assertNull(usuario.getFotoUrl());
        verify(usuarioRepository).save(usuario);
        verify(armazenamento).apagar("antiga.png");
    }

    @Test
    @DisplayName("Remover sem foto não apaga nada")
    void removeSemFoto() {
        service.remover(usuarioId);

        verify(armazenamento, never()).apagar(any());
    }

    @Test
    @DisplayName("Conta inexistente dá 404")
    void contaInexistente() {
        UUID outro = UUID.randomUUID();
        when(usuarioRepository.findById(outro)).thenReturn(Optional.empty());
        MockMultipartFile png = arquivo(PNG);

        assertThrows(RecursoNaoEncontradoException.class, () -> service.definir(outro, png));
        assertThrows(RecursoNaoEncontradoException.class, () -> service.remover(outro));
    }
}
