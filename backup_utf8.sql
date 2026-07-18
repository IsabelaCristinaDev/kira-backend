--
-- PostgreSQL database dump
--

\restrict CiXw8j8CQwnATQcck2owedqKjU5pvqqk5w4bQvsy82jVCVECmckNLDcz4eaAkCI

-- Dumped from database version 16.14
-- Dumped by pg_dump version 16.14

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: admin; Type: TABLE; Schema: public; Owner: kira_user
--

CREATE TABLE public.admin (
    nivel_acesso character varying(50),
    id uuid NOT NULL
);


ALTER TABLE public.admin OWNER TO kira_user;

--
-- Name: agendamento; Type: TABLE; Schema: public; Owner: kira_user
--

CREATE TABLE public.agendamento (
    id uuid NOT NULL,
    data_criacao timestamp(6) without time zone NOT NULL,
    data_hora_fim timestamp(6) without time zone NOT NULL,
    data_hora_inicio timestamp(6) without time zone NOT NULL,
    forma_pagamento character varying(20),
    observacoes text,
    status character varying(20) NOT NULL,
    cliente_id uuid NOT NULL,
    empresa_id uuid NOT NULL,
    funcionaria_id uuid,
    servico_id uuid NOT NULL,
    CONSTRAINT agendamento_forma_pagamento_check CHECK (((forma_pagamento)::text = ANY ((ARRAY['DINHEIRO'::character varying, 'CARTAO_CREDITO'::character varying, 'CARTAO_DEBITO'::character varying, 'PIX'::character varying])::text[]))),
    CONSTRAINT agendamento_status_check CHECK (((status)::text = ANY ((ARRAY['PENDENTE'::character varying, 'CONFIRMADO'::character varying, 'CANCELADO'::character varying, 'CONCLUIDO'::character varying, 'REAGENDADO'::character varying])::text[])))
);


ALTER TABLE public.agendamento OWNER TO kira_user;

--
-- Name: avaliacao; Type: TABLE; Schema: public; Owner: kira_user
--

CREATE TABLE public.avaliacao (
    id uuid NOT NULL,
    comentario text,
    data_criacao timestamp(6) without time zone NOT NULL,
    nota integer NOT NULL,
    tipo_avaliador character varying(20) NOT NULL,
    agendamento_id uuid NOT NULL,
    cliente_id uuid NOT NULL,
    empresa_id uuid NOT NULL,
    CONSTRAINT avaliacao_tipo_avaliador_check CHECK (((tipo_avaliador)::text = ANY ((ARRAY['CLIENTE'::character varying, 'EMPRESA'::character varying])::text[])))
);


ALTER TABLE public.avaliacao OWNER TO kira_user;

--
-- Name: bloqueio_horario; Type: TABLE; Schema: public; Owner: kira_user
--

CREATE TABLE public.bloqueio_horario (
    id uuid NOT NULL,
    data_hora_fim timestamp(6) without time zone NOT NULL,
    data_hora_inicio timestamp(6) without time zone NOT NULL,
    motivo character varying(200),
    funcionaria_id uuid NOT NULL
);


ALTER TABLE public.bloqueio_horario OWNER TO kira_user;

--
-- Name: cliente; Type: TABLE; Schema: public; Owner: kira_user
--

CREATE TABLE public.cliente (
    cpf character varying(14) NOT NULL,
    data_nascimento date,
    id uuid NOT NULL,
    endereco_bairro character varying(100),
    endereco_cep character varying(8),
    endereco_cidade character varying(100),
    endereco_complemento character varying(100),
    endereco_estado character varying(2),
    endereco_logradouro character varying(200),
    endereco_numero character varying(20),
    genero character varying(30),
    latitude double precision,
    longitude double precision,
    CONSTRAINT cliente_genero_check CHECK (((genero)::text = ANY ((ARRAY['FEMININO'::character varying, 'MASCULINO'::character varying, 'NAO_BINARIO'::character varying, 'PREFIRO_NAO_INFORMAR'::character varying])::text[])))
);


ALTER TABLE public.cliente OWNER TO kira_user;

--
-- Name: disponibilidade_estudio; Type: TABLE; Schema: public; Owner: kira_user
--

