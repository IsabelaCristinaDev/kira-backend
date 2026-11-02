package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Agendamento;
import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.Mensagem;
import br.com.kira.kirabackend.domain.entity.Servico;
import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.domain.enums.StatusAgendamento;
import br.com.kira.kirabackend.dto.request.MensagemRequest;
import br.com.kira.kirabackend.dto.response.ConversaResponse;
import br.com.kira.kirabackend.dto.response.MensagemResponse;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.observer.ConversaLidaEvent;
import br.com.kira.kirabackend.observer.MensagemEnviadaEvent;
import br.com.kira.kirabackend.repository.AgendamentoRepository;
import br.com.kira.kirabackend.repository.MensagemRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MensagemServiceTest {

    @Mock
    private MensagemRepository mensagemRepository;

    @Mock
    private AgendamentoRepository agendamentoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private MensagemService service;

    private Cliente cliente;
    private Empresa empresa;
    private Agendamento agendamento;

    @BeforeEach
    void setUp() {
        cliente = new Cliente();
        cliente.setId(UUID.randomUUID());
        cliente.setNome("Ana");

        empresa = new Empresa();
        empresa.setId(UUID.randomUUID());
        empresa.setNome("Estúdio Kira");

        agendamento = new Agendamento();
        agendamento.setId(UUID.randomUUID());
        agendamento.setCliente(cliente);
        agendamento.setEmpresa(empresa);
    }

    private MensagemRequest request(String conteudo) {
        return new MensagemRequest(agendamento.getId(), conteudo);
    }

    private Mensagem mensagem(Usuario remetente, String conteudo, LocalDateTime envio) {
        Mensagem mensagem = new Mensagem();
        mensagem.setId(UUID.randomUUID());
        mensagem.setAgendamento(agendamento);
        mensagem.setRemetente(remetente);
        mensagem.setConteudo(conteudo);
        mensagem.setLida(false);
        mensagem.setDataEnvio(envio);
        return mensagem;
    }

    private void mockarEnvio(Usuario remetente) {
        when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));
        when(usuarioRepository.findById(remetente.getId())).thenReturn(Optional.of(remetente));
    }

    // ---------- enviar ----------

    @Nested
    @DisplayName("enviar")
    class Enviar {

        @Test
        @DisplayName("Cliente do agendamento envia mensagem")
        void clienteEnviaMensagem() {
            mockarEnvio(cliente);
            when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> invocation.getArgument(0));

            MensagemResponse response = service.enviar(cliente.getId(), request("Posso chegar 10 min antes?"));

            ArgumentCaptor<Mensagem> captor = ArgumentCaptor.forClass(Mensagem.class);
            verify(mensagemRepository).save(captor.capture());
            assertSame(agendamento, captor.getValue().getAgendamento());
            assertSame(cliente, captor.getValue().getRemetente());
            assertEquals("Posso chegar 10 min antes?", response.conteudo());
            assertEquals("Ana", response.nomeRemetente());
            assertFalse(response.lida());
        }

        @Test
        @DisplayName("Mensagem salva gera o evento para avisar quem está na conversa")
        void mensagemSalvaPublicaEvento() {
            mockarEnvio(cliente);
            when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> invocation.getArgument(0));

            MensagemResponse response = service.enviar(cliente.getId(), request("Oi"));

            ArgumentCaptor<MensagemEnviadaEvent> captor = ArgumentCaptor.forClass(MensagemEnviadaEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertSame(response, captor.getValue().getMensagem());
            assertEquals(List.of(cliente.getId(), empresa.getId()), captor.getValue().getParticipantes());
        }

        @Test
        @DisplayName("Empresa do agendamento envia mensagem")
        void empresaEnviaMensagem() {
            mockarEnvio(empresa);
            when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> invocation.getArgument(0));

            MensagemResponse response = service.enviar(empresa.getId(), request("Pode sim!"));

            assertEquals(empresa.getId(), response.remetenteId());
        }

        @Test
        @DisplayName("Agendamento cancelado não recebe mensagem nova")
        void canceladoNaoRecebeMensagem() {
            agendamento.setStatus(StatusAgendamento.CANCELADO);
            mockarEnvio(cliente);
            UUID remetenteId = cliente.getId();
            MensagemRequest req = request("Oi");

            assertThrows(RegraDeNegocioException.class,
                    () -> service.enviar(remetenteId, req));
            verify(mensagemRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("Agendamento concluído continua recebendo mensagem")
        void concluidoRecebeMensagem() {
            agendamento.setStatus(StatusAgendamento.CONCLUIDO);
            mockarEnvio(cliente);
            when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> invocation.getArgument(0));

            MensagemResponse response = service.enviar(cliente.getId(), request("Esqueci meu guarda-chuva aí"));

            assertEquals("Esqueci meu guarda-chuva aí", response.conteudo());
        }

        @Test
        @DisplayName("Cliente que não participa do agendamento não pode enviar mensagem")
        void clienteDeForaNaoEnvia() {
            Cliente outroCliente = new Cliente();
            outroCliente.setId(UUID.randomUUID());
            mockarEnvio(outroCliente);
            UUID remetenteId = outroCliente.getId();
            MensagemRequest req = request("Oi");

            assertThrows(RegraDeNegocioException.class,
                    () -> service.enviar(remetenteId, req));
            verify(mensagemRepository, never()).save(any());
        }

        @Test
        @DisplayName("Empresa que não participa do agendamento não pode enviar mensagem")
        void empresaDeForaNaoEnvia() {
            Empresa outraEmpresa = new Empresa();
            outraEmpresa.setId(UUID.randomUUID());
            mockarEnvio(outraEmpresa);
            UUID remetenteId = outraEmpresa.getId();
            MensagemRequest req = request("Oi");

            assertThrows(RegraDeNegocioException.class,
                    () -> service.enviar(remetenteId, req));
            verify(mensagemRepository, never()).save(any());
        }

        @Test
        @DisplayName("Enviar para agendamento inexistente dá 404")
        void agendamentoInexistente() {
            when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.empty());
            UUID remetenteId = cliente.getId();
            MensagemRequest req = request("Oi");

            assertThrows(RecursoNaoEncontradoException.class,
                    () -> service.enviar(remetenteId, req));
            verify(mensagemRepository, never()).save(any());
        }

        @Test
        @DisplayName("Remetente inexistente dá 404")
        void remetenteInexistente() {
            when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));
            when(usuarioRepository.findById(cliente.getId())).thenReturn(Optional.empty());
            UUID remetenteId = cliente.getId();
            MensagemRequest req = request("Oi");

            assertThrows(RecursoNaoEncontradoException.class,
                    () -> service.enviar(remetenteId, req));
            verify(mensagemRepository, never()).save(any());
        }
    }

    // ---------- listar conversa ----------

    @Nested
    @DisplayName("listarConversa")
    class ListarConversa {

        @BeforeEach
        void mockarAgendamento() {
            when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));
        }

        @Test
        @DisplayName("Cliente do agendamento vê as mensagens na ordem de envio")
        void listarConversa() {
            LocalDateTime agora = LocalDateTime.of(2026, 10, 15, 14, 0);
            when(mensagemRepository.findByAgendamentoIdOrderByDataEnvioAsc(agendamento.getId()))
                    .thenReturn(List.of(
                            mensagem(cliente, "Oi, tudo bem?", agora),
                            mensagem(empresa, "Tudo! Como posso ajudar?", agora.plusMinutes(2))));

            List<MensagemResponse> conversa = service.listarConversa(agendamento.getId(), cliente.getId());

            assertEquals(List.of("Oi, tudo bem?", "Tudo! Como posso ajudar?"),
                    conversa.stream().map(MensagemResponse::conteudo).toList());
            assertEquals(List.of("Ana", "Estúdio Kira"),
                    conversa.stream().map(MensagemResponse::nomeRemetente).toList());
            assertTrue(conversa.stream().allMatch(m -> m.agendamentoId().equals(agendamento.getId())));
        }

        @Test
        @DisplayName("Empresa do agendamento também vê a conversa")
        void empresaVeConversa() {
            when(mensagemRepository.findByAgendamentoIdOrderByDataEnvioAsc(agendamento.getId())).thenReturn(List.of());

            assertTrue(service.listarConversa(agendamento.getId(), empresa.getId()).isEmpty());
        }

        @Test
        @DisplayName("Quem não participa do agendamento não vê a conversa")
        void usuarioDeForaNaoVe() {
            UUID outroUsuario = UUID.randomUUID();
            UUID agendamentoId = agendamento.getId();

            assertThrows(RegraDeNegocioException.class,
                    () -> service.listarConversa(agendamentoId, outroUsuario));
            verify(mensagemRepository, never()).findByAgendamentoIdOrderByDataEnvioAsc(any());
        }
    }

    @Test
    @DisplayName("Listar conversa de agendamento inexistente dá 404")
    void listarConversaAgendamentoInexistente() {
        when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.empty());
        UUID agendamentoId = agendamento.getId();
        UUID clienteId = cliente.getId();

        assertThrows(RecursoNaoEncontradoException.class,
                () -> service.listarConversa(agendamentoId, clienteId));
    }

    // ---------- marcar como lida ----------

    @Test
    @DisplayName("Destinatária marca a mensagem como lida")
    void marcarComoLida() {
        Mensagem mensagem = mensagem(cliente, "Oi", LocalDateTime.now());
        when(mensagemRepository.findById(mensagem.getId())).thenReturn(Optional.of(mensagem));

        service.marcarComoLida(mensagem.getId(), empresa.getId());

        assertTrue(mensagem.getLida());
        verify(mensagemRepository).save(mensagem);
        verify(eventPublisher).publishEvent(any(ConversaLidaEvent.class));
    }

    @Test
    @DisplayName("Quem enviou não pode marcar a própria mensagem como lida")
    void remetenteNaoMarcaComoLida() {
        Mensagem mensagem = mensagem(cliente, "Oi", LocalDateTime.now());
        when(mensagemRepository.findById(mensagem.getId())).thenReturn(Optional.of(mensagem));
        UUID mensagemId = mensagem.getId();
        UUID clienteId = cliente.getId();

        assertThrows(RegraDeNegocioException.class,
                () -> service.marcarComoLida(mensagemId, clienteId));
        assertFalse(mensagem.getLida());
        verify(mensagemRepository, never()).save(any());
    }

    @Test
    @DisplayName("Quem não participa do agendamento não marca mensagem como lida")
    void usuarioDeForaNaoMarcaComoLida() {
        Mensagem mensagem = mensagem(cliente, "Oi", LocalDateTime.now());
        when(mensagemRepository.findById(mensagem.getId())).thenReturn(Optional.of(mensagem));
        UUID mensagemId = mensagem.getId();
        UUID outroUsuario = UUID.randomUUID();

        assertThrows(RegraDeNegocioException.class,
                () -> service.marcarComoLida(mensagemId, outroUsuario));
        assertFalse(mensagem.getLida());
        verify(mensagemRepository, never()).save(any());
    }

    @Test
    @DisplayName("Marcar como lida uma mensagem inexistente dá 404")
    void marcarComoLidaInexistente() {
        UUID id = UUID.randomUUID();
        when(mensagemRepository.findById(id)).thenReturn(Optional.empty());
        UUID clienteId = cliente.getId();

        assertThrows(RecursoNaoEncontradoException.class, () -> service.marcarComoLida(id, clienteId));
        verify(mensagemRepository, never()).save(any());
    }

    // ---------- lista de conversas ----------

    @Nested
    @DisplayName("listarConversas")
    class ListarConversas {

        private MensagemRepository.ConversaResumo resumo(Mensagem ultima, long naoLidas) {
            return new MensagemRepository.ConversaResumo() {
                @Override
                public Mensagem getUltimaMensagem() {
                    return ultima;
                }

                @Override
                public long getNaoLidas() {
                    return naoLidas;
                }
            };
        }

        @BeforeEach
        void setUpServico() {
            Servico servico = new Servico();
            servico.setNome("Corte");
            agendamento.setServico(servico);
        }

        @Test
        @DisplayName("Para o estúdio, a outra parte é a cliente")
        void estudioVeACliente() {
            Mensagem ultima = mensagem(cliente, "Oi", LocalDateTime.now());
            when(mensagemRepository.listarConversas(empresa.getId())).thenReturn(List.of(resumo(ultima, 2)));

            ConversaResponse conversa = service.listarConversas(empresa.getId()).get(0);

            assertEquals(agendamento.getId(), conversa.agendamentoId());
            assertEquals("Ana", conversa.nomeOutraParte());
            assertEquals("Corte", conversa.nomeServico());
            assertEquals("Oi", conversa.ultimaMensagem());
            assertFalse(conversa.ultimaMensagemMinha());
            assertEquals(2, conversa.naoLidas());
        }

        @Test
        @DisplayName("Para a cliente, a outra parte é o estúdio, e a última mensagem pode ser dela")
        void clienteVeOEstudio() {
            Mensagem ultima = mensagem(cliente, "Oi", LocalDateTime.now());
            when(mensagemRepository.listarConversas(cliente.getId())).thenReturn(List.of(resumo(ultima, 0)));

            ConversaResponse conversa = service.listarConversas(cliente.getId()).get(0);

            assertEquals("Estúdio Kira", conversa.nomeOutraParte());
            assertTrue(conversa.ultimaMensagemMinha());
        }

        @Test
        @DisplayName("Duas mensagens no mesmo instante não repetem a conversa")
        void empateNaoRepete() {
            LocalDateTime agora = LocalDateTime.now();
            when(mensagemRepository.listarConversas(cliente.getId())).thenReturn(List.of(
                    resumo(mensagem(cliente, "A", agora), 0),
                    resumo(mensagem(empresa, "B", agora), 0)));

            assertEquals(1, service.listarConversas(cliente.getId()).size());
        }
    }

    // ---------- marcar a conversa como lida ----------

    @Test
    @DisplayName("Participante marca de uma vez o que recebeu na conversa")
    void marcarConversaComoLida() {
        when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));
        when(mensagemRepository.marcarRecebidasComoLidas(agendamento.getId(), empresa.getId())).thenReturn(2);

        service.marcarConversaComoLida(agendamento.getId(), empresa.getId());

        ArgumentCaptor<ConversaLidaEvent> captor = ArgumentCaptor.forClass(ConversaLidaEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals(agendamento.getId(), captor.getValue().agendamentoId());
        assertEquals(empresa.getId(), captor.getValue().usuarioId());
    }

    @Test
    @DisplayName("Conversa sem nada para ler não gera aviso")
    void conversaSemNaoLidasNaoAvisa() {
        when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));

        service.marcarConversaComoLida(agendamento.getId(), empresa.getId());

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Quem não participa do agendamento não marca a conversa como lida")
    void usuarioDeForaNaoMarcaConversa() {
        UUID outroUsuario = UUID.randomUUID();
        when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));
        UUID agendamentoId = agendamento.getId();

        assertThrows(RegraDeNegocioException.class,
                () -> service.marcarConversaComoLida(agendamentoId, outroUsuario));
        verify(mensagemRepository, never()).marcarRecebidasComoLidas(any(), any());
    }

    @Test
    @DisplayName("Marcar como lida a conversa de um agendamento inexistente dá 404")
    void marcarConversaInexistente() {
        UUID id = UUID.randomUUID();
        when(agendamentoRepository.findById(id)).thenReturn(Optional.empty());
        UUID clienteId = cliente.getId();

        assertThrows(RecursoNaoEncontradoException.class,
                () -> service.marcarConversaComoLida(id, clienteId));
        verify(mensagemRepository, never()).marcarRecebidasComoLidas(any(), any());
    }
}
