package br.com.kira.kirabackend.domain.entity;

import br.com.kira.kirabackend.util.KiraTimeZone;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Entity
@Table(name = "token_recuperacao_senha")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TokenRecuperacaoSenha {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Só o SHA-256 do token: quem lê o banco não consegue usar o link enviado por e-mail.
    // A coluna continua "token" para o Hibernate (ddl-auto: update) não criar uma coluna nova
    @Column(name = "token", nullable = false, unique = true)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "data_expiracao", nullable = false)
    private LocalDateTime dataExpiracao;

    @Column(nullable = false)
    private Boolean usado = false;

    /** SHA-256 em hexadecimal: o mesmo token sempre gera o mesmo hash, então dá para buscar por ele. */
    public static String hashDoToken(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // Todo Java tem SHA-256 por especificação: não acontece
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    public boolean isExpirado() {
        return LocalDateTime.now(KiraTimeZone.DEFAULT).isAfter(dataExpiracao);
    }
}