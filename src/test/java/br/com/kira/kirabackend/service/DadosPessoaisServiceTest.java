package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Admin;
import br.com.kira.kirabackend.domain.entity.Agendamento;
import br.com.kira.kirabackend.domain.entity.Avaliacao;
import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.ConsentimentoProvador;
import br.com.kira.kirabackend.domain.entity.ContatoEmergencia;
import br.com.kira.kirabackend.domain.entity.DisponibilidadeEstudio;
import br.com.kira.kirabackend.domain.entity.DispositivoNotificacao;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.Endereco;
import br.com.kira.kirabackend.domain.entity.Favorita;
import br.com.kira.kirabackend.domain.entity.FotoPortfolio;
import br.com.kira.kirabackend.domain.entity.Funcionaria;
import br.com.kira.kirabackend.domain.entity.Mensagem;
import br.com.kira.kirabackend.domain.entity.Servico;
import br.com.kira.kirabackend.domain.entity.VerificacaoIdentidade;
import br.com.kira.kirabackend.domain.enums.CategoriaServico;
import br.com.kira.kirabackend.domain.enums.DiaSemana;
import br.com.kira.kirabackend.domain.enums.StatusAgendamento;
import br.com.kira.kirabackend.domain.enums.StatusVerificacao;
import br.com.kira.kirabackend.domain.enums.TipoAvaliador;
import br.com.kira.kirabackend.domain.enums.TipoDocumentoIdentidade;
import br.com.kira.kirabackend.domain.enums.TipoEstabelecimento;
import br.com.kira.kirabackend.domain.enums.TipoUsuario;
import br.com.kira.kirabackend.dto.response.DadosPessoaisResponse;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.AgendamentoRepository;
import br.com.kira.kirabackend.repository.AvaliacaoRepository;
import br.com.kira.kirabackend.repository.ConsentimentoProvadorRepository;
import br.com.kira.kirabackend.repository.ContatoEmergenciaRepository;
import br.com.kira.kirabackend.repository.DisponibilidadeEstudioRepository;
import br.com.kira.kirabackend.repository.DispositivoNotificacaoRepository;
import br.com.kira.kirabackend.repository.FavoritaRepository;
import br.com.kira.kirabackend.repository.FotoPortfolioRepository;
import br.com.kira.kirabackend.repository.FuncionariaRepository;
import br.com.kira.kirabackend.repository.MensagemRepository;
import br.com.kira.kirabackend.repository.ServicoRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import br.com.kira.kirabackend.repository.VerificacaoIdentidadeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DadosPessoaisServiceTest {

    private static final String HASH = "$2a$10$hashquenaopodesair";
    private static final String CHAVE_P256DH = "chave-p256dh-que-nao-pode-sair";
    private static final String CHAVE_AUTH = "chave-auth-que-nao-pode-sair";
    private static final String ENDERECO_PUSH = "https://fcm.googleapis.com/fcm/send/identificador-secreto";

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private AgendamentoRepository agendamentoRepository;
    @Mock private AvaliacaoRepository avaliacaoRepository;
    @Mock private MensagemRepository mensagemRepository;
    @Mock private FavoritaRepository favoritaRepository;
    @Mock private ContatoEmergenciaRepository contatoEmergenciaRepository;
    @Mock private ConsentimentoProvadorRepository consentimentoProvadorRepository;
    @Mock private VerificacaoIdentidadeRepository verificacaoRepository;
    @Mock private DispositivoNotificacaoRepository dispositivoRepository;
    @Mock private FotoPortfolioRepository fotoPortfolioRepository;
    @Mock private ServicoRepository servicoRepository;
    @Mock private FuncionariaRepository funcionariaRepository;
    @Mock private DisponibilidadeEstudioRepository disponibilidadeRepository;

    @InjectMocks
    private DadosPessoaisService service;

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    private static Cliente cliente() {
        Cliente cliente = new Cliente();
        cliente.setId(UUID.randomUUID());
        cliente.setNome("Ana");
        cliente.setEmail("ana@kira.com");
        cliente.setSenhaHash(HASH);
        cliente.setTelefone("62999990000");
        cliente.setCpf("52998224725");
        cliente.setEndereco(new Endereco("74000000", "Rua A", "1", null, "Centro", "Goiania", "GO"));
        cliente.setLatitude(-16.6);
        cliente.setLongitude(-49.2);
        cliente.setFotoUrl("/api/arquivos/perfil.jpg");
        cliente.getPreferencias().addAll(Set.of(CategoriaServico.UNHAS, CategoriaServico.CILIOS));
        return cliente;
    }

    private static Empresa empresa() {
        Empresa empresa = new Empresa();
        empresa.setId(UUID.randomUUID());
        empresa.setNome("Estúdio Bela");
        empresa.setEmail("bela@kira.com");
        empresa.setSenhaHash(HASH);
        empresa.setCnpj("11222333000181");
        empresa.setEspecialidades("Unha, Cílios");
        empresa.setTipoEstabelecimento(TipoEstabelecimento.ESTABELECIMENTO);
        empresa.setFotoUrl("/api/arquivos/logo.png");
        return empresa;
    }

    private static Agendamento agendamento(Cliente cliente, Empresa empresa) {
        Servico servico = new Servico();
        servico.setId(UUID.randomUUID());
        servico.setNome("Manicure");
        servico.setDuracaoMinutos(60);
        Agendamento agendamento = new Agendamento();
        agendamento.setId(UUID.randomUUID());
        agendamento.setCliente(cliente);
        agendamento.setEmpresa(empresa);
        agendamento.setServico(servico);
        agendamento.setDataHoraInicio(LocalDateTime.of(2026, 1, 10, 10, 0));
        agendamento.setDataHoraFim(LocalDateTime.of(2026, 1, 10, 11, 0));
        agendamento.setStatus(StatusAgendamento.CONCLUIDO);
        return agendamento;
    }

    private static Avaliacao avaliacao(Agendamento agendamento, TipoAvaliador autor, int nota) {
        Avaliacao avaliacao = new Avaliacao();
        avaliacao.setId(UUID.randomUUID());
        avaliacao.setAgendamento(agendamento);
        avaliacao.setCliente(agendamento.getCliente());
        avaliacao.setEmpresa(agendamento.getEmpresa());
        avaliacao.setTipoAvaliador(autor);
        avaliacao.setNota(nota);
        return avaliacao;
    }

    @Test
    @DisplayName("Cliente: junta perfil, agendamentos, avaliações, mensagens, favoritas, contatos, consentimentos, dispositivos e fotos")
    void cliente_exportaTudo() throws Exception {
        Cliente cliente = cliente();
        UUID id = cliente.getId();
        Empresa empresa = empresa();
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(cliente));

        Agendamento agendamento = agendamento(cliente, empresa);
        when(agendamentoRepository.findByClienteId(id)).thenReturn(List.of(agendamento));
        when(avaliacaoRepository.findByClienteIdAndTipoAvaliador(id, TipoAvaliador.CLIENTE))
                .thenReturn(List.of(avaliacao(agendamento, TipoAvaliador.CLIENTE, 5)));
        when(avaliacaoRepository.findByClienteIdAndTipoAvaliador(id, TipoAvaliador.EMPRESA))
                .thenReturn(List.of(avaliacao(agendamento, TipoAvaliador.EMPRESA, 4)));

        Mensagem mensagem = new Mensagem();
        mensagem.setId(UUID.randomUUID());
        mensagem.setAgendamento(agendamento);
        mensagem.setRemetente(cliente);
        mensagem.setConteudo("Chego às 10h");
        when(mensagemRepository.findByRemetenteIdOrderByDataEnvioAsc(id)).thenReturn(List.of(mensagem));

        Favorita favorita = new Favorita();
        favorita.setCliente(cliente);
        favorita.setEmpresa(empresa);
        when(favoritaRepository.findByClienteIdOrderByDataCriacaoDesc(id)).thenReturn(List.of(favorita));

        ContatoEmergencia contato = new ContatoEmergencia();
        contato.setNome("Rosa");
        contato.setTelefone("62988887777");
        when(contatoEmergenciaRepository.findByClienteIdOrderByDataCriacaoAsc(id)).thenReturn(List.of(contato));

        ConsentimentoProvador consentimento = new ConsentimentoProvador();
        consentimento.setVersao("2025-01");
        consentimento.setAceitoEm(LocalDateTime.of(2026, 1, 1, 9, 0));
        when(consentimentoProvadorRepository.findByUsuarioId(id)).thenReturn(Optional.of(consentimento));

        VerificacaoIdentidade verificacao = new VerificacaoIdentidade();
        verificacao.setTipoDocumento(TipoDocumentoIdentidade.CNH);
        verificacao.setStatus(StatusVerificacao.VERIFIED);
        verificacao.setSimilaridade(new BigDecimal("0.9876"));
        verificacao.setConfiancaOcr(new BigDecimal("0.5432"));
        verificacao.setRequestId("req-interno-123");
        when(verificacaoRepository.findByUsuarioIdOrderByDataCriacaoDesc(id)).thenReturn(List.of(verificacao));

        DispositivoNotificacao dispositivo = new DispositivoNotificacao();
        dispositivo.setId(UUID.randomUUID());
        dispositivo.setEndpoint(ENDERECO_PUSH);
        dispositivo.setP256dh(CHAVE_P256DH);
        dispositivo.setAuth(CHAVE_AUTH);
        when(dispositivoRepository.findByUsuarioIdOrderByDataCriacaoAsc(id)).thenReturn(List.of(dispositivo));

        DadosPessoaisResponse dados = service.exportar(id);

        assertEquals(TipoUsuario.CLIENTE, dados.tipoConta());
        assertEquals("ana@kira.com", dados.conta().email());
        assertEquals("52998224725", dados.conta().cpf());
        assertEquals("Goiania", dados.conta().endereco().cidade());
        assertEquals(-16.6, dados.conta().latitude());
        assertEquals(List.of(CategoriaServico.CILIOS, CategoriaServico.UNHAS), dados.conta().preferencias());
        assertEquals(1, dados.agendamentos().size());
        assertEquals("Estúdio Bela", dados.agendamentos().get(0).nomeEmpresa());
        assertEquals(5, dados.avaliacoesFeitas().get(0).nota());
        assertEquals(4, dados.avaliacoesRecebidas().get(0).nota());
        assertEquals("Chego às 10h", dados.mensagensEnviadas().get(0).conteudo());
        assertEquals("Estúdio Bela", dados.favoritas().get(0).nome());
        assertEquals("Rosa", dados.contatosEmergencia().get(0).nome());
        assertEquals("2025-01", dados.consentimentos().provador().versao());
        assertEquals(StatusVerificacao.VERIFIED, dados.consentimentos().verificacoes().get(0).status());
        assertEquals("fcm.googleapis.com", dados.dispositivosNotificacao().get(0).servico());
        assertEquals(List.of("/api/arquivos/perfil.jpg"), dados.fotos());
        assertNull(dados.estudio());

        String texto = json.writeValueAsString(dados);
        assertFalse(texto.contains(HASH));
        assertFalse(texto.toLowerCase().contains("senha"));
        assertFalse(texto.contains(CHAVE_P256DH));
        assertFalse(texto.contains(CHAVE_AUTH));
        assertFalse(texto.contains("identificador-secreto"));
        assertFalse(texto.contains("0.9876"));
        assertFalse(texto.contains("req-interno-123"));
    }

    @Test
    @DisplayName("Cliente sem nada além do cadastro: listas vazias e consentimento do Provador nulo")
    void cliente_semDados() {
        Cliente cliente = cliente();
        cliente.setFotoUrl(null);
        cliente.getPreferencias().clear();
        when(usuarioRepository.findById(cliente.getId())).thenReturn(Optional.of(cliente));

        DadosPessoaisResponse dados = service.exportar(cliente.getId());

        assertTrue(dados.conta().preferencias().isEmpty());
        assertTrue(dados.agendamentos().isEmpty());
        assertTrue(dados.fotos().isEmpty());
        assertNull(dados.consentimentos().provador());
        assertTrue(dados.consentimentos().verificacoes().isEmpty());
    }

    @Test
    @DisplayName("Estúdio: inclui serviços, funcionárias, portfólio e horário; avaliações feitas são as do estúdio")
    void estudio_exportaTudo() {
        Empresa empresa = empresa();
        UUID id = empresa.getId();
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(empresa));
        Agendamento agendamento = agendamento(cliente(), empresa);
        when(agendamentoRepository.findByEmpresaId(id)).thenReturn(List.of(agendamento));
        when(avaliacaoRepository.findByEmpresaIdAndTipoAvaliador(id, TipoAvaliador.EMPRESA))
                .thenReturn(List.of(avaliacao(agendamento, TipoAvaliador.EMPRESA, 3)));
        when(avaliacaoRepository.findByEmpresaIdAndTipoAvaliador(id, TipoAvaliador.CLIENTE))
                .thenReturn(List.of(avaliacao(agendamento, TipoAvaliador.CLIENTE, 5)));

        Servico servico = new Servico();
        servico.setNome("Manicure");
        servico.setPreco(new BigDecimal("50.00"));
        when(servicoRepository.findByEmpresaId(id)).thenReturn(List.of(servico));
        Funcionaria funcionaria = new Funcionaria();
        funcionaria.setNome("Carla");
        funcionaria.setFotoUrl("/api/arquivos/carla.jpg");
        when(funcionariaRepository.findByEmpresaId(id)).thenReturn(List.of(funcionaria));
        FotoPortfolio foto = new FotoPortfolio();
        foto.setUrl("/api/arquivos/trabalho.jpg");
        when(fotoPortfolioRepository.findByEmpresaIdOrderByDataCriacaoDesc(id)).thenReturn(List.of(foto));
        DisponibilidadeEstudio horario = new DisponibilidadeEstudio();
        horario.setDiaSemana(DiaSemana.SEGUNDA);
        horario.setHoraInicio(LocalTime.of(9, 0));
        horario.setHoraFim(LocalTime.of(18, 0));
        when(disponibilidadeRepository.findByEmpresaId(id)).thenReturn(List.of(horario));

        DadosPessoaisResponse dados = service.exportar(id);

        assertEquals(TipoUsuario.EMPRESA, dados.tipoConta());
        assertEquals("11222333000181", dados.conta().cnpj());
        assertEquals(List.of("Unha", "Cílios"), dados.conta().especialidades());
        assertNull(dados.conta().preferencias());
        assertEquals(3, dados.avaliacoesFeitas().get(0).nota());
        assertEquals(5, dados.avaliacoesRecebidas().get(0).nota());
        assertTrue(dados.favoritas().isEmpty());
        assertTrue(dados.contatosEmergencia().isEmpty());
        assertEquals("Manicure", dados.estudio().servicos().get(0).nome());
        assertEquals("Carla", dados.estudio().funcionarias().get(0).nome());
        assertEquals("/api/arquivos/trabalho.jpg", dados.estudio().portfolio().get(0).url());
        assertEquals(DiaSemana.SEGUNDA, dados.estudio().horario().get(0).diaSemana());
        assertEquals(List.of("/api/arquivos/logo.png", "/api/arquivos/trabalho.jpg", "/api/arquivos/carla.jpg"),
                dados.fotos());
    }

    @Test
    @DisplayName("Conta inexistente responde 404 e administrador não usa esta rota")
    void contaInvalida() {
        UUID inexistente = UUID.randomUUID();
        when(usuarioRepository.findById(inexistente)).thenReturn(Optional.empty());
        assertThrows(RecursoNaoEncontradoException.class, () -> service.exportar(inexistente));

        Admin admin = new Admin();
        admin.setId(UUID.randomUUID());
        when(usuarioRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        assertThrows(RegraDeNegocioException.class, () -> service.exportar(admin.getId()));
    }
}