CREATE TABLE public.disponibilidade_estudio (
    id uuid NOT NULL,
    dia_semana character varying(10) NOT NULL,
    hora_fim time(6) without time zone NOT NULL,
    hora_inicio time(6) without time zone NOT NULL,
    empresa_id uuid NOT NULL,
    CONSTRAINT disponibilidade_estudio_dia_semana_check CHECK (((dia_semana)::text = ANY ((ARRAY['DOMINGO'::character varying, 'SEGUNDA'::character varying, 'TERCA'::character varying, 'QUARTA'::character varying, 'QUINTA'::character varying, 'SEXTA'::character varying, 'SABADO'::character varying])::text[])))
);


ALTER TABLE public.disponibilidade_estudio OWNER TO kira_user;

--
-- Name: empresa; Type: TABLE; Schema: public; Owner: kira_user
--

CREATE TABLE public.empresa (
    cnpj character varying(18),
    descricao text,
    id uuid NOT NULL,
    tipo_estabelecimento character varying(20) NOT NULL,
    cpf character varying(14),
    data_nascimento date,
    endereco_bairro character varying(100),
    endereco_cep character varying(8),
    endereco_cidade character varying(100),
    endereco_complemento character varying(100),
    endereco_estado character varying(2),
    endereco_logradouro character varying(200),
    endereco_numero character varying(20),
    especialidades text,
    latitude double precision,
    longitude double precision,
    CONSTRAINT empresa_tipo_estabelecimento_check CHECK (((tipo_estabelecimento)::text = ANY ((ARRAY['ESTABELECIMENTO'::character varying, 'AUTONOMA'::character varying])::text[])))
);


ALTER TABLE public.empresa OWNER TO kira_user;

--
-- Name: funcionaria; Type: TABLE; Schema: public; Owner: kira_user
--

CREATE TABLE public.funcionaria (
    id uuid NOT NULL,
    ativo boolean NOT NULL,
    especialidades text,
    foto_url character varying(255),
    nome character varying(150) NOT NULL,
    empresa_id uuid NOT NULL
);


ALTER TABLE public.funcionaria OWNER TO kira_user;

--
-- Name: mensagem; Type: TABLE; Schema: public; Owner: kira_user
--

CREATE TABLE public.mensagem (
    id uuid NOT NULL,
    conteudo text NOT NULL,
    data_envio timestamp(6) without time zone NOT NULL,
    lida boolean NOT NULL,
    agendamento_id uuid NOT NULL,
    remetente_id uuid NOT NULL
);


ALTER TABLE public.mensagem OWNER TO kira_user;

--
-- Name: servico; Type: TABLE; Schema: public; Owner: kira_user
--

CREATE TABLE public.servico (
    id uuid NOT NULL,
    ativo boolean NOT NULL,
    descricao text,
    duracao_minutos integer NOT NULL,
    nome character varying(150) NOT NULL,
    preco numeric(10,2) NOT NULL,
    empresa_id uuid NOT NULL
);


ALTER TABLE public.servico OWNER TO kira_user;

--
-- Name: token_recuperacao_senha; Type: TABLE; Schema: public; Owner: kira_user
--

CREATE TABLE public.token_recuperacao_senha (
    id uuid NOT NULL,
    data_expiracao timestamp(6) without time zone NOT NULL,
    token character varying(255) NOT NULL,
    usado boolean NOT NULL,
    usuario_id uuid NOT NULL
);


ALTER TABLE public.token_recuperacao_senha OWNER TO kira_user;

--
-- Name: usuario; Type: TABLE; Schema: public; Owner: kira_user
--

CREATE TABLE public.usuario (
    id uuid NOT NULL,
    ativo boolean NOT NULL,
    data_criacao timestamp(6) without time zone NOT NULL,
    email character varying(150) NOT NULL,
    foto_url character varying(255),
    nome character varying(150) NOT NULL,
    senha_hash character varying(255) NOT NULL,
    telefone character varying(20)
);


ALTER TABLE public.usuario OWNER TO kira_user;

--
-- Data for Name: admin; Type: TABLE DATA; Schema: public; Owner: kira_user
--

COPY public.admin (nivel_acesso, id) FROM stdin;
\.


--
-- Data for Name: agendamento; Type: TABLE DATA; Schema: public; Owner: kira_user
--

