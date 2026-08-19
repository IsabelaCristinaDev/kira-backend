package br.com.kira.kirabackend.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "comissao_agendamento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ComissaoAgendamento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agendamento_id", nullable = false, unique = true)
    private Agendamento agendamento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "funcionaria_id", nullable = false)
    private Funcionaria funcionaria;

    @Column(name = "valor_servico", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorServico;

    @Column(name = "percentual_comissao", nullable = false, precision = 5, scale = 2)
    private BigDecimal percentualComissao;

    @Column(name = "valor_comissao", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorComissao;

    @Column(name = "data_calculo", nullable = false)
    private LocalDateTime dataCalculo;

    @PrePersist
    protected void aoPersistir() {
        this.dataCalculo = LocalDateTime.now();
    }
}