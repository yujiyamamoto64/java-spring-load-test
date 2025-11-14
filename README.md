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
