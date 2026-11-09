# KIRA Backend

API REST da KIRA, plataforma de agendamento de serviços de beleza. Projeto desenvolvido como Trabalho de Conclusão de Curso (TCC) no SENAI FATESG.

- **Autoras:** Isabela Cristina e Wanessa Cardoso
- **Front-end:** [kira-platform](https://github.com/wanessacastr/kira-platform)

## Funcionalidades

- **Contas:** cadastro de cliente e de empresa, login com JWT, renovação de sessão e recuperação de senha por e-mail.
- **Vitrine:** busca de estúdios, portfólio e avaliações, abertos sem login.
- **Agendamentos:** criação, cancelamento e conclusão de atendimentos, com registro da forma de pagamento.
- **Gestão da empresa:** serviços, funcionárias, disponibilidade, bloqueios de horário, agenda, comissões e painel com indicadores.
- **Comunicação:** mensagens em tempo real por WebSocket, notificações do navegador e lembrete de agendamento por e-mail.
- **Cliente:** favoritas, contatos de emergência e foto de perfil.
- **Provador virtual:** simulação de resultado com IA (Cloudflare Workers AI), condicionada ao consentimento da usuária, que pode ser revogado.
- **Verificação de identidade:** compara a foto do documento com a selfie pelo kira-verify-ai e guarda só o resultado. Pode ser exigida para agendar e para aparecer na vitrine.

> O pagamento é feito no estúdio. O sistema só registra a forma (dinheiro, cartão de crédito ou de débito, ou Pix), informada ao agendar ou ao concluir o atendimento. Não há integração com gateway de pagamento.

## Perfis de usuário

| Perfil | Descrição |
|---|---|
| `CLIENTE` | Busca estúdios, agenda serviços, avalia atendimentos e conversa com as empresas. |
| `EMPRESA` | Gerencia o próprio negócio. Pode ser do tipo `ESTABELECIMENTO` ou `AUTONOMA`. |
| `ADMIN` | Perfil previsto. As rotas `/api/admin/**` já são restritas a ele, mas ainda não há endpoints administrativos. |

## Tecnologias

- Java 17 e Spring Boot 3.3.6 (Web, Data JPA, Security, Validation, WebSocket, Mail, Actuator)
- PostgreSQL 16
- JWT (JJWT 0.12.3)
- Spring Retry
- springdoc-openapi (Swagger)
- Docker e Docker Compose
- JUnit 5, Mockito e Testcontainers
- JaCoCo, SonarCloud e GitHub Actions

## Como rodar

O projeto roda localmente. Não há ambiente de produção ativo.

**Pré-requisitos:** JDK 17 ou mais recente e Docker.

```bash
# 1. Criar o arquivo de configuração e preencher os valores
cp .env.example .env

# 2. Subir o PostgreSQL (porta 5433)
docker compose up -d

# 3. Subir a API (porta 8081)
./mvnw spring-boot:run
```

No Windows, use `mvnw.cmd` no lugar de `./mvnw`.

Com a API no ar:

- Swagger: <http://localhost:8081/swagger-ui.html>
- Status da aplicação: <http://localhost:8081/actuator/health>

As tabelas são criadas e atualizadas automaticamente pelo Hibernate (`ddl-auto: update`).

### Rodar num servidor

O arquivo `docker-compose.servidor.yml` sobe a API e o banco juntos, cada um no seu container:

```bash
cp .env.example .env   # preencher com os valores do servidor
docker compose -f docker-compose.servidor.yml up -d --build
```

- O banco não fica acessível de fora do servidor: só a API o enxerga.
- A API fica em `127.0.0.1:8081`, para ser publicada por um proxy com HTTPS. Para testar sem proxy, use `API_ENDERECO=0.0.0.0:8081`.
- O banco e as fotos enviadas ficam em volumes do Docker e continuam lá a cada novo deploy. Só `docker compose down -v` os apaga.
- Além das variáveis obrigatórias da tabela abaixo, o servidor exige `KIRA_CORS_ORIGENS` e `FRONTEND_URL` com o endereço real do app web. As `SPRING_DATASOURCE_*` não são usadas: a API aponta sozinha para o banco do compose.

### Levar os dados locais para o servidor (primeira subida)

O banco e as fotos andam juntos: o banco guarda só o nome de cada foto, e o arquivo fica na pasta `uploads/`. Por isso o dump é gerado na hora, junto com as fotos daquele momento. Um dump antigo aponta para fotos que podem não existir mais.

**1. Na máquina local**, gerar o dump do banco atual (fica fora do Git pelo `.gitignore`):

```bash
docker exec kira_postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > kira.dump
```

Copiar para o servidor o `kira.dump` e a pasta `uploads/`, junto com o projeto.

**2. No servidor**, com o `.env` preenchido. A ordem importa: o banco é restaurado **antes** de a API subir, senão o Hibernate cria tabelas vazias e a restauração falha.

```bash
DC="docker compose -f docker-compose.servidor.yml"

# Sobe só o banco
$DC up -d --wait postgres

# Restaura. --no-owner: o usuário do banco no servidor pode ter outro nome
$DC cp kira.dump postgres:/tmp/kira.dump
$DC exec -T postgres sh -c 'pg_restore --no-owner --no-privileges --single-transaction --exit-on-error -U "$POSTGRES_USER" -d "$POSTGRES_DB" /tmp/kira.dump && rm /tmp/kira.dump'

# Apaga as sessões do ambiente local: um refresh token antigo continuaria valendo no servidor
$DC exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "TRUNCATE refresh_token, token_recuperacao_senha, dispositivo_notificacao"'

# Sobe a API e copia as fotos para o volume
$DC up -d --wait --build
$DC cp uploads/. api:/app/uploads/
```

Se o banco do servidor já tiver dados, a restauração para no primeiro erro e não altera nada. Depois de conferir, apague o `kira.dump` do servidor: ele tem os dados de todos os usuários.

## Variáveis de ambiente

O arquivo `.env` é carregado automaticamente e nunca deve ir para o repositório. O modelo completo está em [`.env.example`](.env.example).

| Grupo | Variáveis | Obrigatório? |
|---|---|---|
| Banco (Docker) | `DB_USERNAME`, `DB_PASSWORD`, `DB_NAME` | Sim |
| Banco (aplicação) | `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | Sim |
| JWT | `JWT_SECRET`, `JWT_REFRESH_EXPIRATION` | Sim |
| E-mail | `MAIL_USERNAME`, `MAIL_PASSWORD` | Para enviar e-mails |
| Notificações do navegador | `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY`, `VAPID_SUBJECT` | Para usar as notificações |
| Provador virtual | `CLOUDFLARE_ACCOUNT_ID`, `CLOUDFLARE_API_TOKEN` | Para usar o provador |
| Verificação de identidade | `VERIFY_SERVICE_URL` (padrão `http://localhost:8001`, o kira-ai) e `VERIFY_SERVICE_KEY` (opcional) | Para usar a verificação |
| Exigir a verificação | `VERIFICACAO_OBRIGATORIA` | Não, o padrão é `false` |
| Servidor e front-end | `SERVER_PORT`, `FRONTEND_URL` | Não, têm valor padrão |
| Log do SQL | `MOSTRAR_SQL` | Não, o padrão é `false` |
| Fotos enviadas | `KIRA_ARQUIVOS_TIPO`, `KIRA_S3_*` (MinIO ou R2) ou `KIRA_ARQUIVOS_PASTA` | Não, o padrão é a pasta `./uploads` (ver abaixo) |
| Lembrete | `LEMBRETE_ANTECEDENCIA_HORAS`, `LEMBRETE_CRON`, `LEMBRETE_RETRY_*` | Não, têm valor padrão |

### Fotos enviadas

O banco guarda só o nome de cada foto; o arquivo fica num armazenamento de objetos compatível com S3, escolhido por `KIRA_ARQUIVOS_TIPO=s3`:

- **Local:** o MinIO do `docker-compose.yml`, com as chaves `KIRA_S3_ACCESS_KEY` e `KIRA_S3_SECRET_KEY` do `.env`. Painel em <http://localhost:9001>.
- **Servidor:** o Cloudflare R2. No painel da Cloudflare, em R2:
  1. crie o bucket `kira-arquivos`, sem acesso público (as fotos saem pela API);
  2. em *Manage API tokens*, crie um token com **Object Read & Write** só nesse bucket;
  3. no servidor, defina `KIRA_ARQUIVOS_TIPO=s3`, `KIRA_S3_ENDPOINT=https://<ID da conta>.r2.cloudflarestorage.com`, `KIRA_S3_REGIAO=auto` e as chaves do token em `KIRA_S3_ACCESS_KEY`/`KIRA_S3_SECRET_KEY`.

Sem `KIRA_ARQUIVOS_TIPO`, as fotos ficam na pasta `KIRA_ARQUIVOS_PASTA` (padrão `./uploads`). Para levar as fotos dessa pasta para o MinIO, use uma vez `scripts/copiar-fotos-para-minio.sh`.

#### Backup das fotos

Faça o backup das fotos junto com o do banco, no mesmo momento: um sem o outro deixa fotos sem registro ou registros sem foto.

- **R2:** os dados ficam na Cloudflare, sem volume para copiar. Para ter uma cópia fora dela, use `mc mirror` ou `rclone sync` do bucket para uma pasta.
- **MinIO local:** as fotos ficam no volume `kira-backend_kira_minio_data`. Com o MinIO parado, para a cópia ficar consistente:

```bash
docker compose stop minio
docker run --rm -v kira-backend_kira_minio_data:/data:ro -v "$PWD":/backup alpine \
  tar czf /backup/kira-minio.tar.gz -C /data .
docker compose start minio
```

Para restaurar num volume vazio, com o MinIO parado: o mesmo comando, sem o `:ro` no volume e com `tar xzf /backup/kira-minio.tar.gz -C /data`.

### Exigir a identidade verificada

Com `VERIFICACAO_OBRIGATORIA=true`:

- a cliente sem verificação não agenda: a API responde `403` com o código `VERIFICACAO_OBRIGATORIA`;
- o estúdio sem verificação sai da vitrine, da busca e do ranking do provador, e não recebe agendamento (`400`). O perfil dele continua abrindo pelo id;
- `GET /api/verificacao/me` devolve `"required": true`, para o app saber quando pedir a verificação.

Com `false`, o padrão, nada disso vale, e o backend avisa no log ao subir. Só ligue com o kira-verify-ai no ar: sem ele, ninguém consegue se verificar.

Para gerar uma chave JWT forte:

```bash
openssl rand -hex 64
```

## Arquitetura

O código fica em `br.com.kira.kirabackend`, organizado em camadas:

| Pacote | Responsabilidade |
|---|---|
| `controller` | Endpoints REST. Recebem Request e devolvem Response, sem regra de negócio. |
| `service` | Regras de negócio e transações. |
| `service/strategy` | Regras que variam conforme o contexto. |
| `repository` | Acesso a dados com Spring Data JPA. |
| `domain` | Entidades JPA e enums. |
| `dto` | Objetos de entrada (`request`) e saída (`response`) da API. |
| `security` | Autenticação JWT e controle de acesso a recursos. |
| `observer` | Eventos de domínio e seus listeners. |
| `job` | Tarefas agendadas, como o lembrete de agendamento. |
| `exception` | Exceções de negócio e tratamento global de erros. |
| `config` | Configurações do Spring, segurança e WebSocket. |

### Padrões de projeto

- **Strategy:** o cancelamento segue regras diferentes para cada lado. A cliente precisa de no mínimo 2 horas de antecedência, e a empresa não tem prazo. Cada regra fica em uma classe, então uma regra nova não altera as existentes.
- **Observer:** efeitos colaterais de um agendamento ficam em listeners de eventos, separados do fluxo principal. A comissão, por exemplo, é gerada ao tratar o evento `AtendimentoConcluidoEvent`.
- **DTO:** nenhuma entidade JPA sai do controller. Assim, a API não expõe dados internos, e a entrada é validada com Bean Validation.

## Segurança

- A API é stateless e autentica pelo header `Authorization: Bearer <token>`.
- As rotas exigem login por padrão. As públicas são declaradas uma a uma em `SecurityConfig`.
- Sem token ou com token vencido, a resposta é `401`. Logado e sem permissão, é `403`.
- O acesso a recursos de outra empresa ou de outra cliente é bloqueado nos services.
- As senhas são guardadas com BCrypt, e os segredos ficam apenas no `.env`.

## Testes

```bash
./mvnw test
```

- **Unitários:** services, strategies, segurança e job, com JUnit 5 e Mockito.
- **Integração:** os testes do pacote `integracao` usam um PostgreSQL real, criado pelo Testcontainers. Sem Docker, eles são ignorados.
- **`KiraBackendApplicationTests`:** confere que a aplicação inteira sobe, com um PostgreSQL do Testcontainers. Não precisa do banco local nem das variáveis da máquina; sem Docker, é ignorado.

O relatório de cobertura do JaCoCo é gerado em `target/site/jacoco/index.html`. O GitHub Actions roda os testes em cada pull request e em cada push na `main`.
