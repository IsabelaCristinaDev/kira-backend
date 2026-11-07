package br.com.kira.kirabackend.domain.entity;

import br.com.kira.kirabackend.domain.enums.StatusVerificacao;
import br.com.kira.kirabackend.domain.enums.TipoDocumentoIdentidade;
import br.com.kira.kirabackend.domain.enums.TipoUsuario;
import br.com.kira.kirabackend.util.KiraTimeZone;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Uma tentativa de verificação de identidade. Cada envio de fotos gera uma linha e a mais recente é a
 * situação atual da pessoa. Uma linha que ficou PENDING (serviço fora do ar) é reaproveitada no envio
 * seguinte e não conta como tentativa usada.
 *
 * Guarda só o resultado devolvido pelo serviço. Imagens e dados lidos pelo OCR (nome, CPF) nunca
 * entram aqui (LGPD).
 */
@Entity
@Table(name = "verificacao_identidade",
        indexes = @Index(name = "idx_verificacao_identidade_usuario", columnList = "usuario_id"))
@Getter
@Setter
@NoArgsConstructor
public class VerificacaoIdentidade {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_usuario", nullable = false, length = 20, updatable = false)
    private TipoUsuario tipoUsuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", nullable = false, length = 10)
    private TipoDocumentoIdentidade tipoDocumento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusVerificacao status = StatusVerificacao.PENDING;

    /** Códigos devolvidos pelo serviço (ex.: LOW_IMAGE_QUALITY), guardados como lista JSON. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> motivos = new ArrayList<>();

    @Column(precision = 7, scale = 4)
    private BigDecimal similaridade;

    @Column(name = "confianca_ocr", precision = 7, scale = 4)
    private BigDecimal confiancaOcr;

    /** Identificador da chamada no serviço, para cruzar os logs dos dois lados. */
    @Column(name = "request_id", length = 100)
    private String requestId;

    @Column(name = "versao_consentimento", nullable = false, length = 20)
    private String versaoConsentimento;

    @Column(name = "consentimento_aceito_em", nullable = false)
    private LocalDateTime consentimentoAceitoEm;

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "data_atualizacao", nullable = false)
    private LocalDateTime dataAtualizacao;

    @PrePersist
    protected void aoPersistir() {
        LocalDateTime agora = LocalDateTime.now(KiraTimeZone.DEFAULT);
        this.dataCriacao = agora;
        this.dataAtualizacao = agora;
        if (this.status == null) {
            this.status = StatusVerificacao.PENDING;
        }
        if (this.motivos == null) {
            this.motivos = new ArrayList<>();
        }
    }

    @PreUpdate
    protected void aoAtualizar() {
        this.dataAtualizacao = LocalDateTime.now(KiraTimeZone.DEFAULT);
    }
}
