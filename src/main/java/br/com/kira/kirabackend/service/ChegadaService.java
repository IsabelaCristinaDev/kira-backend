package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Agendamento;
import br.com.kira.kirabackend.domain.entity.CodigoChegada;
import br.com.kira.kirabackend.domain.enums.StatusAgendamento;
import br.com.kira.kirabackend.dto.response.AgendamentoResponse;
import br.com.kira.kirabackend.dto.response.CodigoChegadaResponse;
import br.com.kira.kirabackend.exception.AcessoNegadoException;
import br.com.kira.kirabackend.exception.CodigoChegadaException;
import br.com.kira.kirabackend.exception.CodigoChegadaException.Motivo;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.AgendamentoRepository;
import br.com.kira.kirabackend.repository.CodigoChegadaRepository;
import br.com.kira.kirabackend.util.KiraTimeZone;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Confirmação presencial (Kira Confirm): a cliente gera um código de 6 dígitos quando está a caminho e o
 * mostra ao chegar; o estúdio digita o código e o agendamento passa a CHEGADA_CONFIRMADA.
 * <ul>
 *   <li>Só a cliente do agendamento gera e vê o código; só o estúdio do agendamento valida.</li>
 *   <li>Só com o agendamento confirmado e perto do horário: a partir de algumas horas antes até o fim.</li>
 *   <li>O código vale por poucos minutos e uma vez só. Depois de algumas tentativas erradas, deixa de valer
 *       e a cliente precisa gerar outro.</li>
 * </ul>
 */
@Service
@Transactional
public class ChegadaService {

    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final String AGENDAMENTO_NAO_ENCONTRADO = "Agendamento não encontrado";

    private final AgendamentoRepository agendamentoRepository;
    private final CodigoChegadaRepository codigoRepository;
    private final long validadeMinutos;
    private final long antecedenciaHoras;
    private final int tentativasMaximas;

    public ChegadaService(
            AgendamentoRepository agendamentoRepository,
            CodigoChegadaRepository codigoRepository,
            @Value("${kira.chegada.validade-minutos:15}") long validadeMinutos,
            @Value("${kira.chegada.antecedencia-horas:2}") long antecedenciaHoras,
            @Value("${kira.chegada.tentativas-maximas:5}") int tentativasMaximas) {
        this.agendamentoRepository = agendamentoRepository;
        this.codigoRepository = codigoRepository;
        this.validadeMinutos = validadeMinutos;
        this.antecedenciaHoras = antecedenciaHoras;
        this.tentativasMaximas = tentativasMaximas;
    }

    /** "Estou a caminho": devolve o código que ainda vale ou gera um novo. */
    public CodigoChegadaResponse gerar(UUID agendamentoId, UUID clienteId) {
        Agendamento agendamento = travar(agendamentoId);
        exigirCliente(agendamento, clienteId);
        LocalDateTime agora = agora();
        exigirConfirmadoNoHorario(agendamento, agora);

        CodigoChegada codigo = codigoRepository.findByAgendamentoId(agendamentoId)
                .orElseGet(() -> new CodigoChegada(agendamento));
        if (!codigo.ativo(agora) || codigo.getTentativasErradas() >= tentativasMaximas) {
            codigo.renovar(novoCodigo(), agora.plusMinutes(validadeMinutos));
            codigo = codigoRepository.save(codigo);
        }
        return paraResposta(codigo);
    }

    /** O código que ainda vale, para a cliente ver de novo. Sem código ativo, 404. */
    @Transactional(readOnly = true)
    public CodigoChegadaResponse ativo(UUID agendamentoId, UUID clienteId) {
        Agendamento agendamento = agendamentoRepository.findById(agendamentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(AGENDAMENTO_NAO_ENCONTRADO));
        exigirCliente(agendamento, clienteId);
        LocalDateTime agora = agora();
        return codigoRepository.findByAgendamentoId(agendamentoId)
                .filter(c -> agendamento.getStatus() == StatusAgendamento.CONFIRMADO)
                .filter(c -> c.ativo(agora) && c.getTentativasErradas() < tentativasMaximas)
                .map(ChegadaService::paraResposta)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Nenhum código de chegada ativo"));
    }