COPY public.agendamento (id, data_criacao, data_hora_fim, data_hora_inicio, forma_pagamento, observacoes, status, cliente_id, empresa_id, funcionaria_id, servico_id) FROM stdin;
446fe0b4-f3e5-4673-bae4-30b3c4aa62a5	2026-06-27 09:14:57.081079	2026-06-28 11:00:00	2026-06-28 10:00:00	PIX	Primeira vez no studio	CONCLUIDO	76e2de87-280a-4466-889f-a7c79dd662cb	f6b0fc27-56c8-4f55-a938-8cb114032407	\N	ca1776fc-30c7-4415-a11e-ed452ae840e1
0cc8fe9c-bfad-4ebc-9375-26794436c914	2026-07-04 19:04:44.065463	2026-07-20 15:00:00	2026-07-20 14:00:00	PIX	Teste de reagendamento	REAGENDADO	76e2de87-280a-4466-889f-a7c79dd662cb	f6b0fc27-56c8-4f55-a938-8cb114032407	\N	ca1776fc-30c7-4415-a11e-ed452ae840e1
f122c78c-1238-4df1-bdff-1b539c8c9f9f	2026-07-04 19:06:42.204852	2026-07-25 15:00:00	2026-07-25 14:00:00	PIX	Teste de reagendamento	CANCELADO	76e2de87-280a-4466-889f-a7c79dd662cb	f6b0fc27-56c8-4f55-a938-8cb114032407	\N	ca1776fc-30c7-4415-a11e-ed452ae840e1
1d64ad16-dfc2-45ce-92e5-9215469d2974	2026-07-07 18:44:26.836707	2026-08-01 11:00:00	2026-08-01 10:00:00	PIX	Teste cancelamento cliente	PENDENTE	76e2de87-280a-4466-889f-a7c79dd662cb	f6b0fc27-56c8-4f55-a938-8cb114032407	\N	ca1776fc-30c7-4415-a11e-ed452ae840e1
3c6eb1ec-e028-4c31-ba0a-c630ff72380f	2026-07-07 18:56:14.104636	2026-08-01 11:00:00	2026-08-01 10:00:00	PIX	Teste cancelamento cliente	PENDENTE	76e2de87-280a-4466-889f-a7c79dd662cb	f6b0fc27-56c8-4f55-a938-8cb114032407	\N	ca1776fc-30c7-4415-a11e-ed452ae840e1
469ea21d-1848-4899-ba02-ee60529bf37e	2026-07-05 11:16:38.577284	2026-07-05 11:00:00	2026-07-05 10:00:00	PIX	Teste agenda funcionaria	CANCELADO	76e2de87-280a-4466-889f-a7c79dd662cb	f6b0fc27-56c8-4f55-a938-8cb114032407	044363a3-d1e1-4cfb-baba-98e5e4982676	ca1776fc-30c7-4415-a11e-ed452ae840e1
\.


--
-- Data for Name: avaliacao; Type: TABLE DATA; Schema: public; Owner: kira_user
--

COPY public.avaliacao (id, comentario, data_criacao, nota, tipo_avaliador, agendamento_id, cliente_id, empresa_id) FROM stdin;
2f6ed84c-d141-488c-9888-5252110390eb	Atendimento excelente, adorei o corte!	2026-06-28 09:47:23.27781	5	CLIENTE	446fe0b4-f3e5-4673-bae4-30b3c4aa62a5	76e2de87-280a-4466-889f-a7c79dd662cb	f6b0fc27-56c8-4f55-a938-8cb114032407
\.


--
-- Data for Name: bloqueio_horario; Type: TABLE DATA; Schema: public; Owner: kira_user
--

COPY public.bloqueio_horario (id, data_hora_fim, data_hora_inicio, motivo, funcionaria_id) FROM stdin;
3af6d90c-600a-47b6-b1ec-cda3d3cfea20	2026-07-10 18:00:00	2026-07-10 09:00:00	Folga	044363a3-d1e1-4cfb-baba-98e5e4982676
\.


--
-- Data for Name: cliente; Type: TABLE DATA; Schema: public; Owner: kira_user
--

COPY public.cliente (cpf, data_nascimento, id, endereco_bairro, endereco_cep, endereco_cidade, endereco_complemento, endereco_estado, endereco_logradouro, endereco_numero, genero, latitude, longitude) FROM stdin;
12345678901	\N	76e2de87-280a-4466-889f-a7c79dd662cb	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N
71133378722	2003-02-10	47d6dda3-250c-4b94-8f16-6961cd8b813a	Setor Leste Vila Nova	74635040	Goi├ónia	502A	GO	Av. Industrial	364	FEMININO	-16.66535803079227	-49.24012545580871
98765432100	1995-05-15	e8a44cb0-161e-41cf-995f-e881de44b451	Setor Bueno	74810100	Goi├ónia	Apto 201	GO	Rua T-30	100	FEMININO	-16.6869	-49.2648
857.178.330-67	1995-06-15	f334ede1-45ff-42cb-90f6-314bef71f12a	Setor Central	74000000	Goi├ónia	Apto 2	GO	Rua das Flores	123	FEMININO	-16.6869	-49.2648
\.


