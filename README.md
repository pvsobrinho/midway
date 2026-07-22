# Pix Recorrente Seguro

API REST para pagamentos Pix recorrentes.

[Relatório dos testes unitários](docs/Relat%C3%B3rio%20dos%20testes%20unit%C3%A1rios%20-%20POC%20Agendamento%20PIX%20%20-%20Riachuelo.pdf)

## Tecnologias

- Java 21
- Spring Boot 4.1.0
- Maven

## Pré-requisitos

- JDK 21
- Maven instalado e disponível no `PATH`
- Apache Kafka 4.2.x disponível em `localhost:9092`

## Executar o Kafka localmente no Windows

Esta PoC utiliza o Kafka instalado diretamente no Windows, sem Docker e sem ZooKeeper. O Kafka 4 funciona em modo KRaft e requer Java 17 ou superior. O procedimento abaixo adapta o [Quick Start oficial](https://kafka.apache.org/42/getting-started/quickstart/) para PowerShell.

### 1. Baixar e extrair

Baixe a distribuição binária do Kafka em [kafka.apache.org/downloads](https://kafka.apache.org/downloads) e extraia o arquivo em um diretório sem espaços. Os exemplos abaixo consideram:

```text
C:\kafka\kafka_2.13-4.2.1
```

Abra o PowerShell e acesse o diretório:

```powershell
Set-Location C:\kafka\kafka_2.13-4.2.1
java -version
```

### 2. Preparar o armazenamento na primeira execução

Este passo deve ser executado somente uma vez para cada instalação ou diretório de dados do Kafka:

```powershell
$env:KAFKA_LOG4J_OPTS = "-Dlog4j2.configurationFile=config/tools-log4j2.yaml"
$clusterId = (& .\bin\windows\kafka-storage.bat random-uuid | Select-Object -Last 1).Trim()
& .\bin\windows\kafka-storage.bat format --standalone -t $clusterId -c .\config\server.properties
```

Ao final, deve ser exibida uma mensagem informando que o diretório de metadados foi formatado. Nas próximas execuções, não repita este passo.

### 3. Iniciar o servidor Kafka

Ainda no diretório do Kafka, execute (valores varivael de exemplo):

```powershell
$env:KAFKA_HEAP_OPTS = "-Xms512M -Xmx1G"
$env:KAFKA_LOG4J_OPTS = "-Dlog4j2.configurationFile=config/log4j2.yaml"
& .\bin\windows\kafka-server-start.bat .\config\server.properties
```

A variável `KAFKA_HEAP_OPTS` evita que o script tente utilizar o comando `wmic`, que não está disponível em algumas versões atuais do Windows. Mantenha esse terminal aberto enquanto utilizar a aplicação.

### 4. Verificar a conexão

Em outro PowerShell, confirme que a porta do Kafka está disponível:

```powershell
Test-NetConnection 127.0.0.1 -Port 9092
```

O resultado esperado é `TcpTestSucceeded: True`. O tópico `agendamento-solicitado` e os tópicos de retry e DLT são criados automaticamente pela aplicação.

## Executar a aplicação

Com o Kafka em execução, abra outro PowerShell, acesse a raiz do projeto e execute:

```powershell
mvn spring-boot:run
```

A aplicação estará disponível em `http://localhost:8080`. Para encerrar a aplicação ou o Kafka, pressione `Ctrl+C` no respectivo terminal.

O endereço do broker pode ser alterado antes de iniciar a aplicação:

```powershell
$env:KAFKA_BOOTSTRAP_SERVERS = "localhost:9092"
mvn spring-boot:run
```

## Endpoints

- `POST /v1/agendamentos`
- `GET /v1/agendamentos/{id}`

O endpoint de criação exige o header `Idempotency-Key`. Os agendamentos são persistidos em `data/agendamentos.json`.

O `POST` retorna `202 Accepted` com o status `PENDENTE_ANALISE` e o motivo informa que o agendamento aguarda o processamento antifraude. O resultado pode ser acompanhado pelo endpoint `GET`.

## Processamento assíncrono

1. A API salva o agendamento no arquivo JSON.
2. O produtor publica o evento no tópico `agendamento-solicitado`.
3. O consumidor executa as regras antifraude e atualiza o agendamento.
4. Após três falhas de processamento, o evento vai para `agendamento-solicitado-dlt` e o agendamento fica em revisão manual.

O endereço do Kafka pode ser alterado pela variável `KAFKA_BOOTSTRAP_SERVERS`. Se a publicação falhar, o agendamento permanece salvo e é marcado para revisão manual.

## Regras de negócio antifraude

As regras são avaliadas na ordem abaixo. A primeira condição identificada determina o resultado da análise:

1. **Bloqueio vigente — REJEITADO:** impede uma nova solicitação entre o mesmo pagador e recebedor quando existir bloqueio ativo. Essa regra reduz tentativas repetidas após uma rajada considerada suspeita.
2. **Valor acima de R$ 10.000,00 — REJEITADO:** recusa agendamentos acima do limite definido para a PoC, reduzindo a exposição financeira a operações de maior impacto.
3. **Possível duplicidade — REVISAO_MANUAL:** identifica outro agendamento em aberto com o mesmo pagador, recebedor, valor e data, mesmo que a chave de idempotência seja diferente. A revisão evita cobranças duplicadas sem rejeitar automaticamente um caso legítimo.
4. **Terceiro agendamento para a mesma data — REVISAO_MANUAL:** sinaliza concentração incomum de agendamentos do mesmo pagador para o mesmo recebedor e dia.
5. **Dez solicitações em 60 minutos — REVISAO_MANUAL:** considera a décima tentativa uma possível automação maliciosa e bloqueia novas solicitações entre o mesmo pagador e recebedor por 24 horas.
6. **Quatro solicitações em 5 minutos — REVISAO_MANUAL:** identifica uma rajada de solicitações em um intervalo curto, mas não aplica o bloqueio de 24 horas.
7. **Valor acima de R$ 5.000,00 — REVISAO_MANUAL:** exige validação humana para valores relevantes que ainda estejam dentro do limite aceito pela PoC.
8. **Recorrência superior a dois anos — REVISAO_MANUAL:** evita a aprovação automática de um compromisso financeiro por um período prolongado.
9. **Nenhum indício de risco — APROVADO:** ativa o agendamento quando nenhuma das condições anteriores é encontrada.

O resultado, o motivo e o horário da análise são registrados no objeto do agendamento e nos logs da aplicação. Dados sensíveis usados nos logs são mascarados para facilitar a auditoria sem expor as informações completas da requisição.

## Swagger

Com a aplicação em execução, acesse:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## Exemplos com curl

Criar um agendamento:

```bash
curl --request POST "http://localhost:8080/v1/agendamentos" \
  --header "Content-Type: application/json" \
  --header "Idempotency-Key: assinatura-cliente-123" \
  --data '{
    "identificadorPagador": "cliente-123",
    "chavePixRecebedor": "recebedor@exemplo.com",
    "valor": 250.00,
    "descricao": "Mensalidade do serviço",
    "periodicidade": "MENSAL",
    "dataPrimeiroPagamento": "2026-08-01",
    "dataFim": "2027-08-01"
  }'
```

Consultar o agendamento:

```bash
curl --request GET "http://localhost:8080/v1/agendamentos/{id}"
```
