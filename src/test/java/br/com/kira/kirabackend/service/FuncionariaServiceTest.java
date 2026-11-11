package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.Funcionaria;
import br.com.kira.kirabackend.dto.request.FuncionariaRequest;
import br.com.kira.kirabackend.dto.response.FuncionariaResponse;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.FuncionariaRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
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
class FuncionariaServiceTest {

    @Mock
    private FuncionariaRepository funcionariaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ImagensEnviadas imagens;

    @InjectMocks
    private FuncionariaService service;

    private Empresa empresa;

    @BeforeEach
    void setUp() {
        empresa = new Empresa();
        empresa.setId(UUID.randomUUID());
    }

    private FuncionariaRequest request(String nome) {
        return new FuncionariaRequest(nome, "Cílios", new BigDecimal("30"));
    }

    @Test
    @DisplayName("Cadastra a funcionária ativa e ligada à empresa")
    void deveCadastrarFuncionaria() {
        when(usuarioRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));
        when(funcionariaRepository.existsByEmpresaIdAndNomeIgnoreCaseAndAtivoTrue(empresa.getId(), "Bia"))
                .thenReturn(false);

        FuncionariaResponse response = service.cadastrar(empresa.getId(), request("Bia"));

        ArgumentCaptor<Funcionaria> captor = ArgumentCaptor.forClass(Funcionaria.class);
        verify(funcionariaRepository).save(captor.capture());
        assertSame(empresa, captor.getValue().getEmpresa());
        assertEquals("Bia", response.nome());
        assertTrue(response.ativo());
        assertEquals(new BigDecimal("30"), response.comissaoPercentual());
    }

    @Test
    @DisplayName("Não pode haver duas funcionárias ativas com o mesmo nome, sem diferenciar maiúsculas e minúsculas")
    void nomeRepetidoDaErro() {
        when(usuarioRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));
        // Já existe "Bia" ativa. A consulta IgnoreCase faz "BIA" bater com ela.
        when(funcionariaRepository.existsByEmpresaIdAndNomeIgnoreCaseAndAtivoTrue(empresa.getId(), "BIA"))
                .thenReturn(true);

        UUID empresaId = empresa.getId();
        FuncionariaRequest req = request("BIA");

        RegraDeNegocioException ex = assertThrows(RegraDeNegocioException.class,
                () -> service.cadastrar(empresaId, req));

        assertEquals("Já existe uma funcionária ativa com esse nome", ex.getMessage());
        verify(funcionariaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cadastrar para empresa inexistente dá 404")
    void empresaInexistente() {
        when(usuarioRepository.findById(empresa.getId())).thenReturn(Optional.empty());

        UUID empresaId = empresa.getId();
        FuncionariaRequest req = request("Bia");

        assertThrows(RecursoNaoEncontradoException.class,
                () -> service.cadastrar(empresaId, req));
        verify(funcionariaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Desativar faz exclusão lógica, sem apagar o registro")
    void desativarFazExclusaoLogica() {
        Funcionaria funcionaria = new Funcionaria();
        funcionaria.setId(UUID.randomUUID());
        funcionaria.setAtivo(true);
        funcionaria.setEmpresa(empresa);
        when(funcionariaRepository.findById(funcionaria.getId())).thenReturn(Optional.of(funcionaria));

        service.desativar(empresa.getId(), funcionaria.getId());

        assertFalse(funcionaria.getAtivo());
        verify(funcionariaRepository).save(funcionaria);
        verify(funcionariaRepository, never()).delete(any());
        verify(funcionariaRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("Desativar uma funcionária inexistente dá 404")
    void desativarInexistenteDa404() {
        UUID id = UUID.randomUUID();
        when(funcionariaRepository.findById(id)).thenReturn(Optional.empty());

        UUID empresaId = empresa.getId();
        RecursoNaoEncontradoException ex = assertThrows(RecursoNaoEncontradoException.class,
                () -> service.desativar(empresaId, id));

        assertEquals("Funcionária não encontrada", ex.getMessage());
        verify(funcionariaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Funcionária de outra empresa não é alterada nem desativada pela rota desta")
    void naoMexeEmFuncionariaDeOutraEmpresa() {
        Empresa outra = new Empresa();
        outra.setId(UUID.randomUUID());
        Funcionaria funcionaria = new Funcionaria();
        funcionaria.setId(UUID.randomUUID());
        funcionaria.setAtivo(true);
        funcionaria.setEmpresa(outra);
        UUID funcionariaId = funcionaria.getId();
        UUID empresaId = empresa.getId();
        FuncionariaRequest request = request("Bia");
        when(funcionariaRepository.findById(funcionariaId)).thenReturn(Optional.of(funcionaria));

        assertThrows(RecursoNaoEncontradoException.class,
                () -> service.atualizar(empresaId, funcionariaId, request));
        assertThrows(RecursoNaoEncontradoException.class, () -> service.desativar(empresaId, funcionariaId));
        assertTrue(funcionaria.getAtivo());
        verify(funcionariaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Listar funcionárias de empresa desativada dá 404")
    void listarDeEmpresaDesativadaDa404() {
        UUID empresaId = empresa.getId();
        when(usuarioRepository.existsEmpresaAtivaById(empresaId)).thenReturn(false);

        RecursoNaoEncontradoException ex = assertThrows(RecursoNaoEncontradoException.class,
                () -> service.listarPorEmpresa(empresaId));
        assertEquals("Empresa não encontrada", ex.getMessage());
        verifyNoInteractions(funcionariaRepository);
    }

    @Test
    @DisplayName("Listar funcionárias de empresa ativa consulta só os ativos")
    void listarDeEmpresaAtiva() {
        UUID empresaId = empresa.getId();
        when(usuarioRepository.existsEmpresaAtivaById(empresaId)).thenReturn(true);
        when(funcionariaRepository.findByEmpresaIdAndAtivoTrue(empresaId)).thenReturn(List.of());

        assertTrue(service.listarPorEmpresa(empresaId).isEmpty());
        verify(funcionariaRepository).findByEmpresaIdAndAtivoTrue(empresaId);
    }

    @Test
    @DisplayName("Atualizar troca nome, especialidades e comissão, sem mexer na empresa nem no status")
    void deveAtualizarFuncionaria() {
        Funcionaria funcionaria = new Funcionaria();
        funcionaria.setId(UUID.randomUUID());
        funcionaria.setNome("Bia");
        funcionaria.setEspecialidades("Cílios");
        funcionaria.setComissaoPercentual(new BigDecimal("30"));
        funcionaria.setAtivo(true);
        funcionaria.setEmpresa(empresa);
        when(funcionariaRepository.findById(funcionaria.getId())).thenReturn(Optional.of(funcionaria));
        FuncionariaRequest novosDados = new FuncionariaRequest(
                "Beatriz", "Cílios, Sobrancelha", new BigDecimal("40"));

        FuncionariaResponse response = service.atualizar(empresa.getId(), funcionaria.getId(), novosDados);

        verify(funcionariaRepository).save(funcionaria);
        assertEquals("Beatriz", funcionaria.getNome());
        assertEquals("Cílios, Sobrancelha", funcionaria.getEspecialidades());
        assertEquals(new BigDecimal("40"), funcionaria.getComissaoPercentual());
        assertSame(empresa, funcionaria.getEmpresa());
        assertEquals(funcionaria.getId(), response.id());
        assertEquals("Beatriz", response.nome());
        assertEquals(new BigDecimal("40"), response.comissaoPercentual());
        assertTrue(response.ativo());
    }

    @Test
    @DisplayName("Atualizar uma funcionária inexistente dá 404 e nada é salvo")
    void atualizarInexistenteDa404() {
        UUID funcionariaId = UUID.randomUUID();
        UUID empresaId = empresa.getId();
        FuncionariaRequest req = request("Bia");
        when(funcionariaRepository.findById(funcionariaId)).thenReturn(Optional.empty());

        RecursoNaoEncontradoException ex = assertThrows(RecursoNaoEncontradoException.class,
                () -> service.atualizar(empresaId, funcionariaId, req));
        assertEquals("Funcionária não encontrada", ex.getMessage());
        verify(funcionariaRepository, never()).save(any());
    }

    // ---------- foto ----------

    private Funcionaria funcionariaDaEmpresa(String fotoUrl) {
        Funcionaria funcionaria = new Funcionaria();
        funcionaria.setId(UUID.randomUUID());
        funcionaria.setNome("Bia");
        funcionaria.setAtivo(true);
        funcionaria.setEmpresa(empresa);
        funcionaria.setFotoUrl(fotoUrl);
        return funcionaria;
    }

    private final MockMultipartFile arquivo =
            new MockMultipartFile("arquivo", "foto.png", "image/png", new byte[]{1, 2, 3});

    @Test
    @DisplayName("Editar a funcionária não mexe na foto, que tem rota própria")
    void atualizarNaoMexeNaFoto() {
        Funcionaria funcionaria = funcionariaDaEmpresa("/api/arquivos/atual.png");
        when(funcionariaRepository.findById(funcionaria.getId())).thenReturn(Optional.of(funcionaria));

        FuncionariaResponse response = service.atualizar(empresa.getId(), funcionaria.getId(), request("Bia Souza"));

        assertEquals("Bia Souza", response.nome());
        assertEquals("/api/arquivos/atual.png", response.fotoUrl());
        assertEquals("/api/arquivos/atual.png", funcionaria.getFotoUrl());
        verifyNoInteractions(imagens);
    }

    @Test
    @DisplayName("Cadastrar a funcionária começa sem foto")
    void cadastrarComecaSemFoto() {
        when(usuarioRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));

        FuncionariaResponse response = service.cadastrar(empresa.getId(), request("Bia"));

        assertNull(response.fotoUrl());
        verifyNoInteractions(imagens);
    }

    @Test
    @DisplayName("Enviar foto guarda a nova, grava o endereço e apaga a anterior")
    void definirFotoTrocaEApagaAnterior() {
        Funcionaria funcionaria = funcionariaDaEmpresa("/api/arquivos/antiga.png");
        when(funcionariaRepository.findById(funcionaria.getId())).thenReturn(Optional.of(funcionaria));
        when(imagens.guardar(arquivo)).thenReturn("/api/arquivos/nova.png");

        FuncionariaResponse response = service.definirFoto(empresa.getId(), funcionaria.getId(), arquivo);

        assertEquals("/api/arquivos/nova.png", response.fotoUrl());
        assertEquals("/api/arquivos/nova.png", funcionaria.getFotoUrl());
        verify(funcionariaRepository).save(funcionaria);
        verify(imagens).apagarSemFalhar("/api/arquivos/antiga.png");
    }

    @Test
    @DisplayName("Se gravar a funcionária falhar, a foto nova é apagada e a antiga fica")
    void definirFotoFalhaAoSalvarApagaANova() {
        Funcionaria funcionaria = funcionariaDaEmpresa("/api/arquivos/antiga.png");
        UUID empresaId = empresa.getId();
        UUID funcionariaId = funcionaria.getId();
        when(funcionariaRepository.findById(funcionariaId)).thenReturn(Optional.of(funcionaria));
        when(imagens.guardar(arquivo)).thenReturn("/api/arquivos/nova.png");
        doThrow(new IllegalStateException("banco fora")).when(funcionariaRepository).save(funcionaria);

        assertThrows(IllegalStateException.class, () -> service.definirFoto(empresaId, funcionariaId, arquivo));

        verify(imagens).apagarSemFalhar("/api/arquivos/nova.png");
        verify(imagens, never()).apagarSemFalhar("/api/arquivos/antiga.png");
    }

    @Test
    @DisplayName("Imagem recusada não muda a foto da funcionária")
    void definirFotoRecusada() {
        Funcionaria funcionaria = funcionariaDaEmpresa("/api/arquivos/antiga.png");
        UUID empresaId = empresa.getId();
        UUID funcionariaId = funcionaria.getId();
        when(funcionariaRepository.findById(funcionariaId)).thenReturn(Optional.of(funcionaria));
        when(imagens.guardar(arquivo)).thenThrow(new RegraDeNegocioException("A foto precisa ser JPG, PNG ou WEBP"));

        assertThrows(RegraDeNegocioException.class, () -> service.definirFoto(empresaId, funcionariaId, arquivo));

        assertEquals("/api/arquivos/antiga.png", funcionaria.getFotoUrl());
        verify(funcionariaRepository, never()).save(any());
        verify(imagens, never()).apagarSemFalhar(any());
    }

    @Test
    @DisplayName("Foto de funcionária de outra empresa responde como inexistente e nada é guardado")
    void fotoDeFuncionariaDeOutraEmpresa() {
        Empresa outra = new Empresa();
        outra.setId(UUID.randomUUID());
        Funcionaria funcionaria = funcionariaDaEmpresa("/api/arquivos/antiga.png");
        funcionaria.setEmpresa(outra);
        UUID empresaId = empresa.getId();
        UUID funcionariaId = funcionaria.getId();
        when(funcionariaRepository.findById(funcionariaId)).thenReturn(Optional.of(funcionaria));

        assertThrows(RecursoNaoEncontradoException.class,
                () -> service.definirFoto(empresaId, funcionariaId, arquivo));
        assertThrows(RecursoNaoEncontradoException.class, () -> service.removerFoto(empresaId, funcionariaId));

        assertEquals("/api/arquivos/antiga.png", funcionaria.getFotoUrl());
        verify(funcionariaRepository, never()).save(any());
        verifyNoInteractions(imagens);
    }

    @Test
    @DisplayName("Remover a foto limpa o endereço e apaga o arquivo")
    void removerFoto() {
        Funcionaria funcionaria = funcionariaDaEmpresa("/api/arquivos/antiga.png");
        when(funcionariaRepository.findById(funcionaria.getId())).thenReturn(Optional.of(funcionaria));

        service.removerFoto(empresa.getId(), funcionaria.getId());

        assertNull(funcionaria.getFotoUrl());
        verify(funcionariaRepository).save(funcionaria);
        verify(imagens).apagarSemFalhar("/api/arquivos/antiga.png");
    }
}