--
-- Data for Name: disponibilidade_estudio; Type: TABLE DATA; Schema: public; Owner: kira_user
--

COPY public.disponibilidade_estudio (id, dia_semana, hora_fim, hora_inicio, empresa_id) FROM stdin;
128dba04-a945-42bb-b101-fd92ba72bf50	SABADO	18:00:00	09:00:00	f6b0fc27-56c8-4f55-a938-8cb114032407
\.


--
-- Data for Name: empresa; Type: TABLE DATA; Schema: public; Owner: kira_user
--

COPY public.empresa (cnpj, descricao, id, tipo_estabelecimento, cpf, data_nascimento, endereco_bairro, endereco_cep, endereco_cidade, endereco_complemento, endereco_estado, endereco_logradouro, endereco_numero, especialidades, latitude, longitude) FROM stdin;
12.345.678/0001-99	Studio de beleza	f6b0fc27-56c8-4f55-a938-8cb114032407	ESTABELECIMENTO	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N
98.765.432/0001-10	Tratamentos faciais, corporais e depila├º├úo a laser	e7175636-d156-4d38-937a-abe91aa69496	ESTABELECIMENTO	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N
\N	Design de sobrancelhas, micropigmenta├º├úo e extens├úo de c├¡lios	63d20320-bb5f-4a67-aff0-28c0929dbc7a	AUTONOMA	65432178900	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N
56.789.012/0001-55	Design de sobrancelhas premium, micropigmenta├º├úo labial e c├¡lios	b81b124a-0f52-4f60-983d-a0c20e8623fa	ESTABELECIMENTO	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N
23.456.789/0001-44	Microagulhamento, carboxiterapia e remo├º├úo de tatuagem a laser	cdea1e42-7129-45db-acf7-a76a345d61bf	ESTABELECIMENTO	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N
\N	Cortes modernos, colorimetria e tratamentos capilares avan├ºados	03372e5e-63bd-4cc4-ac36-c150d034e058	AUTONOMA	11122233344	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N
\N	Maquiagem profissional para noivas, formandas e eventos sociais	cc764454-c6a3-4e05-beff-be734a766ad9	AUTONOMA	55566677788	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N
83282651000149	\N	b7160af9-d283-46c5-a811-492544d29893	ESTABELECIMENTO	\N	1998-04-20	Nossa Senhora das Gra├ºas	44444444	Santo Ant├┤nio de Jesus	sala 8	BA	Rua Via Coletora B44	44	Unhas	-16.665289823246837	-49.24015883400061
12345678000199	Studio de beleza especializado	c9eaa85d-96b3-41a8-8573-b3478f750bb1	ESTABELECIMENTO	\N	\N	Setor Sul	74000001	Goi├ónia	Sala 3	GO	Av. Goi├ís	500	Cabelo, Unhas, Maquiagem	-16.6869	-49.2648
\N	Manicure e pedicure a domic├¡lio	b66f28e1-4734-42f4-996b-104de367ef51	AUTONOMA	60867760087	1990-03-20	Setor Norte	74000002	Goi├ónia	\N	GO	Rua das Palmeiras	45	Unhas, Pedicure	-16.6869	-49.2648
\.


--
-- Data for Name: funcionaria; Type: TABLE DATA; Schema: public; Owner: kira_user
--

COPY public.funcionaria (id, ativo, especialidades, foto_url, nome, empresa_id) FROM stdin;
044363a3-d1e1-4cfb-baba-98e5e4982676	t	\N	\N	Ana Paula	f6b0fc27-56c8-4f55-a938-8cb114032407
85932bac-a475-4538-989c-e05665b14d1f	t	Corte, Escova e Colora├º├úo	\N	Ana Paula	f6b0fc27-56c8-4f55-a938-8cb114032407
\.


--
-- Data for Name: mensagem; Type: TABLE DATA; Schema: public; Owner: kira_user
--

COPY public.mensagem (id, conteudo, data_envio, lida, agendamento_id, remetente_id) FROM stdin;
c912c1b0-7aa4-4aec-9238-8f0c283a1cfa	Ol├í! Gostaria de confirmar meu hor├írio para amanh├ú.	2026-07-05 11:41:04.717611	f	469ea21d-1848-4899-ba02-ee60529bf37e	76e2de87-280a-4466-889f-a7c79dd662cb
\.


