# Pix Recorrente Seguro

API REST para pagamentos Pix recorrentes.

## Tecnologias

- Java 21
- Spring Boot 4.1.0
- Maven

## Pré-requisitos

- JDK 21
- Maven instalado e disponível no `PATH`
- Apache Kafka disponível em `localhost:9092`

## Executar

```bash
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
