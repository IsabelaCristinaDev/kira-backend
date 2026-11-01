package br.com.kira.kirabackend.service;

import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;

/**
 * Entrega de uma notificação ao serviço de push do navegador.
 * O conteúdo vai cifrado para aquele navegador (RFC 8291), e o serviço só o repassa.
 * O servidor se identifica com um token assinado (RFC 8292).
 * Feito com o Java e o JJWT do projeto: a biblioteca conhecida para isso traz dependências com alertas de segurança.
 */
@Component
public class WebPushEnvio {

    private static final Logger log = LoggerFactory.getLogger(WebPushEnvio.class);

    private static final int TAMANHO_DO_BLOCO = 4096;
    private static final int TEMPO_DE_VIDA_SEGUNDOS = 24 * 60 * 60;
    private static final Duration VALIDADE_DO_TOKEN = Duration.ofHours(12);
    private static final Duration TEMPO_LIMITE = Duration.ofSeconds(10);

    private static final Base64.Decoder BASE64 = Base64.getUrlDecoder();
    private static final Base64.Encoder BASE64_SEM_PREENCHIMENTO = Base64.getUrlEncoder().withoutPadding();

    private final SecureRandom aleatorio = new SecureRandom();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TEMPO_LIMITE).build();

    private final String chavePublica;
    private final String assunto;
    private final PrivateKey chavePrivada;

    public WebPushEnvio(
            @Value("${kira.notificacoes.web.chave-publica}") String chavePublica,
            @Value("${kira.notificacoes.web.chave-privada}") String chavePrivada,
            @Value("${kira.notificacoes.web.assunto}") String assunto) {
        this.assunto = assunto;
        PrivateKey privada = null;
        if (!chavePublica.isBlank() && !chavePrivada.isBlank()) {
            try {
                privada = lerChavePrivada(BASE64.decode(chavePrivada));
            } catch (GeneralSecurityException | IllegalArgumentException e) {
                // Sem notificação, o resto do sistema continua: o chat não depende disso
                log.error("Chaves de notificação inválidas: o envio ficará desligado");
            }
        }
        this.chavePrivada = privada;
        this.chavePublica = privada == null ? null : chavePublica;
    }

    public boolean ativo() {
        return chavePrivada != null;
    }

    /** Nula quando o envio não está configurado. */
    public String chavePublica() {
        return chavePublica;
    }

    /** Devolve o código HTTP do serviço de push. */
    public int enviar(String endpoint, String p256dh, String auth, byte[] conteudo)
            throws IOException, InterruptedException, GeneralSecurityException {
        KeyPair chavesDoEnvio = gerarChaves();
        byte[] sal = new byte[16];
        aleatorio.nextBytes(sal);
        byte[] corpo = cifrar(conteudo, BASE64.decode(p256dh), BASE64.decode(auth), chavesDoEnvio, sal);

        HttpRequest requisicao = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(TEMPO_LIMITE)
                .header("Content-Encoding", "aes128gcm")
                .header("Content-Type", "application/octet-stream")
                .header("TTL", String.valueOf(TEMPO_DE_VIDA_SEGUNDOS))
                .header("Urgency", "high")
                .header("Authorization", "vapid t=" + token(endpoint) + ", k=" + chavePublica)
                .POST(HttpRequest.BodyPublishers.ofByteArray(corpo))
                .build();
        return http.send(requisicao, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private String token(String endpoint) {
        URI uri = URI.create(endpoint);
        String origem = uri.getScheme() + "://" + uri.getAuthority();
        return Jwts.builder()
                .header().type("JWT").and()
                // Texto, não lista: é o que os serviços de push exigem (o JJWT gera lista por padrão)
                .audience().single(origem)
                .subject(assunto)
                .expiration(Date.from(Instant.now().plus(VALIDADE_DO_TOKEN)))
                .signWith(chavePrivada, Jwts.SIG.ES256)
                .compact();
    }

    /** Cifra como descreve a RFC 8291. O teste compara o resultado com o exemplo da própria RFC. */
    static byte[] cifrar(byte[] conteudo, byte[] chaveDoNavegador, byte[] segredo, KeyPair chavesDoEnvio, byte[] sal)
            throws GeneralSecurityException {
        byte[] chaveDoEnvio = codificarPonto((ECPublicKey) chavesDoEnvio.getPublic());

        KeyAgreement acordo = KeyAgreement.getInstance("ECDH");
        acordo.init(chavesDoEnvio.getPrivate());
        acordo.doPhase(lerChavePublica(chaveDoNavegador), true);
        byte[] segredoEcdh = acordo.generateSecret();

        byte[] prkChave = hmac(segredo, segredoEcdh);
        byte[] infoChave = juntar("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), chaveDoNavegador, chaveDoEnvio);
        byte[] ikm = expandir(prkChave, infoChave);
        byte[] prk = hmac(sal, ikm);
        byte[] cek = Arrays.copyOf(expandir(prk, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII)), 16);
        byte[] nonce = Arrays.copyOf(expandir(prk, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII)), 12);

        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        // O 0x02 marca o último bloco
        byte[] cifrado = aes.doFinal(juntar(conteudo, new byte[]{2}));

        return ByteBuffer.allocate(16 + 4 + 1 + chaveDoEnvio.length + cifrado.length)
                .put(sal)
                .putInt(TAMANHO_DO_BLOCO)
                .put((byte) chaveDoEnvio.length)
                .put(chaveDoEnvio)
                .put(cifrado)
                .array();
    }

    private static byte[] expandir(byte[] prk, byte[] info) throws GeneralSecurityException {
        return hmac(prk, juntar(info, new byte[]{1}));
    }

    private static byte[] hmac(byte[] chave, byte[] dados) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(chave, "HmacSHA256"));
        return mac.doFinal(dados);
    }

    private static byte[] juntar(byte[]... partes) {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        for (byte[] parte : partes) {
            saida.writeBytes(parte);
        }
        return saida.toByteArray();
    }

    private static ECParameterSpec curva() throws GeneralSecurityException {
        AlgorithmParameters parametros = AlgorithmParameters.getInstance("EC");
        parametros.init(new ECGenParameterSpec("secp256r1"));
        return parametros.getParameterSpec(ECParameterSpec.class);
    }

    static KeyPair gerarChaves() throws GeneralSecurityException {
        KeyPairGenerator gerador = KeyPairGenerator.getInstance("EC");
        gerador.initialize(new ECGenParameterSpec("secp256r1"));
        return gerador.generateKeyPair();
    }

    static PrivateKey lerChavePrivada(byte[] valor) throws GeneralSecurityException {
        return KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(new BigInteger(1, valor), curva()));
    }

    static ECPublicKey lerChavePublica(byte[] ponto) throws GeneralSecurityException {
        if (ponto.length != 65 || ponto[0] != 4) {
            throw new GeneralSecurityException("Chave pública em formato inválido");
        }
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(ponto, 1, 33));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(ponto, 33, 65));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), curva()));
    }

    static byte[] codificarPonto(ECPublicKey chave) {
        ByteBuffer ponto = ByteBuffer.allocate(65).put((byte) 4);
        ponto.put(coordenada(chave.getW().getAffineX()));
        ponto.put(coordenada(chave.getW().getAffineY()));
        return ponto.array();
    }

    // BigInteger pode vir com um zero a mais ou com zeros a menos: a coordenada tem sempre 32 bytes
    private static byte[] coordenada(BigInteger valor) {
        byte[] bytes = valor.toByteArray();
        byte[] resultado = new byte[32];
        int copiar = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - copiar, resultado, 32 - copiar, copiar);
        return resultado;
    }

    static String codificar(byte[] valor) {
        return BASE64_SEM_PREENCHIMENTO.encodeToString(valor);
    }
}
