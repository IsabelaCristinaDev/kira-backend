package br.com.kira.kirabackend.domain.entity;

import br.com.kira.kirabackend.domain.enums.MotivoDenuncia;
import br.com.kira.kirabackend.domain.enums.StatusDenuncia;
import br.com.kira.kirabackend.util.KiraTimeZone;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "denuncia")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Denuncia {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "denunciante_id", nullable = false)
    private Usuario denunciante;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "denunciado_id", nullable = false)
    private Usuario denunciado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agendamento_id")
    private Agendamento agendamento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private MotivoDenuncia motivo;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(name = "risco_imediato", nullable = false)
    private Boolean riscoImediato = false;

    @Column(nullable = false)
    private Boolean anonima = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusDenuncia status = StatusDenuncia.PENDENTE;

    @Column(name = "autoridade_acionada", nullable = false)
    private Boolean autoridadeAcionada = false;

    @Column(nullable = false)
    private Boolean infundada = false;

    @Column(columnDefinition = "TEXT")
    private String parecer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analisada_por_id")
    private Usuario analisadaPor;

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "data_analise")
    private LocalDateTime dataAnalise;

    @PrePersist
    protected void aoPersistir() {
        this.dataCriacao = LocalDateTime.now(KiraTimeZone.DEFAULT);
        if (this.status == null) {
            this.status = StatusDenuncia.PENDENTE;
        }
    }
}
