package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImagensEnviadasTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1};

    @Mock
    private ArmazenamentoArquivos armazenamento;

    @InjectMocks
    private ImagensEnviadas imagens;

    @Test
    @DisplayName("lerImagem devolve os bytes de uma imagem válida sem guardar nada")
    void lerImagemValida() {
        byte[] lido = imagens.lerImagem(new MockMultipartFile("f", "x.jpg", "image/jpeg", JPEG));

        assertArrayEquals(JPEG, lido);
        verifyNoInteractions(armazenamento);
    }

    @Test
    @DisplayName("lerImagem recusa arquivo vazio")
    void lerImagemVazia() {
        MockMultipartFile vazio = new MockMultipartFile("f", "x.jpg", "image/jpeg", new byte[0]);

        assertEquals("Envie uma foto",
                assertThrows(RegraDeNegocioException.class, () -> imagens.lerImagem(vazio)).getMessage());
    }

    @Test
    @DisplayName("lerImagem confere o conteúdo, não o tipo informado no envio")
    void lerImagemFormatoInvalido() {
        MockMultipartFile falsa = new MockMultipartFile("f", "x.jpg", "image/jpeg", "texto".getBytes());

        assertEquals("A foto precisa ser JPG, PNG ou WEBP",
                assertThrows(RegraDeNegocioException.class, () -> imagens.lerImagem(falsa)).getMessage());
    }

    @Test
    @DisplayName("lerGuardada abre o arquivo pelo nome que vem depois de /api/arquivos/")
    void lerGuardada() {
        when(armazenamento.abrir("abc.jpg")).thenReturn(Optional.of(new ByteArrayResource(JPEG)));

        Optional<byte[]> lido = imagens.lerGuardada(ImagensEnviadas.PREFIXO_URL + "abc.jpg");

        assertArrayEquals(JPEG, lido.orElseThrow());
    }

    @Test
    @DisplayName("lerGuardada devolve vazio quando o arquivo não está na pasta")
    void lerGuardadaSumida() {
        when(armazenamento.abrir("sumida.jpg")).thenReturn(Optional.empty());

        assertTrue(imagens.lerGuardada(ImagensEnviadas.PREFIXO_URL + "sumida.jpg").isEmpty());
    }

    @Test
    @DisplayName("lerGuardada ignora endereço de fora, sem tocar na pasta")
    void lerGuardadaEnderecoDeFora() {
        assertTrue(imagens.lerGuardada("https://exemplo.com/foto.jpg").isEmpty());
        assertTrue(imagens.lerGuardada(null).isEmpty());
        verifyNoInteractions(armazenamento);
    }

    @Test
    @DisplayName("lerGuardada devolve vazio se a leitura do arquivo falhar")
    void lerGuardadaFalhaDeLeitura() throws IOException {
        Resource quebrado = mock(Resource.class);
        when(quebrado.getInputStream()).thenThrow(new IOException("disco"));
        when(armazenamento.abrir("quebrado.jpg")).thenReturn(Optional.of(quebrado));

        assertTrue(imagens.lerGuardada(ImagensEnviadas.PREFIXO_URL + "quebrado.jpg").isEmpty());
    }

    @Test
    @DisplayName("lerGuardada fecha o arquivo depois de ler")
    void lerGuardadaFechaArquivo() throws IOException {
        InputStream fluxo = mock(InputStream.class);
        when(fluxo.readAllBytes()).thenReturn(JPEG);
        Resource arquivo = mock(Resource.class);
        when(arquivo.getInputStream()).thenReturn(fluxo);
        when(armazenamento.abrir("a.jpg")).thenReturn(Optional.of(arquivo));

        imagens.lerGuardada(ImagensEnviadas.PREFIXO_URL + "a.jpg");

        verify(fluxo).close();
    }
}