    /**
     * O estúdio confere o código. As tentativas erradas ficam gravadas mesmo com o erro devolvido ao app:
     * por isso a transação não é desfeita com {@link CodigoChegadaException}.
     */
    @Transactional(noRollbackFor = CodigoChegadaException.class)
    public AgendamentoResponse validar(UUID agendamentoId, UUID empresaId, String codigoDigitado) {
        Agendamento agendamento = travar(agendamentoId);
        if (!agendamento.getEmpresa().getId().equals(empresaId)) {
            throw new AcessoNegadoException("Este atendimento não pertence ao seu estabelecimento");
        }
        // Segundo envio do mesmo código (ex.: dois toques no botão): o app mostra "já utilizado"
        if (agendamento.getStatus() == StatusAgendamento.CHEGADA_CONFIRMADA) {
            throw new CodigoChegadaException(Motivo.TOKEN_JA_UTILIZADO);
        }
        LocalDateTime agora = agora();
        exigirConfirmadoNoHorario(agendamento, agora);

        CodigoChegada codigo = codigoRepository.findByAgendamentoId(agendamentoId)
                .orElseThrow(() -> new CodigoChegadaException(Motivo.TOKEN_INVALIDO));
        if (codigo.usado()) {
            throw new CodigoChegadaException(Motivo.TOKEN_JA_UTILIZADO);
        }
        if (codigo.expirado(agora) || codigo.getTentativasErradas() >= tentativasMaximas) {
            throw new CodigoChegadaException(Motivo.TOKEN_EXPIRADO);
        }
        if (!iguais(codigo.getCodigo(), codigoDigitado)) {
            codigo.setTentativasErradas(codigo.getTentativasErradas() + 1);
            codigoRepository.save(codigo);
            throw new CodigoChegadaException(codigo.getTentativasErradas() >= tentativasMaximas
                    ? Motivo.TOKEN_EXPIRADO
                    : Motivo.TOKEN_INVALIDO);
        }

        codigo.setUsadoEm(agora);
        codigoRepository.save(codigo);
        agendamento.setStatus(StatusAgendamento.CHEGADA_CONFIRMADA);
        return AgendamentoResponse.from(agendamentoRepository.save(agendamento));
    }

    private Agendamento travar(UUID agendamentoId) {
        return agendamentoRepository.travarParaChegada(agendamentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(AGENDAMENTO_NAO_ENCONTRADO));
    }

    private static void exigirCliente(Agendamento agendamento, UUID clienteId) {
        if (!agendamento.getCliente().getId().equals(clienteId)) {
            throw new AcessoNegadoException("Você só pode ver os seus próprios agendamentos");
        }
    }

    private void exigirConfirmadoNoHorario(Agendamento agendamento, LocalDateTime agora) {
        if (agendamento.getStatus() != StatusAgendamento.CONFIRMADO) {
            throw new RegraDeNegocioException("A chegada só pode ser confirmada em um agendamento confirmado");
        }
        if (agora.isBefore(agendamento.getDataHoraInicio().minusHours(antecedenciaHoras))) {
            throw new RegraDeNegocioException("O código de chegada fica disponível " + antecedenciaHoras
                    + (antecedenciaHoras == 1 ? " hora" : " horas") + " antes do horário marcado");
        }
        if (!agora.isBefore(agendamento.getDataHoraFim())) {
            throw new RegraDeNegocioException("O horário deste atendimento já terminou");
        }
    }

    /** Comparação em tempo constante: o tempo de resposta não revela quantos dígitos estavam certos. */
    private static boolean iguais(String esperado, String digitado) {
        return MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8),
                digitado.getBytes(StandardCharsets.UTF_8));
    }

    private static String novoCodigo() {
        return String.format("%06d", ALEATORIO.nextInt(1_000_000));
    }

    private static CodigoChegadaResponse paraResposta(CodigoChegada codigo) {
        return new CodigoChegadaResponse(codigo.getCodigo(),
                codigo.getExpiraEm().atZone(KiraTimeZone.DEFAULT).toOffsetDateTime());
    }

    private static LocalDateTime agora() {
        return LocalDateTime.now(KiraTimeZone.DEFAULT);
    }
}
