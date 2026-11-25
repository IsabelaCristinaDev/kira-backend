-- Preparação do banco para o RAG (kira-ai).
-- Rodar no banco do Kira com o usuário dono das tabelas (DB_USERNAME do .env),
-- depois de a API ter subido ao menos uma vez. Pode rodar de novo sem erro.
--
-- O que cria:
--   - extensão vector (pgvector);
--   - usuário kira_rag (sem senha: definir depois com \password kira_rag);
--   - schema rag, do kira_rag, para as tabelas do RAG;
--   - grupo kira_leitura, que só lê as views do schema kira_leitura
--     (estudio, horario_estudio, servico), concedido ao kira_rag.
--
-- AVISO: as views dependem de colunas de empresa, usuario, servico,
-- disponibilidade_estudio e suspensao_perfil. O backend usa ddl-auto: update;
-- se uma dessas colunas for renomeada ou removida, a alteração do banco falha
-- até as views serem recriadas.
-- A tabela suspensao_perfil vem do canal de segurança. Se ela ainda não existir,
-- a view estudio é criada sem o filtro de suspensão; rode o script de novo
-- quando a tabela existir para ligar o filtro.

CREATE EXTENSION IF NOT EXISTS vector;

DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'kira_rag') THEN
    CREATE ROLE kira_rag LOGIN;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'kira_leitura') THEN
    CREATE ROLE kira_leitura NOLOGIN;
  END IF;
END $$;

DO $$ BEGIN
  EXECUTE format('GRANT CONNECT ON DATABASE %I TO kira_rag', current_database());
END $$;
CREATE SCHEMA IF NOT EXISTS rag AUTHORIZATION kira_rag;
ALTER ROLE kira_rag SET search_path = rag, public;

CREATE SCHEMA IF NOT EXISTS kira_leitura;

DO $$
BEGIN
  IF to_regclass('public.suspensao_perfil') IS NOT NULL THEN
    EXECUTE $v$
      CREATE OR REPLACE VIEW kira_leitura.estudio AS
      SELECT e.id, u.nome, e.endereco_bairro AS bairro, e.descricao
      FROM empresa e
      JOIN usuario u ON u.id = e.id
      WHERE u.ativo IS TRUE
        AND NOT EXISTS (SELECT 1 FROM suspensao_perfil s
                        WHERE s.usuario_id = e.id AND s.ativa IS TRUE)
    $v$;
  ELSE
    RAISE NOTICE 'Tabela suspensao_perfil não existe: view estudio criada sem o filtro de suspensão. Rode este script de novo quando o canal de segurança estiver no banco.';
    EXECUTE $v$
      CREATE OR REPLACE VIEW kira_leitura.estudio AS
      SELECT e.id, u.nome, e.endereco_bairro AS bairro, e.descricao
      FROM empresa e
      JOIN usuario u ON u.id = e.id
      WHERE u.ativo IS TRUE
    $v$;
  END IF;
END $$;

CREATE OR REPLACE VIEW kira_leitura.horario_estudio AS
SELECT d.id, d.empresa_id AS estudio_id, d.dia_semana, d.hora_inicio, d.hora_fim
FROM disponibilidade_estudio d
JOIN kira_leitura.estudio e ON e.id = d.empresa_id;

CREATE OR REPLACE VIEW kira_leitura.servico AS
SELECT s.id, s.empresa_id AS estudio_id, s.nome, s.descricao, s.preco, s.duracao_minutos
FROM servico s
JOIN kira_leitura.estudio e ON e.id = s.empresa_id
WHERE s.ativo IS TRUE;

GRANT USAGE ON SCHEMA kira_leitura TO kira_leitura;
GRANT SELECT ON ALL TABLES IN SCHEMA kira_leitura TO kira_leitura;
GRANT kira_leitura TO kira_rag;
