package br.com.kira.kirabackend.dto.response;

import br.com.kira.kirabackend.domain.enums.CategoriaServico;
import br.com.kira.kirabackend.domain.enums.Genero;
import br.com.kira.kirabackend.domain.enums.StatusVerificacao;
import br.com.kira.kirabackend.domain.enums.TipoDocumentoIdentidade;
import br.com.kira.kirabackend.domain.enums.TipoEstabelecimento;
import br.com.kira.kirabackend.domain.enums.TipoUsuario;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Cópia dos dados pessoais da conta logada (LGPD, direito de acesso e portabilidade).
 * Nunca leva senha, hash, tokens, chaves completas de notificação nem imagens da verificação de identidade.
 *
 * @param estudio nulo para cliente; para estúdio, traz serviços, funcionárias, portfólio e horário
 * @param fotos   endereços de todas as fotos da conta (perfil e, no estúdio, portfólio)
 */
public record DadosPessoaisResponse(
        LocalDateTime geradoEm,
        TipoUsuario tipoConta,
        Conta conta,
        List<AgendamentoResponse> agendamentos,
        List<AvaliacaoResponse> avaliacoesFeitas,
        List<AvaliacaoResponse> avaliacoesRecebidas,
        List<MensagemResponse> mensagensEnviadas,
        List<EstudioFavorito> favoritas,
        List<ContatoEmergenciaResponse> contatosEmergencia,
        Consentimentos consentimentos,
        List<Dispositivo> dispositivosNotificacao,
        List<String> fotos,
        Estudio estudio
) {

    /** Campos que não se aplicam ao tipo da conta vêm nulos (ex.: CNPJ de cliente, preferências de estúdio). */
    public record Conta(
            UUID id,
            String nome,
            String email,
            String telefone,
            String cpf,
            String cnpj,
            LocalDate dataNascimento,
            Genero genero,
            EnderecoResponse endereco,
            Double latitude,
            Double longitude,
            String fotoUrl,
            LocalDateTime dataCriacao,
            String descricao,
            List<String> especialidades,
            TipoEstabelecimento tipoEstabelecimento,
            List<CategoriaServico> preferencias
    ) {}

    public record EstudioFavorito(UUID id, String nome, LocalDateTime salvoEm) {}

    /** @param provador nulo se a pessoa nunca aceitou o termo */
    public record Consentimentos(Provador provador, List<Verificacao> verificacoes) {}

    public record Provador(String versao, LocalDateTime aceitoEm, LocalDateTime revogadoEm) {}

    /** Só o resultado de cada tentativa: nada de imagem, pontuação ou dado lido do documento. */
    public record Verificacao(
            TipoDocumentoIdentidade tipoDocumento,
            StatusVerificacao status,
            List<String> motivos,
            String versaoConsentimento,
            LocalDateTime consentimentoAceitoEm,
            LocalDateTime dataCriacao
    ) {}

    /** @param servico só o domínio do serviço de notificação (ex.: fcm.googleapis.com), sem o endereço completo */
    public record Dispositivo(UUID id, String servico, LocalDateTime dataCriacao) {}

    public record Estudio(
            List<ServicoResponse> servicos,
            List<FuncionariaResponse> funcionarias,
            List<FotoPortfolioResponse> portfolio,
            List<DisponibilidadeResponse> horario
    ) {}
}