--
-- Data for Name: servico; Type: TABLE DATA; Schema: public; Owner: kira_user
--

COPY public.servico (id, ativo, descricao, duracao_minutos, nome, preco, empresa_id) FROM stdin;
ca1776fc-30c7-4415-a11e-ed452ae840e1	t	Corte moderno e personalizado	60	Corte Feminino	80.00	f6b0fc27-56c8-4f55-a938-8cb114032407
bfc9053a-5e54-4e09-9075-d9856704e15d	t	\N	60	Unha em gel	80.00	b7160af9-d283-46c5-a811-492544d29893
ebf59497-8e47-4527-84dd-9119034e992a	t	\N	60	Corte feminino	80.00	c9eaa85d-96b3-41a8-8573-b3478f750bb1
299cd42f-7575-4083-8424-b2157f5267fb	t	\N	45	Manicure	45.00	c9eaa85d-96b3-41a8-8573-b3478f750bb1
64b51662-6b05-4d88-b220-7ad06bbab1f2	t	\N	40	Manicure	35.00	b66f28e1-4734-42f4-996b-104de367ef51
\.


--
-- Data for Name: token_recuperacao_senha; Type: TABLE DATA; Schema: public; Owner: kira_user
--

COPY public.token_recuperacao_senha (id, data_expiracao, token, usado, usuario_id) FROM stdin;
\.


--
-- Data for Name: usuario; Type: TABLE DATA; Schema: public; Owner: kira_user
--

COPY public.usuario (id, ativo, data_criacao, email, foto_url, nome, senha_hash, telefone) FROM stdin;
f6b0fc27-56c8-4f55-a938-8cb114032407	t	2026-06-23 16:58:57.403259	studio@email.com	\N	Studio Beleza	$2a$10$xLDXeandzjUgO/7nonvuBuyxGsmHc0eB0LPPfD/vNEWMNCNStmCP6	62988888888
e7175636-d156-4d38-937a-abe91aa69496	t	2026-06-23 17:10:58.066458	atendimento@esteticavisual.com	\N	Cl├¡nica Est├®tica Visual	$2a$10$0HSkWMEpbhFtMJwGMGm/U.NhJm0DItIZxdmRejA8rIX8VABKeaa0m	31344443333
63d20320-bb5f-4a67-aff0-28c0929dbc7a	t	2026-06-23 17:14:14.077566	ju.sobrancelhas@email.com	\N	Juliana Designer	$2a$10$cq5gBHkixXLZJXOLsLc8/OkuFKKAJgwNGNLbH9e.cyBYTg9Galb0G	41922221111
b81b124a-0f52-4f60-983d-a0c20e8623fa	t	2026-06-23 17:27:18.08283	administracao@studiobelladonna.com	\N	Studio Bella Donna	$2a$10$Fk.f9ZuL1VhyB4HkFAikLOaa7j3sBQs7q.QXizC91gTvZ9bP79I3G	7134442222
cdea1e42-7129-45db-acf7-a76a345d61bf	t	2026-06-23 17:27:28.913831	recepcao@clinicadermacare.com.br	\N	Cl├¡nica Dermacare	$2a$10$nINLpXrSwePco92mYmBA1uoboAEOOr2jiSFUy/nfJaa0Gfalx/Rl.	5135554444
03372e5e-63bd-4cc4-ac36-c150d034e058	t	2026-06-23 17:27:58.212353	bia.cabelos@email.com	\N	Beatriz Hair Stylist	$2a$10$hIG6ireZSXmse7n7lOqCseOrrPCTSSEYXfG6b.hhezIA9hvNFNENK	31966665555
cc764454-c6a3-4e05-beff-be734a766ad9	t	2026-06-23 17:28:08.214623	fer.makeup@email.com	\N	Fernanda Maquiagem	$2a$10$OI3bw5i71C6hUkQmU8M5cev5E1.tO6rL.RTywC/O92bRE9JiubH/u	51955554444
76e2de87-280a-4466-889f-a7c79dd662cb	t	2026-06-27 09:09:34.987846	isabela@email.com	\N	Isabela Cristina	$2a$10$jMmX.U.PQutAGhqnirQP/OpRz1K/YuEn6zcqVXXI6X.pEcrS6LeE.	62999999999
47d6dda3-250c-4b94-8f16-6961cd8b813a	t	2026-07-05 15:10:21.002665	isabeulahenrique@gmail.com	\N	maria almeida	$2a$10$ti5LzzNTwyq81UB9aaGUPe5hqgwLrOAPUklrwwoJ5VrtUgGTqwSiC	62984486422
b7160af9-d283-46c5-a811-492544d29893	t	2026-07-05 15:20:56.531236	studioanamaria@gmail.com	\N	Ana paula luiza	$2a$10$BQ715dE5jiIohPaieZ94kOWDnYd19EQ35giugxHWi5yfrKZ0.hZou	62987554433
e8a44cb0-161e-41cf-995f-e881de44b451	t	2026-07-05 15:50:36.86837	maria@email.com	\N	Maria Silva	$2a$10$U/.3mN1rfUUS57xyV.azdOhk.N8aYcSlfHCKJbXLIx3KL5bJpe2Fi	62988887777
f334ede1-45ff-42cb-90f6-314bef71f12a	t	2026-07-06 17:32:54.436963	mariaduda@email.com	\N	Maria Silva	$2a$10$36xnMXiGImwaxk2qBvrsf.SHgvb9PO93YweZpM5g7bvArXli5bkPO	62998899999
c9eaa85d-96b3-41a8-8573-b3478f750bb1	t	2026-07-06 17:43:48.334503	studio@kira.com	\N	Studio Kira	$2a$10$Uq5n.EO3Xm4x.34BW2nSfeWCin7SZ0dYVoEc2sZDj.b/YhwaihiCe	62988888888
b66f28e1-4734-42f4-996b-104de367ef51	t	2026-07-06 17:46:48.774503	ana@kira.com	\N	Ana Souza	$2a$10$5R/7oOHoDiu9fD3aURXULeFzsJfGSFCYCXdt3THIEZdKLgg.IR97S	62977788877
\.


