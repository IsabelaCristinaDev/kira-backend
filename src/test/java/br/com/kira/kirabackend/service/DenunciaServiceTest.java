package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Admin;
import br.com.kira.kirabackend.domain.entity.Agendamento;
import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.Denuncia;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.SuspensaoPerfil;
import br.com.kira.kirabackend.domain.enums.MotivoDenuncia;
import br.com.kira.kirabackend.domain.enums.StatusAgendamento;
import br.com.kira.kirabackend.domain.enums.StatusDenuncia;
import br.com.kira.kirabackend.dto.request.AnaliseDenunciaRequest;
import br.com.kira.kirabackend.dto.request.DenunciaRequest;
import br.com.kira.kirabackend.dto.response.DenunciaRegistradaResponse;
import br.com.kira.kirabackend.dto.response.DenunciaResponse;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.observer.DenunciaRiscoImediatoEvent;
import br.com.kira.kirabackend.observer.PerfilSuspensoPreventivamenteEvent;
import br.com.kira.kirabackend.repository.AgendamentoRepository;
import br.com.kira.kirabackend.repository.DenunciaRepository;
import br.com.kira.kirabackend.repository.SuspensaoPerfilRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import br.com.kira.kirabackend.util.KiraTimeZone;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DenunciaServiceTest {

    @Mock
    private DenunciaRepository denunciaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private AgendamentoRepository agendamentoRepository;

    @Mock
    private SuspensaoPerfilRepository suspensaoPerfilRepository;

    @Mock
    private SuspensaoPerfilService suspensaoPerfilService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private DenunciaService service;

    private Cliente cliente;
    private Empresa empresa;
    private Agendamento agendamento;

    @BeforeEach
    void setUp() {
        cliente = new Cliente();
        cliente.setId(UUID.randomUUID());
        cliente.setNome("Ana");
        cliente.setTelefone("11999990000");

        empresa = new Empresa();
        empresa.setId(UUID.randomUUID());
        empresa.setNome("Estúdio Kira");
        empresa.setEmail("estudio@kira.com");

        // Por padrão, um atendimento acontecendo agora.
        LocalDateTime agora = LocalDateTime.now(KiraTimeZone.DEFAULT);
        agendamento = new Agendamento();
        agendamento.setId(UUID.randomUUID());
        agendamento.setCliente(cliente);
        agendamento.setEmpresa(empresa);
        agendamento.setStatus(StatusAgendamento.CONFIRMADO);
        agendamento.setDataHoraInicio(agora.minusMinutes(30));
        agendamento.setDataHoraFim(agora.plusMinutes(30));
    }

    private DenunciaRequest request(UUID agendamentoId, boolean riscoImediato) {
        return new DenunciaRequest(empresa.getId(), agendamentoId,
                MotivoDenuncia.COMPORTAMENTO_SUSPEITO, "Endereço não bate com o anunciado", riscoImediato);
    }

    private void mockarUsuarios() {
        when(usuarioRepository.findById(cliente.getId())).thenReturn(Optional.of(cliente));
        when(usuarioRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));
    }

    private void mockarSalvar() {
        when(denunciaRepository.save(any(Denuncia.class))).thenAnswer(inv -> {
            Denuncia d = inv.getArgument(0);
            d.setId(UUID.randomUUID());
            d.setDataCriacao(LocalDateTime.now());
            return d;
        });
    }

    private Denuncia denunciaPendente(boolean anonima) {
        Denuncia d = new Denuncia();
        d.setId(UUID.randomUUID());
        d.setDenunciante(cliente);
        d.setDenunciado(empresa);
        d.setMotivo(MotivoDenuncia.ENDERECO_FALSO);
        d.setRiscoImediato(!anonima);
        d.setAnonima(anonima);
        d.setStatus(StatusDenuncia.PENDENTE);
        d.setAutoridadeAcionada(false);
        return d;
    }

    @Nested
    @DisplayName("registrar")
    class Registrar {

        @Test
        @DisplayName("Fluxo padrão: registra anônima, na fila, sem suspender nem notificar")
        void fluxoPadrao() {
            mockarUsuarios();
            mockarSalvar();

            DenunciaRegistradaResponse response = service.registrar(cliente.getId(), request(null, false));

            ArgumentCaptor<Denuncia> captor = ArgumentCaptor.forClass(Denuncia.class);
            verify(denunciaRepository).save(captor.capture());
            Denuncia salva = captor.getValue();
            assertTrue(salva.getAnonima());
            assertFalse(salva.getRiscoImediato());
            assertEquals(StatusDenuncia.PENDENTE, salva.getStatus());
            assertNull(response.telefoneEmergencia());
            assertEquals(DenunciaService.ORIENTACAO_PADRAO, response.orientacao());
            verify(suspensaoPerfilService, never()).suspender(any(), any(), anyBoolean());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("Risco imediato em atendimento em andamento: liga 190, suspende, avisa o perfil e a equipe")
        void riscoImediatoDuranteAtendimento() {
            mockarUsuarios();
            mockarSalvar();
            when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));

            DenunciaRegistradaResponse response =
                    service.registrar(cliente.getId(), request(agendamento.getId(), true));

            assertEquals("190", response.telefoneEmergencia());
            assertTrue(response.perfilSuspenso());
            assertEquals(DenunciaService.ORIENTACAO_RISCO_IMEDIATO_COM_SUSPENSAO, response.orientacao());

            ArgumentCaptor<Denuncia> captor = ArgumentCaptor.forClass(Denuncia.class);
            verify(denunciaRepository).save(captor.capture());
            assertFalse(captor.getValue().getAnonima());
            verify(suspensaoPerfilService).suspender(empresa, captor.getValue(), true);

            ArgumentCaptor<Object> eventos = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher, times(2)).publishEvent(eventos.capture());
            PerfilSuspensoPreventivamenteEvent aviso = (PerfilSuspensoPreventivamenteEvent) eventos.getAllValues().get(0);
            assertEquals("estudio@kira.com", aviso.email());
            DenunciaRiscoImediatoEvent alerta = (DenunciaRiscoImediatoEvent) eventos.getAllValues().get(1);
            assertEquals("Ana", alerta.nomeDenunciante());
            assertEquals("Estúdio Kira", alerta.nomeDenunciado());
            assertTrue(alerta.perfilSuspenso());
        }

        @Test
        @DisplayName("Risco imediato dentro da folga de 2h antes do início ainda suspende")
        void riscoImediatoDentroDaFolga() {
            mockarUsuarios();
            mockarSalvar();
            LocalDateTime agora = LocalDateTime.now(KiraTimeZone.DEFAULT);
            agendamento.setDataHoraInicio(agora.plusMinutes(90));
            agendamento.setDataHoraFim(agora.plusMinutes(150));
            when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));

            DenunciaRegistradaResponse response =
                    service.registrar(cliente.getId(), request(agendamento.getId(), true));

            assertTrue(response.perfilSuspenso());
        }

        private void assertRiscoImediatoSemSuspensao(DenunciaRegistradaResponse response) {
            assertEquals("190", response.telefoneEmergencia());
            assertFalse(response.perfilSuspenso());
            assertEquals(DenunciaService.ORIENTACAO_RISCO_IMEDIATO_SEM_SUSPENSAO, response.orientacao());
            verify(denunciaRepository).save(any(Denuncia.class));
            verify(suspensaoPerfilService, never()).suspender(any(), any(), anyBoolean());

            ArgumentCaptor<Object> eventos = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher).publishEvent(eventos.capture());
            DenunciaRiscoImediatoEvent alerta = (DenunciaRiscoImediatoEvent) eventos.getValue();
            assertFalse(alerta.perfilSuspenso());
        }

        @Test
        @DisplayName("Risco imediato sem agendamento: aceita e avisa a equipe, mas não suspende")
        void riscoImediatoSemAgendamento() {
            mockarUsuarios();
            mockarSalvar();

            assertRiscoImediatoSemSuspensao(service.registrar(cliente.getId(), request(null, true)));
        }

        @Test
        @DisplayName("Risco imediato fora da janela do atendimento não suspende")
        void riscoImediatoForaDaJanela() {
            mockarUsuarios();
            mockarSalvar();
            LocalDateTime agora = LocalDateTime.now(KiraTimeZone.DEFAULT);
            agendamento.setDataHoraInicio(agora.minusDays(10));
            agendamento.setDataHoraFim(agora.minusDays(10).plusHours(1));
            when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));

            assertRiscoImediatoSemSuspensao(service.registrar(cliente.getId(), request(agendamento.getId(), true)));
        }

        @Test
        @DisplayName("Risco imediato em agendamento cancelado não suspende")
        void riscoImediatoAgendamentoCancelado() {
            mockarUsuarios();
            mockarSalvar();
            agendamento.setStatus(StatusAgendamento.CANCELADO);
            when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));

            assertRiscoImediatoSemSuspensao(service.registrar(cliente.getId(), request(agendamento.getId(), true)));
        }

        @Test
        @DisplayName("Quem já teve risco imediato julgado infundado não suspende mais na hora")
        void denuncianteComHistoricoInfundado() {
            mockarUsuarios();
            mockarSalvar();
            when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));
            when(denunciaRepository.existsByDenuncianteIdAndRiscoImediatoTrueAndInfundadaTrue(cliente.getId()))
                    .thenReturn(true);

            assertRiscoImediatoSemSuspensao(service.registrar(cliente.getId(), request(agendamento.getId(), true)));
        }

        @Test
        @DisplayName("Não aceita agendamento que não envolve as duas partes")
        void agendamentoDeOutros() {
            mockarUsuarios();
            agendamento.setCliente(new Cliente());
            agendamento.getCliente().setId(UUID.randomUUID());
            when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));

            assertThrows(RegraDeNegocioException.class,
                    () -> service.registrar(cliente.getId(), request(agendamento.getId(), true)));
            verify(denunciaRepository, never()).save(any());
        }

        @Test
        @DisplayName("Não permite denunciar a si mesmo")
        void naoDenunciaASiMesmo() {
            DenunciaRequest req = new DenunciaRequest(cliente.getId(), null,
                    MotivoDenuncia.OUTRO, null, false);

            assertThrows(RegraDeNegocioException.class, () -> service.registrar(cliente.getId(), req));
            verify(denunciaRepository, never()).save(any());
        }

        @Test
        @DisplayName("Não permite denunciar a equipe Kira")
        void naoDenunciaAdmin() {
            Admin admin = new Admin();
            admin.setId(UUID.randomUUID());
            when(usuarioRepository.findById(cliente.getId())).thenReturn(Optional.of(cliente));
            when(usuarioRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
            DenunciaRequest req = new DenunciaRequest(admin.getId(), null, MotivoDenuncia.OUTRO, null, false);

            assertThrows(RegraDeNegocioException.class, () -> service.registrar(cliente.getId(), req));
        }

        @Test
        @DisplayName("Não aceita segunda denúncia pendente contra o mesmo perfil")
        void denunciaDuplicada() {
            mockarUsuarios();
            when(denunciaRepository.existsByDenuncianteIdAndDenunciadoIdAndStatus(
                    cliente.getId(), empresa.getId(), StatusDenuncia.PENDENTE)).thenReturn(true);

            assertThrows(RegraDeNegocioException.class,
                    () -> service.registrar(cliente.getId(), request(null, false)));
            verify(denunciaRepository, never()).save(any());
        }

        @Test
        @DisplayName("Denunciado inexistente gera 404")
        void denunciadoInexistente() {
            when(usuarioRepository.findById(cliente.getId())).thenReturn(Optional.of(cliente));
            when(usuarioRepository.findById(empresa.getId())).thenReturn(Optional.empty());

            assertThrows(RecursoNaoEncontradoException.class,
                    () -> service.registrar(cliente.getId(), request(null, false)));
        }
    }

    @Nested
    @DisplayName("consulta")
    class Consulta {

        @Test
        @DisplayName("Denúncia anônima não expõe o denunciante para a equipe")
        void anonimaOcultaDenunciante() {
            Denuncia d = denunciaPendente(true);
            when(denunciaRepository.findById(d.getId())).thenReturn(Optional.of(d));

            DenunciaResponse response = service.buscar(d.getId());

            assertTrue(response.anonima());
            assertNull(response.denuncianteId());
            assertNull(response.nomeDenunciante());
            assertNull(response.telefoneDenunciante());
        }

        @Test
        @DisplayName("Risco imediato identifica o denunciante e informa a suspensão ativa")
        void riscoImediatoIdentificaDenunciante() {
            Denuncia d = denunciaPendente(false);
            SuspensaoPerfil suspensao = new SuspensaoPerfil();
            suspensao.setId(UUID.randomUUID());
            when(denunciaRepository.findById(d.getId())).thenReturn(Optional.of(d));
            when(suspensaoPerfilRepository.findByDenunciaIdAndAtivaTrue(d.getId())).thenReturn(List.of(suspensao));

            DenunciaResponse response = service.buscar(d.getId());

            assertEquals(cliente.getId(), response.denuncianteId());
            assertEquals("11999990000", response.telefoneDenunciante());
            assertTrue(response.perfilSuspenso());
            assertEquals(suspensao.getId(), response.suspensaoAtivaId());
        }
    }

    @Nested
    @DisplayName("analisar")
    class Analisar {

        private final UUID adminId = UUID.randomUUID();

        @Test
        @DisplayName("Arquivar revoga a suspensão preventiva")
        void arquivarRevogaSuspensao() {
            Denuncia d = denunciaPendente(false);
            when(denunciaRepository.findById(d.getId())).thenReturn(Optional.of(d));
            when(denunciaRepository.save(d)).thenReturn(d);

            DenunciaResponse response = service.analisar(d.getId(), adminId,
                    new AnaliseDenunciaRequest(StatusDenuncia.ARQUIVADA, "Sem indícios", false, false, true));

            assertEquals(StatusDenuncia.ARQUIVADA, response.status());
            assertEquals("Sem indícios", response.parecer());
            assertTrue(response.infundada());
            verify(suspensaoPerfilService).revogarDaDenuncia(d.getId(), adminId);
            verify(suspensaoPerfilService, never()).suspender(any(), any(), anyBoolean());
        }

        @Test
        @DisplayName("Encaminhar com suspensão confirma a preventiva já existente")
        void encaminharConfirmaPreventiva() {
            Denuncia d = denunciaPendente(false);
            when(denunciaRepository.findById(d.getId())).thenReturn(Optional.of(d));
            when(denunciaRepository.save(d)).thenReturn(d);
            when(suspensaoPerfilRepository.existsByDenunciaIdAndAtivaTrue(d.getId())).thenReturn(true);

            DenunciaResponse response = service.analisar(d.getId(), adminId,
                    new AnaliseDenunciaRequest(StatusDenuncia.ENCAMINHADA, "Encaminhado à polícia", true, true, false));

            assertTrue(response.autoridadeAcionada());
            verify(suspensaoPerfilService).confirmarDaDenuncia(d.getId());
            verify(suspensaoPerfilService, never()).suspender(any(), any(), anyBoolean());
        }

        @Test
        @DisplayName("Encaminhar com suspensão suspende quem ainda não estava suspenso")
        void encaminharSuspende() {
            Denuncia d = denunciaPendente(true);
            when(denunciaRepository.findById(d.getId())).thenReturn(Optional.of(d));
            when(denunciaRepository.save(d)).thenReturn(d);

            service.analisar(d.getId(), adminId,
                    new AnaliseDenunciaRequest(StatusDenuncia.ENCAMINHADA, "Procedente", true, false, false));

            verify(suspensaoPerfilService).suspender(empresa, d, false);
        }

        @Test
        @DisplayName("Não permite arquivar mantendo o perfil suspenso")
        void arquivarComSuspensao() {
            Denuncia d = denunciaPendente(true);
            when(denunciaRepository.findById(d.getId())).thenReturn(Optional.of(d));

            assertThrows(RegraDeNegocioException.class, () -> service.analisar(d.getId(), adminId,
                    new AnaliseDenunciaRequest(StatusDenuncia.ARQUIVADA, "x", true, false, false)));
            verify(denunciaRepository, never()).save(any());
        }

        @Test
        @DisplayName("Só denúncia arquivada pode ser marcada como infundada")
        void infundadaSoArquivada() {
            Denuncia d = denunciaPendente(false);
            when(denunciaRepository.findById(d.getId())).thenReturn(Optional.of(d));

            assertThrows(RegraDeNegocioException.class, () -> service.analisar(d.getId(), adminId,
                    new AnaliseDenunciaRequest(StatusDenuncia.ENCAMINHADA, "x", true, false, true)));
            verify(denunciaRepository, never()).save(any());
        }

        @Test
        @DisplayName("Não permite analisar duas vezes")
        void jaAnalisada() {
            Denuncia d = denunciaPendente(true);
            d.setStatus(StatusDenuncia.ENCAMINHADA);
            when(denunciaRepository.findById(d.getId())).thenReturn(Optional.of(d));

            assertThrows(RegraDeNegocioException.class, () -> service.analisar(d.getId(), adminId,
                    new AnaliseDenunciaRequest(StatusDenuncia.ARQUIVADA, "x", false, false, false)));
        }

        @Test
        @DisplayName("Decisão PENDENTE não é aceita")
        void decisaoPendente() {
            Denuncia d = denunciaPendente(true);
            when(denunciaRepository.findById(d.getId())).thenReturn(Optional.of(d));

            assertThrows(RegraDeNegocioException.class, () -> service.analisar(d.getId(), adminId,
                    new AnaliseDenunciaRequest(StatusDenuncia.PENDENTE, "x", false, false, false)));
        }
    }
}
