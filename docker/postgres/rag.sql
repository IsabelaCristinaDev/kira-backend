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
-- As views leem de funções, e não direto das tabelas: assim as colunas ficam
-- livres para o Hibernate (ddl-auto: update) ajustar quando a API sobe. Se uma
-- coluna usada aqui for renomeada ou removida, a leitura do RAG dá erro até este
-- script ser ajustado.
--
-- As funções rodam com as permissões de quem roda este script (SECURITY DEFINER),
-- com search_path fixo, e só o grupo kira_leitura pode chamá-las: o kira_rag lê
-- os dados das views sem ter acesso às tabelas.
--
-- A tabela suspensao_perfil vem do canal de segurança. Se ela ainda não existir,
-- a view estudio fica sem o filtro de suspensão; rode o script de novo quando a
-- tabela existir para ligar o filtro.

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

-- Estúdios ativos e sem suspensão ativa
DO $$
DECLARE
  filtro_suspensao text := '';
BEGIN
  IF to_regclass('public.suspensao_perfil') IS NOT NULL THEN
    filtro_suspensao := 'AND NOT EXISTS (SELECT 1 FROM suspensao_perfil s
                                         WHERE s.usuario_id = e.id AND s.ativa IS TRUE)';
  ELSE
    RAISE NOTICE 'Tabela suspensao_perfil não existe: view estudio criada sem o filtro de suspensão. Rode este script de novo quando o canal de segurança estiver no banco.';
  END IF;
  EXECUTE format($f$
    CREATE OR REPLACE FUNCTION kira_leitura.ler_estudio()
    RETURNS TABLE (id uuid, nome varchar, bairro varchar, descricao text)
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path = public, pg_temp
    AS $body$
      SELECT e.id, u.nome, e.endereco_bairro, e.descricao
      FROM empresa e
      JOIN usuario u ON u.id = e.id
      WHERE u.ativo IS TRUE %s
    $body$
  $f$, filtro_suspensao);
END $$;

-- Horários só dos estúdios visíveis
CREATE OR REPLACE FUNCTION kira_leitura.ler_horario_estudio()
RETURNS TABLE (id uuid, estudio_id uuid, dia_semana varchar, hora_inicio time, hora_fim time)
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = public, pg_temp
AS $body$
  SELECT d.id, d.empresa_id, d.dia_semana, d.hora_inicio, d.hora_fim
  FROM disponibilidade_estudio d
  WHERE d.empresa_id IN (SELECT e.id FROM kira_leitura.ler_estudio() e)
$body$;

-- Serviços ativos dos estúdios visíveis
CREATE OR REPLACE FUNCTION kira_leitura.ler_servico()
RETURNS TABLE (id uuid, estudio_id uuid, nome varchar, descricao text, preco numeric, duracao_minutos integer)
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = public, pg_temp
AS $body$
  SELECT s.id, s.empresa_id, s.nome, s.descricao, s.preco, s.duracao_minutos
  FROM servico s
  WHERE s.ativo IS TRUE
    AND s.empresa_id IN (SELECT e.id FROM kira_leitura.ler_estudio() e)
$body$;

-- Quem roda o script de novo, vindo da versão com views diretas, troca as views
DROP VIEW IF EXISTS kira_leitura.servico, kira_leitura.horario_estudio, kira_leitura.estudio;
CREATE VIEW kira_leitura.estudio AS SELECT * FROM kira_leitura.ler_estudio();
CREATE VIEW kira_leitura.horario_estudio AS SELECT * FROM kira_leitura.ler_horario_estudio();
CREATE VIEW kira_leitura.servico AS SELECT * FROM kira_leitura.ler_servico();

-- Funções: ninguém além do kira_leitura executa (o padrão do Postgres libera para todos)
REVOKE ALL ON FUNCTION kira_leitura.ler_estudio(), kira_leitura.ler_horario_estudio(),
  kira_leitura.ler_servico() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION kira_leitura.ler_estudio(), kira_leitura.ler_horario_estudio(),
  kira_leitura.ler_servico() TO kira_leitura;

GRANT USAGE ON SCHEMA kira_leitura TO kira_leitura;
GRANT SELECT ON ALL TABLES IN SCHEMA kira_leitura TO kira_leitura;
GRANT kira_leitura TO kira_rag;