--
-- Name: admin admin_pkey; Type: CONSTRAINT; Schema: public; Owner: kira_user
--

ALTER TABLE ONLY public.admin
    ADD CONSTRAINT admin_pkey PRIMARY KEY (id);


--
-- Name: agendamento agendamento_pkey; Type: CONSTRAINT; Schema: public; Owner: kira_user
--

ALTER TABLE ONLY public.agendamento
    ADD CONSTRAINT agendamento_pkey PRIMARY KEY (id);


--
-- Name: avaliacao avaliacao_pkey; Type: CONSTRAINT; Schema: public; Owner: kira_user
--

ALTER TABLE ONLY public.avaliacao
    ADD CONSTRAINT avaliacao_pkey PRIMARY KEY (id);


--
-- Name: bloqueio_horario bloqueio_horario_pkey; Type: CONSTRAINT; Schema: public; Owner: kira_user
--

ALTER TABLE ONLY public.bloqueio_horario
    ADD CONSTRAINT bloqueio_horario_pkey PRIMARY KEY (id);


--
-- Name: cliente cliente_pkey; Type: CONSTRAINT; Schema: public; Owner: kira_user
--

ALTER TABLE ONLY public.cliente
    ADD CONSTRAINT cliente_pkey PRIMARY KEY (id);


--
-- Name: disponibilidade_estudio disponibilidade_estudio_pkey; Type: CONSTRAINT; Schema: public; Owner: kira_user
--

ALTER TABLE ONLY public.disponibilidade_estudio
    ADD CONSTRAINT disponibilidade_estudio_pkey PRIMARY KEY (id);


--
-- Name: empresa empresa_pkey; Type: CONSTRAINT; Schema: public; Owner: kira_user
--

ALTER TABLE ONLY public.empresa
    ADD CONSTRAINT empresa_pkey PRIMARY KEY (id);


--
-- Name: funcionaria funcionaria_pkey; Type: CONSTRAINT; Schema: public; Owner: kira_user
--

ALTER TABLE ONLY public.funcionaria
    ADD CONSTRAINT funcionaria_pkey PRIMARY KEY (id);


--
-- Name: mensagem mensagem_pkey; Type: CONSTRAINT; Schema: public; Owner: kira_user
--

ALTER TABLE ONLY public.mensagem
    ADD CONSTRAINT mensagem_pkey PRIMARY KEY (id);


--
-- Name: servico servico_pkey; Type: CONSTRAINT; Schema: public; Owner: kira_user
--

ALTER TABLE ONLY public.servico
    ADD CONSTRAINT servico_pkey PRIMARY KEY (id);


--
-- Name: token_recuperacao_senha token_recuperacao_senha_pkey; Type: CONSTRAINT; Schema: public; Owner: kira_user
--

