package br.com.kira.kirabackend.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Código de 6 dígitos que a cliente mostra ao chegar ao estúdio. Um por agendamento: gerar de novo troca o código.
 * Fica guardado como texto porque a cliente precisa vê-lo de novo enquanto vale; dura poucos minutos,
 * vale uma vez só e é trocado depois de algumas tentativas erradas.
 */
@Entity
@Table(name = "codigo_chegada")
@Getter
@Setter
@NoArgsConstructor
public class CodigoChegada {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agendamento_id", nullable = false, unique = true)
    private Agendamento agendamento;

    @Column(nullable = false, length = 6)
    private String codigo;

    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    @Column(name = "usado_em")
    private LocalDateTime usadoEm;

    @Column(name = "tentativas_erradas", nullable = false)
    private int tentativasErradas;

    public CodigoChegada(Agendamento agendamento) {
        this.agendamento = agendamento;
    }

    /** Troca o código e zera a validade, o uso e as tentativas. */
    public void renovar(String novoCodigo, LocalDateTime novaExpiracao) {
        this.codigo = novoCodigo;
        this.expiraEm = novaExpiracao;
        this.usadoEm = null;
        this.tentativasErradas = 0;
    }

    public boolean usado() {
        return usadoEm != null;
    }

    /** Sem validade é um código que ainda não foi gerado: conta como vencido. */
    public boolean expirado(LocalDateTime agora) {
        return expiraEm == null || !agora.isBefore(expiraEm);
    }

    /** Ainda pode ser mostrado e validado. */
    public boolean ativo(LocalDateTime agora) {
        return !usado() && !expirado(agora);
    }
}
