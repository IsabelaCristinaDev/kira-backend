package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.Servico;
import br.com.kira.kirabackend.domain.enums.CategoriaServico;
import br.com.kira.kirabackend.dto.request.ServicoRequest;
import br.com.kira.kirabackend.dto.response.ServicoResponse;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.ServicoRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ServicoServiceTest {

    @Mock
    private ServicoRepository servicoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ImagensEnviadas imagens;

    private ServicoService service;

    private Empresa empresa;

    @BeforeEach
    void setUp() {
        // Pelo construtor, como o Spring faz: garante que as dependências são realmente injetadas.
        service = new ServicoService(servicoRepository, usuarioRepository, imagens);

        empresa = new Empresa();
        empresa.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("Cadastra o serviço ativo e ligado à empresa")
    void deveCadastrarServico() {
        when(usuarioRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));
        when(servicoRepository.save(any(Servico.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ServicoResponse response = service.cadastrar(empresa.getId(),
                new ServicoRequest("Design de sobrancelha", null, 45, new BigDecimal("50.00"), null));

        ArgumentCaptor<Servico> captor = ArgumentCaptor.forClass(Servico.class);
        verify(servicoRepository).save(captor.capture());
        assertSame(empresa, captor.getValue().getEmpresa());
        assertTrue(response.ativo());
        assertEquals(45, response.duracaoMinutos());
        assertEquals(new BigDecimal("50.00"), response.preco());
        assertEquals(CategoriaServico.SOBRANCELHAS, captor.getValue().getCategoria());
        assertEquals(CategoriaServico.SOBRANCELHAS, response.categoria());
    }

    @Test
    @DisplayName("A categoria escolhida é salva mesmo que o nome sugira outra")
    void cadastraComCategoriaEscolhida() {
        when(usuarioRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));
        when(servicoRepository.save(any(Servico.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ServicoResponse response = service.cadastrar(empresa.getId(),
                new ServicoRequest("Pacote noiva", null, 120, new BigDecimal("300"), CategoriaServico.MAQUIAGEM));

        assertEquals(CategoriaServico.MAQUIAGEM, response.categoria());
    }

    @Test
    @DisplayName("Editar sem categoria deduz de novo pelo nome novo")
    void editarDeduzPeloNomeNovo() {
        Servico servico = new Servico();
        servico.setId(UUID.randomUUID());
        servico.setEmpresa(empresa);
        servico.setCategoria(CategoriaServico.CABELO);
        when(servicoRepository.findById(servico.getId())).thenReturn(Optional.of(servico));
        when(servicoRepository.save(any(Servico.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ServicoResponse response = service.atualizar(empresa.getId(), servico.getId(),
                new ServicoRequest("Manicure", null, 40, new BigDecimal("35"), null));

        assertEquals(CategoriaServico.UNHAS, servico.getCategoria());
        assertEquals(CategoriaServico.UNHAS, response.categoria());
    }

    @Test
    @DisplayName("Serviço antigo, sem categoria no banco, sai com a categoria deduzida pelo nome")
    void servicoAntigoSemCategoria() {
        Servico antigo = new Servico();
        antigo.setId(UUID.randomUUID());
        antigo.setNome("Lash lifting");
        when(usuarioRepository.existsEmpresaAtivaById(empresa.getId())).thenReturn(true);
        when(servicoRepository.findByEmpresaIdAndAtivoTrue(empresa.getId())).thenReturn(List.of(antigo));

        assertEquals(CategoriaServico.CILIOS, service.listarPorEmpresa(empresa.getId()).get(0).categoria());
        assertEquals(null, antigo.getCategoria());
    }

    @Test
    @DisplayName("Cadastrar para empresa inexistente dá 404")
    void empresaInexistente() {
        when(usuarioRepository.findById(empresa.getId())).thenReturn(Optional.empty());

        UUID empresaId = empresa.getId();
        ServicoRequest req = new ServicoRequest("Corte", null, 30, new BigDecimal("40"), null);

        assertThrows(RecursoNaoEncontradoException.class,
                () -> service.cadastrar(empresaId, req));
        verify(servicoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Desativar faz exclusão lógica, sem apagar o registro")
    void desativarFazExclusaoLogica() {
        Servico servico = new Servico();
        servico.setId(UUID.randomUUID());
        servico.setAtivo(true);
        servico.setEmpresa(empresa);
        when(servicoRepository.findById(servico.getId())).thenReturn(Optional.of(servico));

        service.desativar(empresa.getId(), servico.getId());

        assertFalse(servico.getAtivo());
        verify(servicoRepository).save(servico);
        verify(servicoRepository, never()).delete(any());
        verify(servicoRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("Desativar um serviço inexistente dá 404")
    void desativarInexistenteDa404() {
        UUID id = UUID.randomUUID();
        when(servicoRepository.findById(id)).thenReturn(Optional.empty());

        UUID empresaId = empresa.getId();
        RecursoNaoEncontradoException ex = assertThrows(RecursoNaoEncontradoException.class,
                () -> service.desativar(empresaId, id));

        assertEquals("Serviço não encontrado", ex.getMessage());
        verify(servicoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Atualizar um serviço inexistente dá 404")
    void atualizarInexistenteDa404() {
        UUID id = UUID.randomUUID();
        when(servicoRepository.findById(id)).thenReturn(Optional.empty());

        UUID empresaId = empresa.getId();
        ServicoRequest request = new ServicoRequest("Corte", null, 30, new BigDecimal("40"), null);
        assertThrows(RecursoNaoEncontradoException.class, () -> service.atualizar(empresaId, id, request));
        verify(servicoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Serviço de outra empresa não é alterado nem desativado pela rota desta")
    void naoMexeEmServicoDeOutraEmpresa() {
        Empresa outra = new Empresa();
        outra.setId(UUID.randomUUID());
        Servico servico = new Servico();
        servico.setId(UUID.randomUUID());
        servico.setAtivo(true);
        servico.setEmpresa(outra);
        UUID servicoId = servico.getId();
        UUID empresaId = empresa.getId();
        ServicoRequest request = new ServicoRequest("Corte", null, 30, new BigDecimal("40"), null);
        when(servicoRepository.findById(servicoId)).thenReturn(Optional.of(servico));

        assertThrows(RecursoNaoEncontradoException.class, () -> service.atualizar(empresaId, servicoId, request));
        assertThrows(RecursoNaoEncontradoException.class, () -> service.desativar(empresaId, servicoId));
        assertTrue(servico.getAtivo());
        verify(servicoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Listar serviços de empresa desativada dá 404")
    void listarDeEmpresaDesativadaDa404() {
        UUID empresaId = empresa.getId();
        when(usuarioRepository.existsEmpresaAtivaById(empresaId)).thenReturn(false);

        RecursoNaoEncontradoException ex = assertThrows(RecursoNaoEncontradoException.class,
                () -> service.listarPorEmpresa(empresaId));
        assertEquals("Empresa não encontrada", ex.getMessage());
        verifyNoInteractions(servicoRepository);
    }

    @Test
    @DisplayName("Listar serviços de empresa ativa consulta só os ativos")
    void listarDeEmpresaAtiva() {
        UUID empresaId = empresa.getId();
        when(usuarioRepository.existsEmpresaAtivaById(empresaId)).thenReturn(true);
        when(servicoRepository.findByEmpresaIdAndAtivoTrue(empresaId)).thenReturn(List.of());

        assertTrue(service.listarPorEmpresa(empresaId).isEmpty());
        verify(servicoRepository).findByEmpresaIdAndAtivoTrue(empresaId);
    }

    // ---------- foto ----------

    private final MockMultipartFile arquivo =
            new MockMultipartFile("arquivo", "foto.png", "image/png", new byte[]{1, 2, 3});

    private Servico servicoDaEmpresa(String fotoUrl) {
        Servico servico = new Servico();
        servico.setId(UUID.randomUUID());
        servico.setNome("Corte");
        servico.setDuracaoMinutos(60);
        servico.setPreco(new BigDecimal("80"));
        servico.setAtivo(true);
        servico.setEmpresa(empresa);
        servico.setFotoUrl(fotoUrl);
        return servico;
    }

    @Test
    @DisplayName("A resposta do serviço traz o endereço da foto")
    void respostaTrazFoto() {
        Servico servico = servicoDaEmpresa("/api/arquivos/corte.png");

        assertEquals("/api/arquivos/corte.png", ServicoResponse.from(servico).fotoUrl());
    }

    @Test
    @DisplayName("Editar o serviço não mexe na foto")
    void atualizarNaoMexeNaFoto() {
        Servico servico = servicoDaEmpresa("/api/arquivos/corte.png");
        when(servicoRepository.findById(servico.getId())).thenReturn(Optional.of(servico));
        when(servicoRepository.save(any(Servico.class))).thenAnswer(inv -> inv.getArgument(0));

        ServicoResponse response = service.atualizar(empresa.getId(), servico.getId(),
                new ServicoRequest("Corte curto", null, 45, new BigDecimal("70"), null));

        assertEquals("/api/arquivos/corte.png", response.fotoUrl());
        verifyNoInteractions(imagens);
    }

    @Test
    @DisplayName("Enviar foto guarda a nova, grava o endereço e apaga a anterior")
    void definirFotoTrocaEApagaAnterior() {
        Servico servico = servicoDaEmpresa("/api/arquivos/antiga.png");
        when(servicoRepository.findById(servico.getId())).thenReturn(Optional.of(servico));
        when(imagens.guardar(arquivo)).thenReturn("/api/arquivos/nova.png");
        when(servicoRepository.save(servico)).thenReturn(servico);

        ServicoResponse response = service.definirFoto(empresa.getId(), servico.getId(), arquivo);

        assertEquals("/api/arquivos/nova.png", response.fotoUrl());
        assertEquals("/api/arquivos/nova.png", servico.getFotoUrl());
        verify(imagens).apagarSemFalhar("/api/arquivos/antiga.png");
    }

    @Test
    @DisplayName("Se gravar o serviço falhar, a foto nova é apagada e a antiga fica")
    void definirFotoFalhaAoSalvarApagaANova() {
        Servico servico = servicoDaEmpresa("/api/arquivos/antiga.png");
        UUID empresaId = empresa.getId();
        UUID servicoId = servico.getId();
        when(servicoRepository.findById(servicoId)).thenReturn(Optional.of(servico));
        when(imagens.guardar(arquivo)).thenReturn("/api/arquivos/nova.png");
        doThrow(new IllegalStateException("banco fora")).when(servicoRepository).save(servico);

        assertThrows(IllegalStateException.class, () -> service.definirFoto(empresaId, servicoId, arquivo));

        verify(imagens).apagarSemFalhar("/api/arquivos/nova.png");
        verify(imagens, never()).apagarSemFalhar("/api/arquivos/antiga.png");
    }

    @Test
    @DisplayName("Imagem recusada não muda a foto do serviço")
    void definirFotoRecusada() {
        Servico servico = servicoDaEmpresa("/api/arquivos/antiga.png");
        UUID empresaId = empresa.getId();
        UUID servicoId = servico.getId();
        when(servicoRepository.findById(servicoId)).thenReturn(Optional.of(servico));
        when(imagens.guardar(arquivo)).thenThrow(new RegraDeNegocioException("A foto precisa ser JPG, PNG ou WEBP"));

        assertThrows(RegraDeNegocioException.class, () -> service.definirFoto(empresaId, servicoId, arquivo));

        assertEquals("/api/arquivos/antiga.png", servico.getFotoUrl());
        verify(servicoRepository, never()).save(any());
        verify(imagens, never()).apagarSemFalhar(any());
    }

    @Test
    @DisplayName("Foto de serviço de outra empresa responde como inexistente e nada é guardado")
    void fotoDeServicoDeOutraEmpresa() {
        Empresa outra = new Empresa();
        outra.setId(UUID.randomUUID());
        Servico servico = servicoDaEmpresa("/api/arquivos/antiga.png");
        servico.setEmpresa(outra);
        UUID empresaId = empresa.getId();
        UUID servicoId = servico.getId();
        when(servicoRepository.findById(servicoId)).thenReturn(Optional.of(servico));

        assertThrows(RecursoNaoEncontradoException.class, () -> service.definirFoto(empresaId, servicoId, arquivo));
        assertThrows(RecursoNaoEncontradoException.class, () -> service.removerFoto(empresaId, servicoId));

        assertEquals("/api/arquivos/antiga.png", servico.getFotoUrl());
        verify(servicoRepository, never()).save(any());
        verifyNoInteractions(imagens);
    }

    @Test
    @DisplayName("Remover a foto limpa o endereço e apaga o arquivo")
    void removerFoto() {
        Servico servico = servicoDaEmpresa("/api/arquivos/antiga.png");
        when(servicoRepository.findById(servico.getId())).thenReturn(Optional.of(servico));

        service.removerFoto(empresa.getId(), servico.getId());

        assertNull(servico.getFotoUrl());
        verify(servicoRepository).save(servico);
        verify(imagens).apagarSemFalhar("/api/arquivos/antiga.png");
    }
}