ALTER TABLE ONLY public.token_recuperacao_senha
    ADD CONSTRAINT token_recuperacao_senha_pkey PRIMARY KEY (id);


--
-- Name: avaliacao uk4kq2suts67k8wqvylj1yj6b75; Type: CONSTRAINT; Schema: public; Owner: kira_user
--

ALTER TABLE ONLY public.avaliacao
    ADD CONSTRAINT uk4kq2suts67k8wqvylj1yj6b75 UNIQUE (agendamento_id);


--
-- Name: usuario uk5171l57faosmj8myawaucatdw; Type: CONSTRAINT; Schema: public; Owner: kira_user
--

ALTER TABLE ONLY public.usuario
    ADD CONSTRAINT uk5171l57faosmj8myawaucatdw UNIQUE (email);


--
-- Name: empresa uk579mxohqloqo8pcqbpmeft807; Type: CONSTRAINT; Schema: public; Owner: kira_user
--

ALTER TABLE ONLY public.empresa
    ADD CONSTRAINT uk579mxohqloqo8pcqbpmeft807 UNIQUE (cpf);


--
-- Name: token_recuperacao_senha uk5mr1mybvivl3it40wokx8i7h6; Type: CONSTRAINT; Schema: public; Owner: kira_user
--

ALTER TABLE ONLY public.token_recuperacao_senha
    ADD CONSTRAINT uk5mr1mybvivl3it40wokx8i7h6 UNIQUE (token);


--
-- Name: empresa uk74xhe5obsc7li6x4q5wi75pd5; Type: CONSTRAINT; Schema: public; Owner: kira_user
--

ALTER TABLE ONLY public.empresa
    ADD CONSTRAINT uk74xhe5obsc7li6x4q5wi75pd5 UNIQUE (cnpj);


--
-- Name: cliente ukr1u8010d60num5vc8fp0q1j2a; Type: CONSTRAINT; Schema: public; Owner: kira_user
--

ALTER TABLE ONLY public.cliente
    ADD CONSTRAINT ukr1u8010d60num5vc8fp0q1j2a UNIQUE (cpf);


--
-- Name: usuario usuario_pkey; Type: CONSTRAINT; Schema: public; Owner: kira_user
--

ALTER TABLE ONLY public.usuario
    ADD CONSTRAINT usuario_pkey PRIMARY KEY (id);


--
-- Name: empresa fk1qxb7ae8vdagy0mb5p5pjifed; Type: FK CONSTRAINT; Schema: public; Owner: kira_user
--

ALTER TABLE ONLY public.empresa
    ADD CONSTRAINT fk1qxb7ae8vdagy0mb5p5pjifed FOREIGN KEY (id) REFERENCES public.usuario(id);


--
-- Name: servico fk2dywyklyhnl9sp0i1nqvjf95i; Type: FK CONSTRAINT; Schema: public; Owner: kira_user
--

ALTER TABLE ONLY public.servico
    ADD CONSTRAINT fk2dywyklyhnl9sp0i1nqvjf95i FOREIGN KEY (empresa_id) REFERENCES public.empresa(id);


--
-- Name: mensagem fk6ct07n5wm2ci4hu2qgcvb7y46; Type: FK CONSTRAINT; Schema: public; Owner: kira_user
--

ALTER TABLE ONLY public.mensagem
    ADD CONSTRAINT fk6ct07n5wm2ci4hu2qgcvb7y46 FOREIGN KEY (remetente_id) REFERENCES public.usuario(id);


--
-- Name: agendamento fk917hu1kyw4thfpcdiwvy2t2ui; Type: FK CONSTRAINT; Schema: public; Owner: kira_user
--

ALTER TABLE ONLY public.agendamento
    ADD CONSTRAINT fk917hu1kyw4thfpcdiwvy2t2ui FOREIGN KEY (servico_id) REFERENCES public.servico(id);


--
-- Name: avaliacao fk93rovp50f70x62dmw4nw8aeqd; Type: FK CONSTRAINT; Schema: public; Owner: kira_user
--

ALTER TABLE ONLY public.avaliacao
    ADD CONSTRAINT fk93rovp50f70x62dmw4nw8aeqd FOREIGN KEY (agendamento_id) REFERENCES public.agendamento(id);


--
-- Name: bloqueio_horario fkdej60cen3uxeaxoblqolumhmw; Type: FK CONSTRAINT; Schema: public; Owner: kira_user
--

ALTER TABLE ONLY public.bloqueio_horario
    ADD CONSTRAINT fkdej60cen3uxeaxoblqolumhmw FOREIGN KEY (funcionaria_id) REFERENCES public.funcionaria(id);


