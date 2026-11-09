package br.com.kira.kirabackend.config;

import br.com.kira.kirabackend.service.ArmazenamentoArquivos;
import br.com.kira.kirabackend.service.ArmazenamentoS3;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * Liga o armazenamento S3 quando KIRA_ARQUIVOS_TIPO=s3: o MinIO no ambiente local e o Cloudflare R2 no servidor.
 * Sem isso, vale a pasta local.
 */
@Configuration
@ConditionalOnProperty(name = "kira.arquivos.tipo", havingValue = "s3")
public class ArmazenamentoS3Config {

    @Bean
    public S3Client s3Client(
            @Value("${kira.arquivos.s3.endpoint}") String endpoint,
            @Value("${kira.arquivos.s3.regiao}") String regiao,
            @Value("${kira.arquivos.s3.access-key}") String chaveDeAcesso,
            @Value("${kira.arquivos.s3.secret-key}") String chaveSecreta) {
        if (chaveDeAcesso.isBlank() || chaveSecreta.isBlank()) {
            throw new IllegalStateException("Com KIRA_ARQUIVOS_TIPO=s3, defina KIRA_S3_ACCESS_KEY e KIRA_S3_SECRET_KEY");
        }
        return S3Client.builder()
                // Explícito: é o único cliente HTTP do SDK no pom.xml
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .endpointOverride(URI.create(endpoint))
                // O MinIO ignora a região e o R2 usa "auto", mas o SDK exige uma
                .region(Region.of(regiao))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(chaveDeAcesso, chaveSecreta)))
                .serviceConfiguration(S3Configuration.builder()
                        // O bucket vai no caminho (servidor/bucket), e não no nome do servidor: MinIO e R2 aceitam
                        .pathStyleAccessEnabled(true)
                        // O envio em pedaços (padrão do SDK) faz o R2 recusar a assinatura com 403
                        .chunkedEncodingEnabled(false)
                        .build())
                // Checksum só quando a operação exige: o cálculo automático do SDK (desde a 2.30) não é
                // suportado por todos os serviços compatíveis com S3
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
    }

    @Bean
    public ArmazenamentoArquivos armazenamentoS3(S3Client s3Client,
                                                 @Value("${kira.arquivos.s3.bucket}") String bucket) {
        return new ArmazenamentoS3(s3Client, bucket);
    }
}
