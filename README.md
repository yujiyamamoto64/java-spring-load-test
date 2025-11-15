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

Para conectar ao Aurora/PostgreSQL na AWS, defina o profile `aws` e variáveis de ambiente com o endpoint Secret Manager:

```bash
export SPRING_PROFILES_ACTIVE=aws
export SPRING_DATASOURCE_URL="jdbc:postgresql://database-1.cluster-cc3cosc4w9th.us-east-1.rds.amazonaws.com:5432/postgres"
export SPRING_DATASOURCE_USERNAME="postgres"
export SPRING_DATASOURCE_PASSWORD="<senha do Secrets Manager>"
./mvnw spring-boot:run
```

Endpoints principais:

- `POST /api/transfers` recebe JSON com `idempotencyKey`, `fromAccount`, `toAccount`, `amountInCents` e `currency`.
- `GET /api/transfers/stats` retorna vazao, media de latencia e volume processado.

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

## Versao 3: migrando para AWS (Aurora + Fargate)

- O código de versao 3 grava contas/transacoes no Postgres. As tabelas são criadas a partir de `schema.sql` (`accounts` e `transfers`) com `INSERT ... ON CONFLICT` para garantir idempotencia por `idempotency_key`.
- Provisionamento do banco: crie um Aurora PostgreSQL Serverless v2 (versao 17.4), com template Dev/Test, storage `Aurora Standard`, sem replica, capacidade 0.5–128 ACUs, VPC privada e acesso restrito a security groups do ECS. Guarde o secret gerado (`rds!cluster-...`) para obter usuario/senha.
- Ajuste o profile `aws` ou o `.env` com as variaveis `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` e `SPRING_DATASOURCE_PASSWORD` usando o endpoint `database-1.cluster-cc3cosc4w9th.us-east-1.rds.amazonaws.com`.
- Construa a imagem com o `Dockerfile` e publique no ECR. Crie um cluster ECS com launch type Fargate, task definition usando `SPRING_PROFILES_ACTIVE=aws` e variaveis lidas do Secrets Manager.
- Suba um Serviço Fargate (de preferencia ligado a um Application Load Balancer) e execute o k6 de uma instância EC2 na mesma região (us-east-1) para minimizar latência.
