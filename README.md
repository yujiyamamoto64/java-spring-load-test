# Java Spring Load Test

Este projeto demonstra uma API de transferencia bancaria com Spring Boot WebFlux ajustada para suportar mais de 1 milhao de requisicoes por minuto e inclui um cenario de teste de estresse com k6.

## Arquitetura

- **WebFlux/Netty**: IO nao bloqueante usando Netty com ajustes de conexao, buffers e LoopResources dedicados.
- **Modelo em memoria**: Saldos guardados em `ConcurrentHashMap` com `AtomicLong` para operacoes lock-free por conta.
- **Idempotencia**: Cache em memoria com TTL evita reprocesar transferencias repetidas mantendo baixa latencia.
- **Metricas e observabilidade**: Actuator + Micrometer Prometheus expoem estatisticas de negocio e da JVM (`/actuator/metrics`, `/api/transfers/stats`).
- **Configuracao**: `application.properties` concentra limites de conexao, tamanho de buffer e numero de contas pre-provisionadas.

## Executando a API

```bash
./mvnw spring-boot:run
```

> Versão 4: a aplicação agora foca apenas nos ambientes **local** (PostgreSQL via Docker) e **Railway**. Todo o conteúdo da versão 3 sobre AWS/Aurora/Fargate foi removido para simplificar o setup.

### Banco PostgreSQL local com Docker

Use `docker-compose.db.yml` para subir um PostgreSQL local com o mesmo schema usado no Aurora:

```bash
docker compose -f docker-compose.db.yml up -d
```

O compose cria o banco `loadtest` expondo a porta `15432` no host (mapeada para `5432` no container) com usuário/senha `loadtest`. Se quiser outro valor, edite `docker-compose.db.yml`. O arquivo `schema.sql` é carregado automaticamente por estar montado em `/docker-entrypoint-initdb.d`.

Depois de subir o container, a aplicação deve rodar com o profile `local`, que já aponta para `jdbc:postgresql://localhost:15432/loadtest` com usuário/senha `loadtest`, então nenhuma variável adicional é necessária:

```bash
export SPRING_PROFILES_ACTIVE=local
./mvnw spring-boot:run
```

ou

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Se precisar customizar usuário/senha/porta, use as variáveis `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_DATASOURCE_MAX_POOL_SIZE` e `SPRING_DATASOURCE_MIN_IDLE` quando iniciar o app.

> Observação: o arquivo `.env` agora traz um exemplo com o profile `local`. Copie-o para `.env.local` se quiser manter credenciais diferentes por máquina.

Para desligar e remover os dados do container, execute `docker compose -f docker-compose.db.yml down -v`.

Endpoints principais:

- `POST /api/transfers` recebe JSON com `idempotencyKey`, `fromAccount`, `toAccount`, `amountInCents` e `currency`.
- `GET /api/transfers/stats` retorna vazao, media de latencia e volume processado.

## Deploy no Railway

O Railway detecta automaticamente o `Dockerfile` e define `PORT` para o processo. O profile `railway` (arquivo `application-railway.properties`) usa as variáveis `PGHOST/PGPORT/PGUSER/PGPASSWORD/PGDATABASE` que o plugin de Postgres disponibiliza.

1. Instale a CLI (`npm i -g railway` ou baixe do site) e faça login:
   ```bash
   railway login
   ```
2. Inicialize o projeto e conecte este repositório:
   ```bash
   railway init --project java-spring-load-test
   railway link
   ```
3. Provisione o Postgres gerenciado pelo Railway (gera `PG*` e `DATABASE_URL` automaticamente):
   ```bash
   railway add postgres
   ```
4. Defina as variáveis do serviço para usar o profile `railway` (opcionalmente ajuste JVM):
   ```bash
   railway variables set SPRING_PROFILES_ACTIVE=railway JAVA_OPTS="-Xms2g -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=50 -XX:+AlwaysActAsServerClassMachine"
   ```
5. Faça o build/deploy usando o Dockerfile existente:
   ```bash
   railway up --service api
   ```
6. Acompanhe os logs com `railway logs` e copie o domínio gerado (`https://<app>.up.railway.app`). Essa URL será usada no k6:
   ```bash
   k6 run -e BASE_URL=https://<app>.up.railway.app loadtest/k6-transfer.js
   ```

### Banco PostgreSQL no Railway

- O comando `railway add postgres` já cria o banco e injeta variáveis `PGHOST`, `PGPORT`, `PGUSER`, `PGPASSWORD`, `PGDATABASE` e `DATABASE_URL` na aplicação.
- O profile `railway` converte essas variáveis em propriedades do Spring, criando as tabelas definidas em `schema.sql` automaticamente (`spring.sql.init.mode=always`).
- Se quiser usar um Postgres externo, basta definir `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` na aba *Variables* ou via `railway variables set`.

## Teste de carga (1M rpm)

Requisitos: [k6](https://k6.io). Ajuste `BASE_URL`, `ACCOUNTS` e `CURRENCY` se necessario.

```bash
k6 run -e BASE_URL=http://localhost:8080 loadtest/k6-transfer.js
```

O arquivo `loadtest/k6-transfer.js` define dois cenarios: aquecimento progressivo e `constant-arrival-rate` equivalente a 1.000.000 requisicoes por minuto (aprox. 16.667 rps). Use `k6 run --vus` para experimentar outras combinacoes.

## Recomendacoes de tunning

1. **JVM**: para workloads acima de 1M rpm, inicie com `-Xms4g -Xmx4g -XX:+AlwaysActAsServerClassMachine`. Ajuste G1GC `-XX:MaxGCPauseMillis=50` conforme o perfil observado.
2. **Sistema operacional**: aumente `ulimit -n`, `net.ipv4.ip_local_port_range` e `net.core.somaxconn` para nao esgotar portas ou descritores durante o teste.
3. **Infraestrutura**: use instancias com muitos vCPUs e rede de baixa latencia. Distribua os geradores de carga em mais de uma maquina para nao limitar o cliente.
4. **Observabilidade**: monitore `transfer_success` no k6, `http.server.requests` no Actuator e `GET /api/transfers/stats` para identificar quedas de throughput ou aumento de latencia.

Siga `JavaSpringLoadTestApplication` para incluir integracoes reais (fila, banco) preservando o padrao nao bloqueante.

## Tuning aplicado na versao 2

- O `spring-boot-maven-plugin` ja inicia a aplicacao com `-Xms2g -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=50 -XX:+AlwaysActAsServerClassMachine`. Altere via `-Dapp.jvm.args=\"...\"` se quiser outro perfil.
- O `NettyTuningConfig` fixa `LoopResources` dedicados, backlog em 65k, keep-alive agressivo e buffers de 1MB para reduzir o custo por conexao.
- Rode `powershell -ExecutionPolicy Bypass -File .\\scripts\\windows-tuning.ps1` como Administrador para aumentar o range de portas efemeras, reduzir `TcpTimedWaitDelay` e habilitar RSS antes dos testes k6.

## Versao 4: Railway + ambientes locais

- O foco oficial é rodar localmente (profile `local`, PostgreSQL via Docker) ou no Railway (profile `railway`). Para outras infraestruturas copie `application-railway.properties` e ajuste as credenciais necessárias.
- A documentação de AWS/Aurora/Fargate da versão anterior foi removida para simplificar o onboarding.
- O `.env` serve apenas como exemplo de configuração local; em produção use o painel do Railway para definir as variáveis (ou `railway variables set`).
- O guia de deploy acima mostra todo o fluxo recomendado (provisionar Postgres gerenciado no Railway, apontar o profile `railway` e executar `railway up`).
