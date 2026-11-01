package br.com.kira.kirabackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECPrivateKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebPushEnvioTest {

    private static byte[] b64(String valor) {
        return Base64.getUrlDecoder().decode(valor);
    }

    private static String b64(byte[] valor) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(valor);
    }

    @Test
    @DisplayName("A cifra bate byte a byte com o exemplo da RFC 8291")
    void exemploDaRfc() throws Exception {
        byte[] chavePublicaDoEnvio = b64("BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8");
        KeyPair chavesDoEnvio = new KeyPair(
                WebPushEnvio.lerChavePublica(chavePublicaDoEnvio),
                WebPushEnvio.lerChavePrivada(b64("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw")));

        byte[] resultado = WebPushEnvio.cifrar(
                "When I grow up, I want to be a watermelon".getBytes(StandardCharsets.UTF_8),
                b64("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4"),
                b64("BTBZMqHH6r4Tts7J_aSIgg"),
                chavesDoEnvio,
                b64("DGv6ra1nlYgDCS1FRnbzlw"));

        assertEquals(
                "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6Tlz"
                        + "AC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN",
                b64(resultado));
    }

    @Test
    @DisplayName("O envio chega ao serviço com o token válido, e só o navegador consegue decifrar")
    void envioDePontaAPonta() throws Exception {
        KeyPair chavesDoServidor = WebPushEnvio.gerarChaves();
        String publicaDoServidor = b64(WebPushEnvio.codificarPonto((ECPublicKey) chavesDoServidor.getPublic()));
        byte[] privadaDoServidor = coordenadaPrivada((ECPrivateKey) chavesDoServidor.getPrivate());
        KeyPair chavesDoNavegador = WebPushEnvio.gerarChaves();
        byte[] chaveDoNavegador = WebPushEnvio.codificarPonto((ECPublicKey) chavesDoNavegador.getPublic());
        byte[] segredo = new byte[16];
        Arrays.fill(segredo, (byte) 7);
        byte[] conteudo = "{\"titulo\":\"Ana\"}".getBytes(StandardCharsets.UTF_8);

        AtomicReference<byte[]> corpo = new AtomicReference<>();
        AtomicReference<String> autorizacao = new AtomicReference<>();
        AtomicReference<String> codificacao = new AtomicReference<>();
        HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/push", troca -> {
            corpo.set(troca.getRequestBody().readAllBytes());
            autorizacao.set(troca.getRequestHeaders().getFirst("Authorization"));
            codificacao.set(troca.getRequestHeaders().getFirst("Content-Encoding"));
            troca.sendResponseHeaders(201, -1);
            troca.close();
        });
        servidor.start();
        try {
            String endpoint = "http://localhost:" + servidor.getAddress().getPort() + "/push";
            WebPushEnvio envio = new WebPushEnvio(publicaDoServidor, b64(privadaDoServidor), "https://exemplo.test");

            int status = envio.enviar(endpoint, b64(chaveDoNavegador), b64(segredo), conteudo);

            assertEquals(201, status);
            assertEquals("aes128gcm", codificacao.get());
            String[] partes = autorizacao.get().replace("vapid t=", "").split(", k=");
            assertEquals(publicaDoServidor, partes[1]);
            Claims claims = Jwts.parser().verifyWith(chavesDoServidor.getPublic()).build()
                    .parseSignedClaims(partes[0]).getPayload();
            // Lido do token cru: os serviços de push exigem "aud" como texto, não como lista
            JsonNode token = new ObjectMapper().readTree(Base64.getUrlDecoder().decode(partes[0].split("\\.")[1]));
            assertTrue(token.get("aud").isTextual(), token.toString());
            assertEquals("http://localhost:" + servidor.getAddress().getPort(), token.get("aud").asText());
            assertEquals("https://exemplo.test", claims.getSubject());
            assertArrayEquals(conteudo, decifrarComoNavegador(corpo.get(), chavesDoNavegador, chaveDoNavegador, segredo));
        } finally {
            servidor.stop(0);
        }
    }

    @Test
    @DisplayName("Sem chaves, o envio fica desligado e a aplicação sobe normalmente")
    void semChaves() {
        WebPushEnvio envio = new WebPushEnvio("", "", "https://exemplo.test");

        assertFalse(envio.ativo());
        assertNull(envio.chavePublica());
    }

    @Test
    @DisplayName("Chave privada inválida desliga o envio em vez de derrubar a aplicação")
    void chaveInvalida() {
        WebPushEnvio envio = new WebPushEnvio("publica", "@@@", "https://exemplo.test");

        assertFalse(envio.ativo());
    }

    @Test
    @DisplayName("Chave do navegador fora do formato é recusada")
    void chaveDoNavegadorInvalida() {
        assertThrows(java.security.GeneralSecurityException.class, () -> WebPushEnvio.lerChavePublica(new byte[10]));
    }

    private static byte[] coordenadaPrivada(ECPrivateKey chave) {
        byte[] bytes = chave.getS().toByteArray();
        byte[] resultado = new byte[32];
        int copiar = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - copiar, resultado, 32 - copiar, copiar);
        return resultado;
    }

    private static byte[] decifrarComoNavegador(byte[] corpo, KeyPair navegador, byte[] chaveDoNavegador, byte[] segredo)
            throws Exception {
        byte[] sal = Arrays.copyOfRange(corpo, 0, 16);
        int tamanhoDaChave = corpo[20];
        byte[] chaveDoEnvio = Arrays.copyOfRange(corpo, 21, 21 + tamanhoDaChave);
        byte[] cifrado = Arrays.copyOfRange(corpo, 21 + tamanhoDaChave, corpo.length);

        KeyAgreement acordo = KeyAgreement.getInstance("ECDH");
        acordo.init(navegador.getPrivate());
        acordo.doPhase(WebPushEnvio.lerChavePublica(chaveDoEnvio), true);

        byte[] prkChave = hmac(segredo, acordo.generateSecret());
        byte[] ikm = hmac(prkChave, juntar("WebPush: info\0".getBytes(StandardCharsets.US_ASCII),
                chaveDoNavegador, chaveDoEnvio, new byte[]{1}));
        byte[] prk = hmac(sal, ikm);
        byte[] cek = Arrays.copyOf(hmac(prk, juntar("Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), new byte[]{1})), 16);
        byte[] nonce = Arrays.copyOf(hmac(prk, juntar("Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), new byte[]{1})), 12);

        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] claro = aes.doFinal(cifrado);
        assertEquals(2, claro[claro.length - 1]);
        return Arrays.copyOf(claro, claro.length - 1);
    }

    private static byte[] hmac(byte[] chave, byte[] dados) throws Exception {
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
}
