# Postgres do Kira com a extensão pgvector (busca por vetores do kira-ai).
# Mantém a base Alpine da imagem anterior: trocar para a imagem oficial do pgvector (Debian)
# mudaria a ordenação de texto e deixaria os índices já gravados no volume fora de ordem.
ARG PG_IMAGE=postgres:16.15-alpine3.24
FROM ${PG_IMAGE}

ARG PGVECTOR_VERSION=v0.8.7

# OPTFLAGS="" compila sem -march=native, para a extensão rodar em qualquer máquina.
# with_llvm=no pula o código para JIT, que exigiria o clang da mesma versão do LLVM da imagem.
RUN apk add --no-cache --virtual .build-deps git build-base \
    && git clone --depth 1 --branch "${PGVECTOR_VERSION}" https://github.com/pgvector/pgvector.git /tmp/pgvector \
    && make -C /tmp/pgvector OPTFLAGS="" with_llvm=no \
    && make -C /tmp/pgvector install with_llvm=no \
    && rm -rf /tmp/pgvector \
    && apk del .build-deps