--
-- Name: avaliacao fkg7hadvlyrm8dp0bjt9jxqoo1k; Type: FK CONSTRAINT; Schema: public; Owner: kira_user
--

ALTER TABLE ONLY public.avaliacao
    ADD CONSTRAINT fkg7hadvlyrm8dp0bjt9jxqoo1k FOREIGN KEY (empresa_id) REFERENCES public.empresa(id);


--
-- Name: agendamento fkgh8d6k5uh7vufobjel30xtr7p; Type: FK CONSTRAINT; Schema: public; Owner: kira_user
--

ALTER TABLE ONLY public.agendamento
    ADD CONSTRAINT fkgh8d6k5uh7vufobjel30xtr7p FOREIGN KEY (empresa_id) REFERENCES public.empresa(id);


--
-- Name: disponibilidade_estudio fkgro9fsskhvhfpgks6bti52o78; Type: FK CONSTRAINT; Schema: public; Owner: kira_user
--

ALTER TABLE ONLY public.disponibilidade_estudio
    ADD CONSTRAINT fkgro9fsskhvhfpgks6bti52o78 FOREIGN KEY (empresa_id) REFERENCES public.empresa(id);


--
-- Name: token_recuperacao_senha fkh6uig36eghvqbi383nvnw3bpw; Type: FK CONSTRAINT; Schema: public; Owner: kira_user
--

ALTER TABLE ONLY public.token_recuperacao_senha
    ADD CONSTRAINT fkh6uig36eghvqbi383nvnw3bpw FOREIGN KEY (usuario_id) REFERENCES public.usuario(id);


--
-- Name: admin fkiq5uw34yv94wnpdofq0tc4f21; Type: FK CONSTRAINT; Schema: public; Owner: kira_user
--

ALTER TABLE ONLY public.admin
    ADD CONSTRAINT fkiq5uw34yv94wnpdofq0tc4f21 FOREIGN KEY (id) REFERENCES public.usuario(id);


--
-- Name: funcionaria fkk9yhat03jcsa0conch4vis1e6; Type: FK CONSTRAINT; Schema: public; Owner: kira_user
--

ALTER TABLE ONLY public.funcionaria
    ADD CONSTRAINT fkk9yhat03jcsa0conch4vis1e6 FOREIGN KEY (empresa_id) REFERENCES public.empresa(id);


--
-- Name: agendamento fklysb71xgvy1ofu36a7rreoho4; Type: FK CONSTRAINT; Schema: public; Owner: kira_user
--

ALTER TABLE ONLY public.agendamento
    ADD CONSTRAINT fklysb71xgvy1ofu36a7rreoho4 FOREIGN KEY (funcionaria_id) REFERENCES public.funcionaria(id);


--
-- Name: avaliacao fkpogwypj6dq5o1p5vu3q31lifm; Type: FK CONSTRAINT; Schema: public; Owner: kira_user
--

ALTER TABLE ONLY public.avaliacao
    ADD CONSTRAINT fkpogwypj6dq5o1p5vu3q31lifm FOREIGN KEY (cliente_id) REFERENCES public.cliente(id);


--
-- Name: mensagem fkr7v91cjt7pggokeb6robtyitt; Type: FK CONSTRAINT; Schema: public; Owner: kira_user
--

ALTER TABLE ONLY public.mensagem
    ADD CONSTRAINT fkr7v91cjt7pggokeb6robtyitt FOREIGN KEY (agendamento_id) REFERENCES public.agendamento(id);


--
-- Name: agendamento fksgdo4l8yts964f089m6ujyuef; Type: FK CONSTRAINT; Schema: public; Owner: kira_user
--

ALTER TABLE ONLY public.agendamento
    ADD CONSTRAINT fksgdo4l8yts964f089m6ujyuef FOREIGN KEY (cliente_id) REFERENCES public.cliente(id);


--
-- Name: cliente fksitxst8o302fspskxfjatuyrl; Type: FK CONSTRAINT; Schema: public; Owner: kira_user
--

ALTER TABLE ONLY public.cliente
    ADD CONSTRAINT fksitxst8o302fspskxfjatuyrl FOREIGN KEY (id) REFERENCES public.usuario(id);


--
-- PostgreSQL database dump complete
--

\unrestrict CiXw8j8CQwnATQcck2owedqKjU5pvqqk5w4bQvsy82jVCVECmckNLDcz4eaAkCI

