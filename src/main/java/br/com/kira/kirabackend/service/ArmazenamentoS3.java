package br.com.kira.kirabackend.service;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.Optional;
import java.util.UUID;

/**
 * Guarda os arquivos num bucket do MinIO, do Cloudflare R2 ou de outro serviço compatível com S3. O bucket fica privado: as fotos
 * continuam sendo entregues pela API, em /api/arquivos/{nome}, como na pasta local.
 */
public class ArmazenamentoS3 implements ArmazenamentoArquivos {

    private final S3Client s3;
    private final String bucket;

    public ArmazenamentoS3(S3Client s3, String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
        prepararBucket();
    }

    // Confere na subida, como a pasta local: sem isso, o erro só aparece no primeiro envio de foto, como um 500
    private void prepararBucket() {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            criarBucket();
        } catch (SdkException e) {
            throw new IllegalStateException("Não foi possível acessar o bucket \"" + bucket
                    + "\". Confira se o serviço (MinIO ou R2) está no ar e KIRA_S3_ENDPOINT, KIRA_S3_ACCESS_KEY e KIRA_S3_SECRET_KEY", e);
        }
    }

    private void criarBucket() {
        try {
            s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        } catch (SdkException e) {
            throw new IllegalStateException(
                    "Não foi possível criar o bucket \"" + bucket + "\". Confira KIRA_S3_BUCKET", e);
        }
    }

    @Override
    public String salvar(byte[] conteudo, String extensao) {
        String nome = UUID.randomUUID() + "." + extensao;
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(nome).build(), RequestBody.fromBytes(conteudo));
        return nome;
    }

    @Override
    public Optional<Resource> abrir(String nome) {
        if (!ArmazenamentoArquivos.nomeValido(nome)) {
            return Optional.empty();
        }
        try {
            // As fotos têm no máximo 5 MB: cabem na memória
            byte[] conteudo = s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(nome).build())
                    .asByteArray();
            return Optional.of(new ByteArrayResource(conteudo));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    @Override
    public void apagar(String nome) {
        if (!ArmazenamentoArquivos.nomeValido(nome)) {
            return;
        }
        // Apagar um nome que não existe não dá erro no S3
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(nome).build());
    }
}
