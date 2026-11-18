#!/usr/bin/env bash
# Copia as fotos da pasta local (KIRA_ARQUIVOS_PASTA) para o bucket do MinIO, com os mesmos nomes.
# Usar uma vez, ao passar de KIRA_ARQUIVOS_TIPO=local para s3. Não apaga nada da pasta.
#
# Uso: scripts/copiar-fotos-para-minio.sh [pasta] [bucket]
#   pasta  padrão: ../kira-backend/uploads
#   bucket padrão: kira-arquivos
#
# Antes: o MinIO no ar (docker compose up -d) e a API iniciada uma vez com o tipo s3, que cria o bucket.
# As chaves são pedidas no terminal, e não ficam no comando nem no histórico.
set -euo pipefail

PASTA="$(realpath "${1:-../kira-backend/uploads}")"
BUCKET="${2:-kira-arquivos}"
ENDERECO="localhost:9000"
IMAGEM_MC="pgsty/mc:RELEASE.2026-10-26T00-00-00Z"

if [ ! -d "$PASTA" ]; then
    echo "Pasta não encontrada: $PASTA" >&2
    exit 1
fi

if [ "$(docker inspect -f '{{.State.Running}}' kira_minio 2>/dev/null)" != "true" ]; then
    echo "O MinIO não está no ar. Rode 'docker compose up -d' na pasta do backend." >&2
    exit 1
fi

read -rp "KIRA_S3_ACCESS_KEY: " CHAVE
read -rsp "KIRA_S3_SECRET_KEY: " SEGREDO
echo

# Configuração temporária do mc, só desta execução. As chaves entram pela entrada padrão,
# e não pelo comando, e a pasta é apagada no fim
CONFIG_MC="$(mktemp -d)"
trap 'rm -rf "$CONFIG_MC"' EXIT

mc() {
    docker run --rm -i --network host --user "$(id -u):$(id -g)" \
        -v "$CONFIG_MC":/config -v "$PASTA":/origem:ro "$IMAGEM_MC" --config-dir /config "$@"
}

if ! printf '%s\n%s\n' "$CHAVE" "$SEGREDO" | mc alias set kira "http://$ENDERECO" >/dev/null; then
    echo "O MinIO recusou as chaves. Use as mesmas KIRA_S3_ACCESS_KEY e KIRA_S3_SECRET_KEY do .env." >&2
    exit 1
fi
unset SEGREDO

if ! mc ls "kira/$BUCKET" >/dev/null; then
    echo "Não foi possível abrir o bucket \"$BUCKET\". Confira as chaves e se a API já subiu uma vez com KIRA_ARQUIVOS_TIPO=s3." >&2
    exit 1
fi

# mirror pula o que já está no bucket igual, então pode rodar de novo sem problema
mc mirror /origem "kira/$BUCKET"

NA_PASTA=$(find "$PASTA" -maxdepth 1 -type f | wc -l)
NO_BUCKET=$(mc ls "kira/$BUCKET" | wc -l)
echo
echo "Arquivos na pasta: $NA_PASTA"
echo "Arquivos no bucket: $NO_BUCKET"
if [ "$NO_BUCKET" -lt "$NA_PASTA" ]; then
    echo "Faltam arquivos no bucket. Veja os erros acima." >&2
    exit 1
fi
echo "Pronto. A pasta continua igual; apague só depois de conferir as fotos pelo app."
