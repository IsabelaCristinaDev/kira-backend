package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Admin;
import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.VerificacaoIdentidade;
import br.com.kira.kirabackend.domain.enums.StatusVerificacao;
import br.com.kira.kirabackend.domain.enums.TipoDocumentoIdentidade;
import br.com.kira.kirabackend.domain.enums.TipoUsuario;
import br.com.kira.kirabackend.dto.response.VerificacaoEmAnaliseResponse;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.VerificacaoIdentidadeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Revisão manual das verificações em análise")
class RevisaoVerificacaoServiceTest {

    @Mock
    private VerificacaoIdentidadeRepository repository;

    @InjectMocks
    private RevisaoVerificacaoService service;

    private Admin admin;
    private Cliente cliente;
    private VerificacaoIdentidade verificacao;

    @BeforeEach
    void setUp() {
        admin = new Admin();
        admin.setId(UUID.randomUUID());

        cliente = new Cliente();
        cliente.setId(UUID.randomUUID());
        cliente.setNome("Ana");

        verificacao = new VerificacaoIdentidade();
        verificacao.setId(UUID.randomUUID());
        verificacao.setUsuario(cliente);
        verificacao.setTipoUsuario(TipoUsuario.CLIENTE);
        verificacao.setTipoDocumento(TipoDocumentoIdentidade.CNH);
        verificacao.setStatus(StatusVerificacao.IN_REVIEW);
        verificacao.setMotivos(List.of("LOW_FACE_SIMILARITY"));
        verificacao.setSimilaridade(new BigDecimal("0.6100"));
        verificacao.setConfiancaOcr(new BigDecimal("0.9000"));
        verificacao.setRequestId("req-1");
        verificacao.setDataCriacao(LocalDateTime.of(2026, 11, 28, 10, 0));
    }

    @Test
    @DisplayName("Lista as verificações em análise com os dados da revisão")
    void listaEmAnalise() {
        when(repository.findByStatusOrderByDataCriacaoAsc(StatusVerificacao.IN_REVIEW))
                .thenReturn(List.of(verificacao));

        List<VerificacaoEmAnaliseResponse> lista = service.listarEmAnalise();

        assertEquals(1, lista.size());
        VerificacaoEmAnaliseResponse item = lista.get(0);
        assertEquals(verificacao.getId(), item.id());
        assertEquals(cliente.getId(), item.usuarioId());
        assertEquals("Ana", item.nome());
        assertEquals(TipoUsuario.CLIENTE, item.tipoUsuario());
        assertEquals(TipoDocumentoIdentidade.CNH, item.tipoDocumento());
        assertEquals(List.of("LOW_FACE_SIMILARITY"), item.motivos());
        assertEquals(new BigDecimal("0.6100"), item.similaridade());
        assertEquals(new BigDecimal("0.9000"), item.confiancaOcr());
        assertEquals("req-1", item.requestId());
        assertEquals(verificacao.getDataCriacao(), item.dataCriacao());
    }

    @Test
    @DisplayName("Aprovar: vira VERIFIED e grava quem revisou e quando")
    void aprova() {
        when(repository.travarParaRevisao(verificacao.getId())).thenReturn(Optional.of(verificacao));
        when(repository.save(verificacao)).thenReturn(verificacao);

        service.aprovar(verificacao.getId(), admin);

        assertEquals(StatusVerificacao.VERIFIED, verificacao.getStatus());
        assertSame(admin, verificacao.getRevisadoPor());
        assertNotNull(verificacao.getRevisadoEm());
        verify(repository).save(verificacao);
    }

    @Test
    @DisplayName("Reprovar: vira REJECTED, mantém os motivos do serviço e grava quem revisou")
    void reprova() {
        when(repository.travarParaRevisao(verificacao.getId())).thenReturn(Optional.of(verificacao));
        when(repository.save(verificacao)).thenReturn(verificacao);

        service.reprovar(verificacao.getId(), admin);

        assertEquals(StatusVerificacao.REJECTED, verificacao.getStatus());
        assertEquals(List.of("LOW_FACE_SIMILARITY"), verificacao.getMotivos());
        assertSame(admin, verificacao.getRevisadoPor());
        assertNotNull(verificacao.getRevisadoEm());
    }

    @ParameterizedTest(name = "{0} não pode ser revisada")
    @EnumSource(value = StatusVerificacao.class, names = "IN_REVIEW", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("Só verificação em análise pode ser aprovada ou reprovada")
    void soEmAnalise(StatusVerificacao status) {
        verificacao.setStatus(status);
        when(repository.travarParaRevisao(verificacao.getId())).thenReturn(Optional.of(verificacao));

        RegraDeNegocioException aprovar = assertThrows(RegraDeNegocioException.class,
                () -> service.aprovar(verificacao.getId(), admin));
        RegraDeNegocioException reprovar = assertThrows(RegraDeNegocioException.class,
                () -> service.reprovar(verificacao.getId(), admin));

        assertEquals("Esta verificação não está em análise", aprovar.getMessage());
        assertEquals("Esta verificação não está em análise", reprovar.getMessage());
        assertEquals(status, verificacao.getStatus());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Verificação inexistente: 404")
    void inexistente() {
        UUID id = UUID.randomUUID();
        when(repository.travarParaRevisao(id)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> service.aprovar(id, admin));
        verify(repository, never()).save(any());
    }
}
