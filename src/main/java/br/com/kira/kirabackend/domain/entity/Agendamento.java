package br.com.kira.kirabackend.domain.entity;

import br.com.kira.kirabackend.domain.enums.FormaPagamento;
import br.com.kira.kirabackend.domain.enums.StatusAgendamento;
import br.com.kira.kirabackend.util.KiraTimeZone;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "agendamento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Agendamento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "funcionaria_id")
    private Funcionaria funcionaria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "servico_id", nullable = false)
    private Servico servico;

    @Column(name = "data_hora_inicio", nullable = false)
    private LocalDateTime dataHoraInicio;

    @Column(name = "data_hora_fim", nullable = false)
    private LocalDateTime dataHoraFim;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusAgendamento status = StatusAgendamento.PENDENTE;

    /**
     * Preço do serviço no momento do agendamento. Fica guardado aqui para que uma mudança
     * de preço depois não altere o que a cliente combinou. Nulo nos agendamentos antigos,
     * criados antes deste campo existir.
     */
    @Column(precision = 10, scale = 2)
    private BigDecimal valor;

    /**
     * Valor combinado com a cliente: o guardado no agendamento ou, nos agendamentos antigos
     * (sem valor), o preço atual do serviço. Comissão e faturamento usam este valor.
     */
    public BigDecimal valorCombinado() {
        return valor != null ? valor : servico.getPreco();
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", length = 20)
    private FormaPagamento formaPagamento;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "lembrete_enviado", columnDefinition = "boolean not null default false")
    private Boolean lembreteEnviado = false;

    @PrePersist
    protected void aoPersistir() {
        this.dataCriacao = LocalDateTime.now(KiraTimeZone.DEFAULT);
        if (this.status == null) {
            this.status = StatusAgendamento.PENDENTE;
        }
        if (this.lembreteEnviado == null) {
            this.lembreteEnviado = false;
        }
    }
}